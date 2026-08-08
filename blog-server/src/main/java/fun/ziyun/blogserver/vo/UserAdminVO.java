package fun.ziyun.blogserver.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户管理列表 VO（仅管理员可见）。
 * 相比登录用户自己的 UserVO，额外暴露 email 与管理字段（status/createTime/updateTime）；
 * 依然不暴露 password —— 密码哈希永不出实体层。
 */
@Data
public class UserAdminVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    /** 角色：0-普通用户 1-管理员 */
    private Integer role;

    /** 状态：0-禁用 1-正常 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
