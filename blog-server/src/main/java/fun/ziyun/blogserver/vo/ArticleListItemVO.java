package fun.ziyun.blogserver.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项 VO（前台首页卡片 / 管理端列表共用）。
 *
 * <p>设计说明（VO 与实体的区别）：</p>
 * <pre>
 * 列表接口刻意【不返回 content 正文】—— 它是 LONGTEXT 大字段，
 * 列表一页 10 篇文章全量带出，SQL 传输与 JSON 体积都会放大数倍，
 * 而列表页 UI 根本用不到正文（卡片只显示标题/摘要/封面）。
 * VO 层砍掉 content，从接口契约层面杜绝这种浪费。
 * 额外字段（categoryName / authorName / tags）是「跨表联查的展示值」，
 * 实体是单表投影，装不下这些 —— 这正是 VO 存在的原因。
 * </pre>
 */
@Data
public class ArticleListItemVO {

    /** 雪花 ID（JSON 序列化为 String，见 JacksonConfig 说明） */
    private Long id;

    private String title;

    private String summary;

    private String cover;

    /** 分类 ID 与分类名（未分类时两者均 null） */
    private Long categoryId;

    private String categoryName;

    /** 作者昵称（联查 user 表，展示用） */
    private String authorName;

    /** 状态：0-草稿 1-发布（管理端列表需要区分，前台列表恒为 1） */
    private Integer status;

    private Integer viewCount;

    private Integer likeCount;

    private LocalDateTime createTime;

    /** 文章标签列表（联查 article_tag + tag 表） */
    private List<TagVO> tags;
}
