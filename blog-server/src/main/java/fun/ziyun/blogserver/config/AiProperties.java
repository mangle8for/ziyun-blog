package fun.ziyun.blogserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 写作功能配置（绑定 blog.ai 前缀）。
 *
 * <p>密钥说明：encrypt-key 用于 API Key 的 AES/GCM 加密落库。
 * 经环境变量 AI_ENCRYPT_KEY 注入；未配置时回退复用 JWT 签名密钥
 * 派生（本地开发零配置可用，生产二者均由环境变量提供）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "blog.ai")
public class AiProperties {

    /** API Key 加密密钥（任意长字符串，代码内先 SHA-256 派生 AES 密钥） */
    private String encryptKey = "";

    /** 上游连接超时（秒） */
    private long connectTimeoutSeconds = 10;

    /** 上游整体响应超时（秒）：覆盖流式生成的完整时长 */
    private long readTimeoutSeconds = 180;

    /** 发送给模型的正文上下文最大字符数（超出截断，控制 token 成本） */
    private int maxContextChars = 6000;
}
