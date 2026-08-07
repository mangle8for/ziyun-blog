package fun.ziyun.blogserver.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情 VO（前台详情页）。
 * 相对列表项多出 content 正文与上一篇/下一篇导航。
 */
@Data
public class ArticleDetailVO {

    private Long id;

    private String title;

    private String summary;

    /** 正文 Markdown 原文（前端用 MdPreview 渲染） */
    private String content;

    private String cover;

    private Long categoryId;

    private String categoryName;

    private String authorName;

    private Integer status;

    private Integer viewCount;

    private Integer likeCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<TagVO> tags;

    /** 上一篇（按发布时间早于当前文章倒序取最近一篇，无则 null） */
    private ArticleNavVO prevArticle;

    /** 下一篇（按发布时间晚于当前文章正序取最近一篇，无则 null） */
    private ArticleNavVO nextArticle;
}
