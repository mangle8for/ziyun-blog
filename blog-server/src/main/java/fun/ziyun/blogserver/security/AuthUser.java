package fun.ziyun.blogserver.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 自定义 UserDetails 实现：Spring Security 认证成功后存放在
 * SecurityContext 里的「当前用户」载体。
 *
 * <p>设计说明（为什么自定义而不直接用框架的 User）：</p>
 * <pre>
 * 1. 框架内置的 org.springframework.security.core.userdetails.User
 *    只承载 username/password/authorities，我们还需要 userId/role，
 *    后续接口（如文章作者、GET /me）都要从当前用户取这些信息；
 * 2. 数据库 User 实体不应直接塞进 SecurityContext —— 它是 ORM 投影，
 *    且含敏感字段（password），借 UserDetails 接口做一层隔离更干净。
 * 3. 所有实现接口的方法（getAuthorities 等）由框架按需调用，
 *    业务代码统一通过 SecurityContextHolder.getContext() 取本对象。
 * </pre>
 */
@Getter
public class AuthUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    /** 角色码：0-普通用户 1-管理员（与数据库一致） */
    private final Integer role;

    public AuthUser(Long id, String username, String password, Integer role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    /**
     * 返回权限集合：角色码转 Spring Security 的 ROLE_ 前缀权限。
     * SecurityConfig 里 hasRole("ADMIN") 实际匹配的就是 ROLE_ADMIN。
     * 这是 Spring Security 的约定：hasRole 自动补 ROLE_ 前缀，
     * 权限字符串必须与之一致才能命中。
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = role != null && role == 1 ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(authority));
    }

    /** 账号是否未过期（本项目不做账号过期控制，恒 true） */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** 账号是否未锁定（预留：后续可配合 status 字段实现禁用） */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** 凭据是否未过期 */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 是否启用（预留：对应数据库 status 字段，后续可接） */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
