package fun.ziyun.blogserver.service;

import fun.ziyun.blogserver.dto.LoginDTO;
import fun.ziyun.blogserver.dto.RegisterDTO;
import fun.ziyun.blogserver.dto.UpdatePasswordDTO;
import fun.ziyun.blogserver.dto.UpdateProfileDTO;
import fun.ziyun.blogserver.vo.LoginVO;
import fun.ziyun.blogserver.vo.UserVO;

/**
 * 认证服务：注册/登录/登出/当前用户/个人信息/改密。
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

    /** 修改个人信息（本人）：昵称/邮箱/头像，返回更新后的用户信息 */
    UserVO updateProfile(Long userId, UpdateProfileDTO dto);

    /**
     * 修改密码（本人）：
     * 校验原密码 -> 更新 BCrypt 哈希 -> 清除 Redis 登录态。
     * 清除后所有会话（含当前）的 token 立即失效，需重新登录。
     */
    void updatePassword(Long userId, UpdatePasswordDTO dto);
}
