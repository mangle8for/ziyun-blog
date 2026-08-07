package fun.ziyun.blogserver.vo;

import lombok.Data;

/**
 * 上一篇/下一篇导航 VO：只带跳转所需的最小字段，
 * 避免把整篇相邻文章序列化进详情响应。
 */
@Data
public class ArticleNavVO {

    private Long id;

    private String title;
}
