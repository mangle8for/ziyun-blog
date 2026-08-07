package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体。
 *
 * <p>设计说明（用户名/密码格式约束）：</p>
 * <pre>
 * 用户名：4~20 位字母/数字/下划线 —— 限制字符集防止用户名里
 * 混入空格、emoji 等造成显示与匹配混乱，也杜绝 SQL/脚本注入面。
 * 密码：6~32 位 —— 长度下限防弱口令，上限防超长哈希计算攻击
 * （BCrypt 对超长输入的处理开销异常，是已知的 DoS 向量）。
 * </pre>
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名须为 4-20 位字母/数字/下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须在 6-32 位之间")
    private String password;

    /** 昵称：选填，留空则默认用用户名 */
    @Size(max = 50, message = "昵称不能超过 50 字")
    private String nickname;
}
