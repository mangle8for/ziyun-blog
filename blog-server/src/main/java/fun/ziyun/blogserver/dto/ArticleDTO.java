package fun.ziyun.blogserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 文章新增/更新请求体（POST / PUT 共用）。
 *
 * <p>设计说明（DTO 与实体的分工）：</p>
 * <pre>
 * 为什么不直接拿 Article 实体当请求体？
 *   1. 安全：实体的 authorId/status/viewCount 等字段若被客户端
 *      伪造赋值（如越权把 viewCount 改成 999999），后端无法区分
 *      是用户传的还是自己填的；DTO 只暴露「用户有权改」的字段。
 *   2. 约束：字段级校验注解写在 DTO 上（实体是表投影，不该背校验职责），
 *      新增与更新语义一致时共用同一个 DTO。
 *   3. 类型：tagIds 这种「接口层才有」的字段（表里是中间表），
 *      实体天然不承载，必须由 DTO 表达。
 * </pre>
 */
@Data
public class ArticleDTO {

    /** 标题：必填且不超 200 字（对应表 varchar(200)，超长会 INSERT 报错，先拦在入口） */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    /** 摘要：选填，最多 500 字 */
    @Size(max = 500, message = "摘要不能超过 500 字")
    private String summary;

    /** 正文 Markdown：必填（空文章没有存在意义，草稿也不例外） */
    @NotBlank(message = "正文不能为空")
    private String content;

    /** 封面 URL：选填，P2 接 MinIO 后由前端上传后回填 */
    private String cover;

    /** 分类 ID：选填（可为空 = 未分类），由 Service 校验是否存在 */
    private Long categoryId;

    /** 置顶：选填（0-普通 1-置顶），null 时新增按 0、更新保持原值 */
    @Min(value = 0, message = "置顶标记不合法")
    @Max(value = 1, message = "置顶标记不合法")
    private Integer pinned;

    /** 状态：0-草稿 1-发布，必须显式传入（用 0/1 与表结构对齐，不用布尔） */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态不合法")
    @Max(value = 1, message = "状态不合法")
    private Integer status;

    /**
     * 标签 ID 列表：选填，可为空列表。
     * 更新时「先删后插」全量替换 —— 前端编辑页直接提交最终标签集合即可，
     * 无需计算增删差异。
     */
    private List<Long> tagIds;
}
