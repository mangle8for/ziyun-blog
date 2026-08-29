package fun.ziyun.blogserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 核心配置。
 *
 * <p>设计说明（无状态 JWT 时代的 Security 配置逐项拆解）：</p>
 * <pre>
 * 1. CSRF 关闭：CSRF 防护针对「浏览器 Cookie 会话」的跨站请求伪造，
 *    本项目认证走 Authorization 头（JWT），攻击者无法自动携带该头
 *    （Cookie 是浏览器自动带的，自定义头必须由 JS 显式设置），
 *    因此 CSRF 在此场景无意义，开启反而干扰 REST 调用。
 * 2. Session STATELESS：不创建 HttpSession。JWT 场景下服务端无状态，
 *    SecurityContext 由 JwtAuthenticationFilter 每次请求重建。
 *    对比传统 Session 认证：Session 存服务端内存/Redis，多实例要同步；
 *    JWT 方案天然水平扩展。
 * 3. 授权规则（核心）：
 *    - ignore-paths（配置里的认证白名单）与 /auth/** 放行；
 *    - GET 请求公开（博客内容只读，任何人可看）；
 *    - 其余请求（写操作）需要认证 + ROLE_ADMIN。
 *    规则按顺序匹配，先写的先命中，故精确规则放前面。
 * 4. 401/403 JSON：默认行为是重定向到 /login 或返回 HTML 错误页，
 *    前后端分离下必须替换成 JSON（入口点处理未认证、拒绝处理器
 *    处理已认证但无权限）。
 * 5. BCrypt：密码单向哈希 + 内置盐（每次哈希不同，同密码密文不同），
 *    无法逆向；成本因子 10 是安全与性能的平衡点。
 * </pre>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    /** 认证白名单（绑定 blog.security.ignore-paths 配置） */
    private final SecurityProperties securityProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. 无状态 + 关 CSRF（见类注释）
                .csrf(AbstractHttpConfigurer::disable)
                // CORS：仅当配置了允许来源时生效（同域部署零影响）
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 2. 授权规则
                .authorizeHttpRequests(auth -> auth
                        // 认证白名单（login/register 等完全公开路径）
                        .requestMatchers(securityProperties.getIgnorePaths().toArray(new String[0])).permitAll()
                        // me/logout 需要「已登录」即可（任何角色，不止 ADMIN）——
                        // 注意必须声明在 GET 公开规则之前，先匹配先生效
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                        // 个人信息/改密/头像：任意登录用户（PUT/POST 默认会被 anyRequest
                        // 的 ADMIN 拦住，故显式声明为 authenticated；写操作中仅这几条
                        // 对普通用户开放）
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/auth/profile", "/api/v1/auth/password").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/avatar").authenticated()
                        // 用户管理接口：仅管理员（GET /api/v1/users 不能落入下方 GET 公开规则）
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                        // AI 供应商设置与写作对话：仅管理员（同理必须先于 GET 公开规则）——
                        // 供应商列表是 GET，若漏声明会被下方 permitAll 放行泄露配置
                        .requestMatchers("/api/v1/ai/**").hasRole("ADMIN")
                        // GET 公开接口（文章/分类/标签浏览）
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                        // 其余所有请求需要管理员权限（写操作/上传）
                        .anyRequest().hasRole("ADMIN"))
                // 3. 异常处理：401 / 403 输出统一 JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, ResultCode.FORBIDDEN)))
                // 4. 自定义 JWT 过滤器插入到表单登录过滤器之前
                //    （表单登录过滤器是链上默认的认证入口，JWT 要抢在它前面）
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 5. 安全响应头：在 Spring Security 默认头（nosniff/X-Frame-Options 等）
                //    基础上补 Referrer-Policy，控制跳转外站时的来源信息泄漏
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    /**
     * CORS 配置源：从 blog.security.cors-allowed-origins 读取白名单。
     *
     * <p>关键设计（同源请求必须放行）：</p>
     * <pre>
     * 1. 浏览器（Chrome 等）对同源 POST/PUT/DELETE 也会携带 Origin 头；
     *    Spring Security 的 CorsFilter 只要拿到非空 Origin 就按跨源校验，
     *    若白名单为空（同域部署的默认形态），同源登录请求会被误判为
     *    「跨源且未授权」而 403（响应 Invalid CORS request）。
     * 2. 因此这里对「非 CORS 请求（无 Origin）」与「同源请求」直接返回 null，
     *    Security CorsFilter 收到 null 配置即放行（等价于不做 CORS 处理）；
     *    只有真正的跨源请求才套用白名单校验。
     * 3. 同源判定依赖 nginx 转发的 X-Forwarded-Proto + Host（见 blog.conf），
     *    否则 HTTPS 反代后 request 的原始 scheme 是 http，会误判为跨源。
     * </pre>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (origin == null || isSameOrigin(request, origin)) {
                return null;
            }
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(securityProperties.getCorsAllowedOrigins());
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
            return config;
        };
    }

    /** 同源判定：用「转发协议 + Host」拼出期望 Origin 与请求 Origin 比对 */
    private boolean isSameOrigin(HttpServletRequest request, String origin) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader(HttpHeaders.HOST);
        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            if ((request.isSecure() && port != 443) || (!request.isSecure() && port != 80)) {
                host += ":" + port;
            }
        }
        return origin.equalsIgnoreCase(scheme + "://" + host);
    }

    /** 密码编码器：Bean 声明后 Spring Security 自动用于密码比对 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager：登录接口手动认证的入口。
     * AuthenticationConfiguration 是 Boot 自动配置提供的装配器，
     * 从它拿到的是「配好 UserDetailsService + PasswordEncoder」的实例。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private void writeJson(HttpServletResponse response, ResultCode resultCode) throws java.io.IOException {
        response.setStatus(resultCode.getCode());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(resultCode)));
    }
}
