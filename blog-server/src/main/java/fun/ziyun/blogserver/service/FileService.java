package fun.ziyun.blogserver.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象接口 —— 「一键切换底层」的关键。
 *
 * <p>设计说明（存储抽象的价值）：</p>
 * <pre>
 * 业务代码（Controller/文章模块）只依赖本接口，不感知底层是
 * OSS、MinIO 还是本地磁盘。切换实现 = 改 application.yml 的
 * blog.storage.type + 有对应实现类，业务代码零改动。
 * 接口方法按「最小公共能力」设计：
 *   - 各对象存储的差异（签名 URL、防盗链、生命周期）属于各自特性，
 *     不塞进公共接口，避免接口被最复杂实现绑架；
 *   - 目前只有 upload 一个方法 —— 后续需要删除/预览时按需补充。
 * 扩展方式：新增 MinioFileServiceImpl 实现本接口，并在实现类上
 * 标注条件注解（参照 OssFileServiceImpl 的 @ConditionalOnProperty），
 * 改配置即可切换，互不干扰。
 * </pre>
 */
public interface FileService {

    /**
     * 上传文件，返回可直接访问的 URL。
     *
     * @param file 上传的二进制文件
     * @return 公开可访问的完整 URL（如 https://bucket.oss-cn-beijing.aliyuncs.com/blog/2026/08/uuid.jpg）
     */
    String upload(MultipartFile file);
}
