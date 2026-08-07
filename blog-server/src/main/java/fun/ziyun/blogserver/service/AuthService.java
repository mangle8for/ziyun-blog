package fun.ziyun.blogserver.service;

import fun.ziyun.blogserver.dto.LoginDTO;
import fun.ziyun.blogserver.dto.RegisterDTO;
import fun.ziyun.blogserver.vo.LoginVO;
import fun.ziyun.blogserver.vo.UserVO;

/**
 * 认证服务：注册/登录/登出/当前用户。
 */
public interface AuthService {

    /** 注册：创建普通用户（role=0），返回用户 ID */
    Long register(RegisterDTO dto);

    /** 登录：校验密码 + 签发 JWT + 写入 Redis 登录态 */
    LoginVO login(LoginDTO dto);

    /** 登出：删除 Redis 登录态（实现踢下线） */
    void logout(Long userId);

    /** 当前登录用户信息（GET /me 用） */
    UserVO getCurrentUser(Long userId);
}
