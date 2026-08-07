package fun.ziyun.blogserver.vo;

import lombok.Data;

/**
 * 标签 VO：分类/标签下拉与文章卡片展示共用。
 * 字段与 Tag 实体一致，但独立成 VO 是为接口契约稳定 ——
 * 未来实体新增审计字段时不会污染 API 响应。
 */
@Data
public class TagVO {

    private Long id;

    private String name;
}
