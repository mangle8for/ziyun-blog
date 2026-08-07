package fun.ziyun.blogserver.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.security.AuthUser;
import fun.ziyun.blogserver.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：每次请求进来先解析 token 并填充安全上下文。
 *
 * <p>设计说明（为什么用过滤器而不是拦截器/切面）：</p>
 * <pre>
 * Spring Security 的过滤器链（FilterChain）是请求进入 DispatcherServlet
 * 【之前】执行的 —— 拦截器（HandlerInterceptor）要等 Servlet 处理开始后
 * 才触发，此时鉴权失败再拦截，无法阻止进入 Controller 的映射解析；
 * 而 Security 的授权判断（hasRole）就挂在过滤器链上，必须在链上完成认证。
 * 因此自定义认证逻辑必须作为 Filter 插入 Security 过滤器链
 * （本过滤器注册在 UsernamePasswordAuthenticationFilter 之前），
 * 与授权过滤器天然衔接。OncePerRequestFilter 保证一次请求只执行一次
 * （避免内部转发时重复执行）。
 *
 * 流程：
 *   1. 取 Authorization: Bearer xxx 头（无 token 直接放行 —— 匿名请求，
 *      是否允许访问由后续授权过滤器按白名单决定）；
 *   2. JwtUtil 校验签名与过期（篡改/过期 -> 视为匿名，放行授权链）;
 *   3. 查 Redis login:token:{userId} 比对 token —— 防「登出后旧 token
 *      仍有效」与「同账号被新登录顶号」；
 *   4. 校验通过 -> 构造 Authentication 存入 SecurityContext，
 *      后续 Controller 通过 SecurityContextHolder 获取当前用户。
 *
 * 【关键设计 —— 校验失败为何降级为匿名而非直接 401】：
 * 用户可能带着「已失效的旧 token」访问公开接口（如首页文章列表），
 * 此时请求本质是游客访问，应当放行。若过滤器直接返回 401，
 * 会导致公开页面因残留 token 而无法访问（真实踩过的坑）。
 * 因此 token 无效时不写响应，仅不设置认证 —— 由后续授权过滤器裁决：
 *   - 公开路径（permitAll）-> 放行，游客正常浏览；
 *   - 受限路径（authenticated/hasRole）-> 触发 AuthenticationEntryPoint 返回 401。
 * 唯一例外：Redis 连接异常属于基础设施故障（fail-closed），
 * 无法判断 token 真实性，直接 503 明确告知（见 RedisConfig 说明）。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Redis 登录态 key 前缀（与 AuthService 一致） */
    private static final String LOGIN_TOKEN_KEY = "login:token:";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            // 无 token：匿名请求，交给后续过滤器/白名单决定
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = jwtUtil.getUserId(claims);

            // Redis 白名单校验：key 存的 token 必须与请求 token 一致
            // （登出删 key -> 旧 token 失效；新登录覆盖 -> 顶号生效）
            String redisToken = stringRedisTemplate.opsForValue().get(LOGIN_TOKEN_KEY + userId);
            if (redisToken == null || !redisToken.equals(token)) {
                // 已登出/被顶号：降级为匿名，交给授权链裁决（见类注释）
                filterChain.doFilter(request, response);
                return;
            }

            // 从 token claims 恢复权限并填充上下文。
            // 注意这里不查库：token 已签名 + Redis 白名单已验，再查库
            // 每次请求多一次 IO；角色变更场景由 Redis 顶号机制兜底
            // （改角色后需重新登录，旧 token 已被覆盖）。
            Integer role = claims.get("role", Integer.class);
            String username = claims.get("username", String.class);
            AuthUser authUser = new AuthUser(userId, username, null, role);
            // 已认证的 Authentication：第三个参数是权限集合，供授权过滤器判断
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(authUser, null,
                            authUser.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            // 签名无效/已过期（ExpiredJwtException 是其子类）：降级为匿名放行
            // （公开接口应允许游客访问，受限接口由授权链返回 401 —— 见类注释）
            log.debug("JWT 校验失败，按匿名请求处理: {}", e.getMessage());
        } catch (RedisConnectionFailureException e) {
            // fail-closed：Redis 不可用时无法确认登录态真实性，宁可拒绝服务
            // 也不放行 —— 若放行，登出/顶号全部失效，攻击面更大
            log.error("Redis 不可用，认证服务降级拒绝: {}", e.getMessage());
            writeJson(response, ResultCode.SERVICE_UNAVAILABLE, "认证服务暂不可用");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** 从请求头解析 Bearer token（格式不合法返回 null 视为匿名） */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /** 统一 JSON 错误响应（过滤器里没有 @RestControllerAdvice，只能手写） */
    private void writeJson(HttpServletResponse response, ResultCode resultCode, String msg) throws IOException {
        response.setStatus(resultCode.getCode());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(resultCode, msg)));
    }
}
