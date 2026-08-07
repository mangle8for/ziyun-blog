package fun.ziyun.blogserver.vo;

import lombok.Data;

/**
 * 用户信息 VO（登录响应与 /me 共用）。
 * 刻意不含 password/email —— 接口契约只暴露展示所需字段，
 * 实体里的敏感字段在 VO 层被隔离（对比直接把 User 实体返回的风险）。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private Integer role;
}
