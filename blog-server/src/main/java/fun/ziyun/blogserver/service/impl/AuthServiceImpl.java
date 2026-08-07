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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
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
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Redis 登录态 key 前缀（与 JwtAuthenticationFilter 一致） */
    private static final String LOGIN_TOKEN_KEY = "login:token:";

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
        Authentication authentication;
        try {
            // 标准认证：UserDetailsService 查库 + BCrypt 比对（见其类注释的链路图）
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
        } catch (BadCredentialsException e) {
            // 统一提示，不暴露「用户名不存在」或「密码错误」的差异（防用户名枚举）
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

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
}
