package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章置顶切换请求体（PUT /api/v1/articles/{id}/pinned）。
 *
 * <p>设计说明：与 StatusUpdateDTO 同构但字段语义独立 ——
 * 「置顶」与「发布状态」是两个正交维度，共用 DTO 会让
 * {status: 1} 这类载荷含义模糊；独立 DTO 让接口契约自解释。</p>
 */
@Data
public class PinnedUpdateDTO {

    /** 目标置顶状态：0-取消置顶 1-置顶 */
    @NotNull(message = "置顶状态不能为空")
    @Min(value = 0, message = "置顶状态不合法")
    @Max(value = 1, message = "置顶状态不合法")
    private Integer pinned;
}
