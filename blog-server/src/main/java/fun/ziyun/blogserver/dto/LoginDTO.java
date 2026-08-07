package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求体。
 * 密码约束与注册一致（防止超长输入触发 BCrypt 计算开销攻击）。
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 32, message = "密码长度不能超过 32 位")
    private String password;
}
