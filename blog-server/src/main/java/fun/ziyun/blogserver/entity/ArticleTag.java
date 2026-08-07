package fun.ziyun.blogserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文章-标签关联实体，对应表 article_tag（复合主键，无自增 id）。
 *
 * <p>设计说明（为什么这个实体没有 @TableId）：</p>
 * <pre>
 * MP 的 BaseMapper 内置方法（selectById 等）都依赖单一主键，
 * 复合主键表无法使用它们 —— 本实体的所有操作（按文章查标签、
 * 删除某文章的关联、批量插入）都是自定义 SQL，写在 ArticleTagMapper
 * 的注解里。这也提醒我们：中间表尽量只做「纯关联」，别把业务
 * 塞进关联表，否则会处处受制于单主键约束。
 * </pre>
 */
@Data
@TableName("article_tag")
public class ArticleTag {

    /** 文章 ID（复合主键之一） */
    private Long articleId;

    /** 标签 ID（复合主键之二） */
    private Long tagId;
}
