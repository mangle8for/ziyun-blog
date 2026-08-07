package fun.ziyun.blogserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.dto.LoginDTO;
import fun.ziyun.blogserver.dto.RegisterDTO;
import fun.ziyun.blogserver.entity.User;
import fun.ziyun.blogserver.exception.BusinessException;
import fun.ziyun.blogserver.mapper.UserMapper;
import fun.ziyun.blogserver.security.AuthUser;
import fun.ziyun.blogserver.service.AuthService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

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

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

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

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        // 只拷贝展示字段（UserVO 无 password/email，天然隔离）
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    // ==================== 登录限流（防暴力破解） ====================

    /**
     * 检查是否处于锁定状态。Redis 不可用时 fail-open（跳过限流），
     * 避免基础设施故障导致正常用户无法登录 —— 限流是加分项不是业务依赖。
     */
    private void checkLoginLocked(String username) {
        try {
            Boolean locked = stringRedisTemplate.hasKey(LOGIN_LOCK_KEY + username);
            if (Boolean.TRUE.equals(locked)) {
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                        "登录失败次数过多，请 " + WINDOW_MINUTES + " 分钟后再试");
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，跳过登录限流检查: {}", e.getMessage());
        }
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
