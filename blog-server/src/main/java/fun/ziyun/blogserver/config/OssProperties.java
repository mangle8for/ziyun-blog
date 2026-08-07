package fun.ziyun.blogserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置属性绑定。
 *
 * <p>设计说明（@ConfigurationProperties vs @Value）：</p>
 * <pre>
 * @Value 逐个注入：字段多时代码臃肿，且类型转换/校验要靠自己；
 * @ConfigurationProperties 把前缀 blog.storage.oss 下的配置整体绑定到
 * 本对象，字段名与 yml 松耦合（可再配 relaxed binding），
 * 还能配合 @Validated 做启动时校验（本类未启用，够用即可）。
 * 相比 Spring Boot 老版本还要手动 @EnableConfigurationProperties 注册，
 * @Component 直接声明即可被扫描装配。
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "blog.storage.oss")
public class OssProperties {

    /** 区域 Endpoint（如 https://oss-cn-beijing.aliyuncs.com，不带 bucket 前缀） */
    private String endpoint;

    /** AccessKey ID（生产用环境变量注入） */
    private String accessKeyId;

    /** AccessKey Secret（生产用环境变量注入） */
    private String accessKeySecret;

    /** 桶名 */
    private String bucket;

    /** 桶内目录前缀（如 blog，最终 key 形如 blog/2026/08/uuid.jpg） */
    private String basePath;
}
