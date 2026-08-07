package fun.ziyun.blogserver.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.config.OssProperties;
import fun.ziyun.blogserver.exception.BusinessException;
import fun.ziyun.blogserver.service.FileService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 阿里云 OSS 文件存储实现。
 *
 * <p>设计说明（条件装配 —— 存储开关的底层机制）：</p>
 * <pre>
 * @ConditionalOnProperty(name = "blog.storage.type", havingValue = "oss")：
 * 仅当配置 blog.storage.type=oss 时本 Bean 才注册进容器。
 * Spring Boot 自动配置大量使用此类条件注解（如 classpath 有 MySQL
 * 驱动才装配数据源），这里拿来控制存储实现：
 *   - FileService 接口被注入时，容器里恰好只有匹配的那个实现；
 *   - 未来加 MinioFileServiceImpl 标注 havingValue="minio"，
 *     两个实现互斥共存，改配置即切换，零代码改动。
 *
 * 上传安全三连：
 *   1. 类型白名单：只允许图片扩展名（jpg/jpeg/png/webp/gif），
 *      防上传可执行文件/脚本后通过 OSS 静态域名被当 HTML 执行（XSS）；
 *   2. 大小限制 5MB：防存储滥用与带宽消耗；
 *   3. UUID 重命名：丢弃用户原始文件名 —— 原始名可能含路径穿越
 *      （../）、恶意脚本片段，且重名会互相覆盖。
 * </pre>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "blog.storage.type", havingValue = "oss")
public class OssFileServiceImpl implements FileService {

    /** 允许的图片扩展名（小写，不含点） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** 上传大小上限：5MB */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final OssProperties properties;
    private final OSS ossClient;

    public OssFileServiceImpl(OssProperties properties) {
        this.properties = properties;
        // OSSClient 是线程安全的，全局单例复用（对比每请求新建的开销）
        this.ossClient = new OSSClientBuilder().build(
                properties.getEndpoint(), properties.getAccessKeyId(), properties.getAccessKeySecret());
    }

    @Override
    public String upload(MultipartFile file) {
        validate(file);

        // key 规则：blog/yyyy/MM/uuid.ext —— 按日期分目录便于按时间
        // 前缀管理/清理（OSS 无目录概念，前缀模拟）
        String ext = getExtension(file.getOriginalFilename());
        String key = properties.getBasePath() + "/"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            ossClient.putObject(properties.getBucket(), key, file.getInputStream());
        } catch (IOException e) {
            // 网络/IO 异常属服务器侧问题，记完整日志
            log.error("OSS 上传失败", e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "文件上传失败，请稍后重试");
        }

        // 桶设为公共读时该 URL 可直接访问（需在 OSS 控制台配置桶权限）
        // 拼接规则：https://{bucket}.{endpoint主机}/{key}
        String host = properties.getEndpoint().replace("https://", "").replace("http://", "");
        return "https://" + properties.getBucket() + "." + host + "/" + key;
    }

    /** 上传前置校验（空文件/类型/大小） */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件大小不能超过 5MB");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "仅支持图片类型: " + String.join("/", ALLOWED_EXTENSIONS));
        }
    }

    /** 提取扩展名（小写）；无扩展名/隐藏文件（.gitignore 之类）返回空串 */
    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? "" : filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /** 应用关闭时释放 OSS 连接资源（避免连接泄漏） */
    @PreDestroy
    public void close() {
        ossClient.shutdown();
    }
}
