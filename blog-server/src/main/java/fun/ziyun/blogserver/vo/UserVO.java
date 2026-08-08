package fun.ziyun.blogserver.vo;

import lombok.Data;

/**
 * 用户信息 VO（登录响应、/me、个人信息页共用）。
 * 刻意不含 password —— 密码哈希在 VO 层被隔离（对比直接把 User 实体返回的风险）。
 * email 仅返回给登录用户本人（个人信息页展示/编辑用），不会出现在公开接口。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    private Integer role;
}
