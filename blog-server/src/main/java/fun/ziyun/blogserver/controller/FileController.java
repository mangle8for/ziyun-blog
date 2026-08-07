package fun.ziyun.blogserver.controller;

import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口。
 * POST /api/v1/files（ADMIN）：图片上传到对象存储，返回可访问 URL。
 * 前端使用场景：md-editor-v3 的 onUploadImg 回调、封面上传控件。
 *
 * <p>设计说明：接口层只做「接文件 + 调服务」，校验职责全部下沉到
 * FileService（类型白名单/大小），Controller 保持极薄 ——
 * 换存储实现时本类零改动。</p>
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(fileService.upload(file));
    }
}
