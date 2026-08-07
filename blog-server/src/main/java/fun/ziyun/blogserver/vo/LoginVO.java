package fun.ziyun.blogserver.vo;

import lombok.Data;

/**
 * 登录响应 VO：token + 用户信息一次返回，
 * 前端登录后无需再调 /me（省一次请求，且 token 与用户绑定关系明确）。
 */
@Data
public class LoginVO {

    /** JWT（前端存 localStorage，请求时放 Authorization 头） */
    private String token;

    private UserVO user;
}
