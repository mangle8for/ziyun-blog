package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人信息请求体（登录用户修改自己的资料）。
 * 字段均可为 null（表示不修改）：
 *  - nickname：必填（前端表单总是提交），空串按 null 处理
 *  - email：可选，空串按 null 处理（清空邮箱）
 *  - avatar：可选，图片 URL（当前版本前端直接填 URL，不做上传）
 */
@Data
public class UpdateProfileDTO {

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    @Size(max = 255, message = "头像地址长度不能超过 255")
    private String avatar;
}
