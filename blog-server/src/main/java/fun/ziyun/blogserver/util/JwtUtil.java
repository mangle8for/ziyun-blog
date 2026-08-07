package fun.ziyun.blogserver.util;

import fun.ziyun.blogserver.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析工具（jjwt 0.12.x）。
 *
 * <p>设计说明（JWT 是什么 + 为什么与 Redis 配合）：</p>
 * <pre>
 * JWT = 三段式字符串：Header(算法).Payload(声明).Signature(签名)。
 *   - Payload 里放 userId/username/role 等声明，Base64 编码【可读但不可改】
 *     （改任何字节都会导致签名校验失败）；
 *   - Signature 用 HMAC-SHA256 对 Header+Payload 加 secret 计算。
 * 关键点：JWT 本身只解决「防篡改」，不解决「吊销」——
 *   签发后 72 小时内 token 永远有效，除非服务端有注销手段。
 * 因此本项目采用「JWT 自校验 + Redis 白名单」双保险：
 *   JwtUtil 验证签名与过期（防伪造），Redis 验证 token 是否被登出/顶号（防滥用）。
 *
 * 对比 jjwt 0.11 旧 API：0.12 把 parserBuilder() 改为 parser()，
 * 密钥要求 HS256 时 secret 至少 32 字节 —— 这是 0.12 最常踩的坑。
 * </pre>
 */
@Component
public class JwtUtil {

    /** HS256 要求密钥 >= 32 字节（256 bit），配置的默认值 44 字节满足 */
    private final SecretKey key;

    /** token 有效期（毫秒，由配置小时数换算） */
    private final long expireMillis;

    public JwtUtil(@Value("${blog.jwt.secret}") String secret,
                   @Value("${blog.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 60 * 60 * 1000;
    }

    /**
     * 生成 token。
     *
     * <p>设计说明（subject 放什么）：subject 是 JWT 标准字段，放 userId ——
     * 它是稳定标识；username/role 放自定义 claim，前端「我的信息」可从
     * me 接口获取而非解析 token（token 里的信息过期后可能已不准确）。</p>
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMillis);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，返回标准 Claims 集合。
     *
     * <p>设计说明（异常分类）：调用方需要区分「过期」与「无效」——
     * 过期提示重新登录，无效（签名被改/格式错）直接拒绝。
     * 所以这里抛特定异常类型而不是统一抛 RuntimeException。</p>
     *
     * @throws ExpiredJwtException token 已过期
     * @throws JwtException       token 无效（签名不匹配/格式错误）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 Claims 取 userId（subject 里存的字符串转 Long） */
    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
