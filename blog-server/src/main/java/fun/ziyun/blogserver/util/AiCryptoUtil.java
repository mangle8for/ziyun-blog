package fun.ziyun.blogserver.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 加密工具（AES/GCM）。
 *
 * <p>设计说明：</p>
 * <pre>
 * 1. 密钥来源：blog.ai.encrypt-key（环境变量 AI_ENCRYPT_KEY），
 *    未配置时回退复用 JWT 签名密钥 —— 本地开发零配置可用，
 *    生产两者均由环境变量提供；密钥任意长度，先 SHA-256 派生 256 位 AES 密钥。
 * 2. 算法选 AES/GCM/NoPadding：带认证的加密（篡改密文解密直接失败），
 *    随机 12 字节 IV 每次加密重新生成，存储格式 Base64(IV + 密文+认证标签)。
 * 3. 零第三方依赖：JDK 自带 javax.crypto 即可，不引入 jasypt 等额外包。
 * </pre>
 */
@Component
public class AiCryptoUtil {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiCryptoUtil(@Value("${blog.ai.encrypt-key:}") String encryptKey,
                        @Value("${blog.jwt.secret:}") String jwtSecret) {
        // 加密密钥未配置时回退 JWT 密钥（二者都来自环境变量，见 AiProperties 注释）
        String source = (encryptKey == null || encryptKey.isBlank()) ? jwtSecret : encryptKey;
        if (source == null || source.isBlank()) {
            throw new IllegalStateException(
                    "AI 加密密钥缺失：请配置环境变量 AI_ENCRYPT_KEY 或 JWT_SECRET");
        }
        this.secretKey = deriveKey(source);
    }

    /** 明文 -> Base64(IV + 密文+认证标签) */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    /** Base64(IV + 密文+认证标签) -> 明文 */
    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,
                    new GCMParameterSpec(GCM_TAG_BITS, combined, 0, GCM_IV_BYTES));
            byte[] plainText = cipher.doFinal(combined, GCM_IV_BYTES, combined.length - GCM_IV_BYTES);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败（加密密钥是否变更过？）", e);
        }
    }

    /** 任意长度密钥字符串 -> 256 位 AES 密钥（SHA-256 派生） */
    private SecretKey deriveKey(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("加密密钥派生失败", e);
        }
    }
}
