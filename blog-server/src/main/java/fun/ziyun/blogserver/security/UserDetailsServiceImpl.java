package fun.ziyun.blogserver.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.ziyun.blogserver.entity.User;
import fun.ziyun.blogserver.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;

/**
 * 用户加载服务：Spring Security 登录认证时按用户名查库。
 *
 * <p>设计说明（UserDetailsService 在认证链路中的位置）：</p>
 * <pre>
 * 认证流程（配合 SecurityConfig）：
 *   1. 登录接口调用 AuthenticationManager.authenticate(token)；
 *   2. AuthenticationManager 委托 DaoAuthenticationProvider；
 *   3. DaoAuthenticationProvider 调用本服务 loadUserByUsername 拿到
 *      库里的密码哈希与权限；
 *   4. 用 BCryptPasswordEncoder 比对「输入密码哈希」与「库里哈希」；
 *   5. 一致 -> 认证成功，返回带权限的 Authentication 存入上下文。
 * 这是「用户名密码认证」的标准链路，JWT 只是认证成功后的令牌载体，
 * 与登录时的密码校验互不冲突。
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // @TableLogic 自动过滤已删除用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null) {
            // 统一提示「用户名或密码错误」，不区分用户是否存在，
            // 避免攻击者通过报错信息枚举有效用户名
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        // 被管理员禁用的账号（status=0）：禁止登录。
        // 抛 DisabledException 而非伪装「用户名或密码错误」：
        // 账号本身合法存在，明确告知便于用户联系管理员解禁。
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new DisabledException("账号已被禁用，请联系管理员");
        }
        return new AuthUser(user.getId(), user.getUsername(), user.getPassword(), user.getRole());
    }
}
