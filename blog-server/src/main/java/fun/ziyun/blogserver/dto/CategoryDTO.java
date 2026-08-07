package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增/更新请求体。
 */
@Data
public class CategoryDTO {

    /** 分类名：必填且唯一（唯一性由数据库 uk_name 兜底，Service 前置校验） */
    @NotBlank(message = "分类名不能为空")
    @Size(max = 50, message = "分类名不能超过 50 字")
    private String name;

    /** 分类简介：选填，最多 255 字 */
    @Size(max = 255, message = "简介不能超过 255 字")
    private String description;
}
