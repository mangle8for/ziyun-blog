package fun.ziyun.blogserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.dto.LoginDTO;
import fun.ziyun.blogserver.dto.RegisterDTO;
import fun.ziyun.blogserver.dto.UpdatePasswordDTO;
import fun.ziyun.blogserver.dto.UpdateProfileDTO;
import fun.ziyun.blogserver.entity.User;
import fun.ziyun.blogserver.exception.BusinessException;
import fun.ziyun.blogserver.mapper.UserMapper;
import fun.ziyun.blogserver.security.AuthUser;
import fun.ziyun.blogserver.service.AuthService;
import fun.ziyun.blogserver.service.FileService;
import fun.ziyun.blogserver.util.JwtUtil;
import fun.ziyun.blogserver.vo.LoginVO;
import fun.ziyun.blogserver.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 认证服务实现。
 *
 * <p>设计说明（登录态的三个参与者与生命周期）：</p>
 * <pre>
 * 一次登录产生三个状态，登出/顶号必须三处协同：
 *   1. JWT（无状态）：签名含 userId，72 小时有效；
 *   2. Redis key login:token:{userId} = token 值（白名单）：
 *      - 每次请求 JwtAuthenticationFilter 比对「请求 token == 库中 token」；
 *      - 新登录覆盖旧值 -> 旧 token 失效（顶号/单会话）；
 *      - 登出删除 key -> 旧 token 失效（踢下线）；
 *   3. 数据库密码哈希（不可逆，仅登录时校验用）。
 * Redis key 有效期与 JWT 对齐（72h），到期自动清理，无僵尸 key。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Redis 登录态 key 前缀（与 JwtAuthenticationFilter 一致） */
    private static final String LOGIN_TOKEN_KEY = "login:token:";

    /** 登录失败计数 key 前缀（滑动窗口内累计失败次数） */
    private static final String LOGIN_FAIL_KEY = "login:fail:";

    /** 登录锁定 key 前缀（达到阈值后禁止登录一段时间） */
    private static final String LOGIN_LOCK_KEY = "login:lock:";

    /** 锁定阈值：窗口内失败超过该次数则锁定 */
    private static final int MAX_FAIL_COUNT = 5;

    /** 计数窗口与锁定时长（分钟）：15 分钟内失败 5 次锁 15 分钟 */
    private static final long WINDOW_MINUTES = 15;

    /** 头像修改月度上限：每月最多 3 次（自然月，Redis 计数） */
    private static final long AVATAR_MONTHLY_LIMIT = 3;

    /** 头像月度计数 key 前缀（key 带 yyyyMM，天然按自然月隔离） */
    private static final String AVATAR_COUNT_KEY = "avatar:count:";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final FileService fileService;

    /** JWT 有效期（小时），与 token 生命周期保持一致（Redis key 同步过期） */
    @Value("${blog.jwt.expire-hours}")
    private long expireHours;

    @Override
    public Long register(RegisterDTO dto) {
        // 重名校验：数据库唯一索引兜底，业务层先给友好提示
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在：" + dto.getUsername());
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        // BCrypt 加密后入库（每次加密结果不同，见 SecurityConfig 说明）
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 昵称缺省回退用户名，保证展示不为空
        user.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? dto.getUsername() : dto.getNickname());
        // 注册用户固定普通角色；管理员只能由预置/手动提升，防越权注册
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 防暴力破解：锁定期间直接拒绝，不进入密码比对
        checkLoginLocked(dto.getUsername());

        Authentication authentication;
        try {
            // 标准认证：UserDetailsService 查库 + BCrypt 比对（见其类注释的链路图）
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
        } catch (BadCredentialsException e) {
            // 统一提示，不暴露「用户名不存在」或「密码错误」的差异（防用户名枚举）；
            // 同时记录一次失败，累计达阈值触发锁定
            recordLoginFailure(dto.getUsername());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        } catch (DisabledException e) {
            // 账号被管理员禁用（UserDetailsServiceImpl 抛 DisabledException）：
            // 明确提示原因，不参与失败计数（账号本身是合法存在的）
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        // 登录成功：清除失败计数与锁定标记
        clearLoginFailures(dto.getUsername());

        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        User user = userMapper.selectById(authUser.getId());

        // 签发 JWT + 写入 Redis 白名单（覆盖旧值 = 单会话顶号）
        String token = jwtUtil.generateToken(user);
        stringRedisTemplate.opsForValue().set(
                LOGIN_TOKEN_KEY + user.getId(), token,
                expireHours, TimeUnit.HOURS);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(toUserVO(user));
        return vo;
    }

    @Override
    public void logout(Long userId) {
        // 删除白名单 key：此后该 token 的请求全部判定「登录已失效」
        stringRedisTemplate.delete(LOGIN_TOKEN_KEY + userId);
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        return toUserVO(user);
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }

        // 昵称：非空才更新（空串视为未提交）；昵称回退用户名由前端兜底
        if (StringUtils.hasText(dto.getNickname())) {
            user.setNickname(dto.getNickname().trim());
        }
        // 邮箱：空串/空白 = 清空；非空时校验格式（可空邮箱）
        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim();
            if (email.isEmpty()) {
                user.setEmail(null);
            } else {
                if (!EMAIL_PATTERN.matcher(email).matches()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱格式不正确");
                }
                user.setEmail(email);
            }
        }
        // 头像：非空才更新
        if (StringUtils.hasText(dto.getAvatar())) {
            user.setAvatar(dto.getAvatar().trim());
        }

        userMapper.updateById(user);
        log.info("用户 {} 更新了个人资料", userId);
        return toUserVO(user);
    }

    @Override
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }

        // 1. 必须校验原密码：防止已登录会话被他人冒用直接改密
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "原密码错误");
        }
        // 2. 新密码不能与原密码相同
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能与原密码相同");
        }

        // 3. 更新 BCrypt 哈希（每次随机盐，密文无规律）
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);

        // 4. 安全收尾：清除 Redis 登录态，所有会话（含当前）强制重新登录。
        //    防止「旧密码继续可用/其他设备未失效」——改密即全局下线。
        stringRedisTemplate.delete(LOGIN_TOKEN_KEY + userId);
        log.info("用户 {} 修改了密码，所有会话已失效", userId);
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }

        // 1. 月度限流：每月最多 3 次。先读计数（不占位），
        //    上传失败（类型/大小/OSS 异常）不计入次数。
        String monthKey = AVATAR_COUNT_KEY + userId + ":"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String usedStr = stringRedisTemplate.opsForValue().get(monthKey);
        long used = usedStr == null ? 0 : Long.parseLong(usedStr);
        if (used >= AVATAR_MONTHLY_LIMIT) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "本月头像修改次数已达上限（每月 " + AVATAR_MONTHLY_LIMIT + " 次），请下月再试");
        }

        // 2. 上传（类型白名单/5MB 校验与 OSS 上传在 FileService 内）
        String url = fileService.upload(file);

        // 3. 上传成功才计数；首次计数时设置过期 = 下月 1 号零点（自然月自动重置）
        Long newUsed = stringRedisTemplate.opsForValue().increment(monthKey);
        if (newUsed != null && newUsed == 1) {
            long ttlSeconds = Duration.between(LocalDateTime.now(),
                    LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay()).getSeconds();
            stringRedisTemplate.expire(monthKey, Math.max(ttlSeconds, 1), TimeUnit.SECONDS);
        }

        // 4. 更新头像字段（旧头像文件不删除：OSS 无引用计数，删了无法回滚；
        //    每月 3 次的额度天然限制了废弃文件增长）
        User update = new User();
        update.setId(userId);
        update.setAvatar(url);
        userMapper.updateById(update);

        log.info("用户 {} 更新头像，本月第 {} 次（上限 {}）", userId, Math.min(newUsed, AVATAR_MONTHLY_LIMIT), AVATAR_MONTHLY_LIMIT);
        return url;
    }

    /** 简单邮箱格式校验（可空字段，非空时严格校验） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$");

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        // 只拷贝展示字段（password 在实体层 @JsonIgnore 且 VO 无该字段，双层隔离）
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    // ==================== 登录限流（防暴力破解） ====================

    /**
     * 检查是否处于锁定状态。Redis 不可用时 fail-open（跳过限流），
     * 避免基础设施故障导致正常用户无法登录 —— 限流是加分项不是业务依赖。
     * 锁定提示展示实时剩余时间（读 Redis TTL），而非固定时长。
     */
    private void checkLoginLocked(String username) {
        try {
            Long ttlSeconds = stringRedisTemplate.getExpire(
                    LOGIN_LOCK_KEY + username, TimeUnit.SECONDS);
            if (ttlSeconds != null && ttlSeconds > 0) {
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                        "登录失败次数过多，请 " + formatRemainTime(ttlSeconds) + " 后再试");
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，跳过登录限流检查: {}", e.getMessage());
        }
    }

    /** 秒数格式化为「x 分 x 秒」：不足 1 分钟只显示秒，向上取整保证不为 0 秒 */
    private String formatRemainTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0 && secs > 0) {
            return minutes + " 分 " + secs + " 秒";
        }
        if (minutes > 0) {
            return minutes + " 分钟";
        }
        // 不足 1 秒也至少显示 1 秒，避免出现「0 秒后再试」
        return Math.max(secs, 1) + " 秒";
    }

    /**
     * 记录一次登录失败：滑动窗口内计数递增，首次失败时设置窗口 TTL；
     * 计数达到阈值后写入锁定标记（TTL = 锁定时长）并清空计数。
     */
    private void recordLoginFailure(String username) {
        try {
            String failKey = LOGIN_FAIL_KEY + username;
            Long count = stringRedisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(failKey, WINDOW_MINUTES, TimeUnit.MINUTES);
            }
            if (count != null && count >= MAX_FAIL_COUNT) {
                stringRedisTemplate.opsForValue().set(
                        LOGIN_LOCK_KEY + username, "1", WINDOW_MINUTES, TimeUnit.MINUTES);
                stringRedisTemplate.delete(failKey);
                log.warn("用户 {} 登录失败次数达阈值，锁定 {} 分钟", username, WINDOW_MINUTES);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，跳过登录失败计数: {}", e.getMessage());
        }
    }

    /** 登录成功：清除失败计数与锁定标记（下次失败从零计数） */
    private void clearLoginFailures(String username) {
        try {
            stringRedisTemplate.delete(LOGIN_FAIL_KEY + username);
            stringRedisTemplate.delete(LOGIN_LOCK_KEY + username);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，跳过登录失败记录清理: {}", e.getMessage());
        }
    }
}
