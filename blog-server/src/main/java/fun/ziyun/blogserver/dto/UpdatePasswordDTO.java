package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求体（安全要求）：
 *  - 必须携带原密码，后端 BCrypt 比对通过才允许修改（防已登录会话被冒用）
 *  - 新密码 6-32 位，且必须同时包含字母与数字（拒绝纯数字/纯字母弱口令）
 *  - 修改成功后删除 Redis 登录态：所有会话（含当前）强制重新登录
 */
@Data
public class UpdatePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度须在 6-32 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码须同时包含字母和数字")
    private String newPassword;
}
