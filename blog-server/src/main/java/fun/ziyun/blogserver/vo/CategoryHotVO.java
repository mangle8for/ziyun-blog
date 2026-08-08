package fun.ziyun.blogserver.vo;

import lombok.Data;

/**
 * 热门分类 VO：分类基础信息 + 「已发布文章数」聚合结果，
 * 供前台首页筛选区按热度展示 TopN。
 *
 * <p>articleCount 用 Integer 而非 Long：COUNT 结果在业务上
 * 不会超 int 范围，且避免全局 Long -> String 序列化让前端
 * 多一次 Number() 转换。</p>
 */
@Data
public class CategoryHotVO {

    private Long id;

    private String name;

    /** 已发布且未删除的文章数（0 文章的分类不会出现在结果中） */
    private Integer articleCount;
}
