package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询通用请求参数（GET query 绑定）。
 *
 * <p>设计说明（分页参数边界校验）：</p>
 * <pre>
 * 分页参数是攻击与性能问题的重灾区：
 *   - page 传 0 或负数 -> SQL LIMIT 负偏移，MySQL 报错或返回异常结果；
 *   - size 传 100000 -> 一次查出全表，拖垮数据库与网络。
 * 因此在参数入口就设硬边界：page>=1、size 1~50。
 * 校验失败由 GlobalExceptionHandler 统一返回 400，
 * 前端分页器只会在正常区间取值，正常用户永远触碰不到这些错误。
 *
 * GET 参数的 @Valid 校验生效条件：Controller 方法参数上加 @Validated，
 * 注意与 @RequestBody 的 @Valid 是两个不同触发点（详见 ArticleController）。
 * </pre>
 */
@Data
public class PageQuery {

    /** 当前页码，从 1 开始（前端分页器默认 1） */
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    /** 每页条数，上限 50（博客文章卡片列表实际用 6~10，上限留足） */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    private long size = 10;
}
