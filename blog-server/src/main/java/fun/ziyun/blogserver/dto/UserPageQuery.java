package fun.ziyun.blogserver.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户管理分页查询（管理员后台）。
 * 在通用 PageQuery 基础上追加关键字搜索：username / nickname 模糊匹配。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQuery extends PageQuery {

    /** 搜索关键字：按用户名/昵称模糊匹配（可选） */
    private String keyword;
}
