package fun.ziyun.blogserver.vo;

import lombok.Data;

/**
 * 热门标签 VO：与 CategoryHotVO 对称，
 * 标签基础信息 + 「已发布文章数」聚合结果。
 */
@Data
public class TagHotVO {

    private Long id;

    private String name;

    /** 已发布且未删除的文章数（0 文章的标签不会出现在结果中） */
    private Integer articleCount;
}
