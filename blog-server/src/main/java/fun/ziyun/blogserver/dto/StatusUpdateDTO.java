package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章状态切换请求体（PUT /api/v1/articles/{id}/status）。
 *
 * <p>设计说明：状态切换单独一个接口而非复用 PUT 全量更新 ——
 * 发布/取消发布是高频动作，全量更新要求客户端携带整篇文章数据，
 * 切换接口只需一个状态字段，语义清晰、省流量、便于前端按钮直连。</p>
 */
@Data
public class StatusUpdateDTO {

    /** 目标状态：0-草稿 1-发布 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态不合法")
    @Max(value = 1, message = "状态不合法")
    private Integer status;
}
