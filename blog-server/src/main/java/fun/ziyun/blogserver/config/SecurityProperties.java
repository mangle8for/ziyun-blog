package fun.ziyun.blogserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Security 相关配置属性绑定（blog.security 前缀）。
 *
 * <p>设计说明（为什么不用 @Value）：</p>
 * <pre>
 * @Value("${blog.security.ignore-paths}") 只支持简单标量值，
 * 对 YAML List 类型会报「Could not resolve placeholder」——
 * 这是刚才真实踩到的坑。@ConfigurationProperties 是专为
 * 结构化配置设计的绑定机制：List/Map/嵌套对象都能直接映射，
 * 还能复用同一前缀下的多个字段（避免一堆 @Value 散落各字段）。
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "blog.security")
public class SecurityProperties {

    /**
     * 认证白名单：无需登录即可访问的路径（Ant 风格通配符）。
     * 放配置文件而非代码：调整放行规则不用改代码重新编译。
     */
    private List<String> ignorePaths = new ArrayList<>();

    /**
     * CORS 允许的前端来源（浏览器跨域白名单）。
     * 同一域部署（nginx 反代）时为空列表 = CORS 不生效，零影响；
     * 前后端跨域部署（如前端独立域名）时配置为实际来源，
     * 生产环境建议经环境变量注入。
     */
    private List<String> corsAllowedOrigins = new ArrayList<>();
}
