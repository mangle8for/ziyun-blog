package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标签新增/更新请求体（标签只有 name 一个业务字段）。
 */
@Data
public class TagDTO {

    /** 标签名：必填且唯一 */
    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名不能超过 50 字")
    private String name;
}
