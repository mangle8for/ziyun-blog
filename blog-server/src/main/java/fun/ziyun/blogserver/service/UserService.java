package fun.ziyun.blogserver.service;

import fun.ziyun.blogserver.common.PageResult;
import fun.ziyun.blogserver.dto.UpdateProfileDTO;
import fun.ziyun.blogserver.vo.UserAdminVO;
import fun.ziyun.blogserver.vo.UserVO;

/**
 * 用户服务：用户管理（管理员）+ 个人信息（本人）。
 */
public interface UserService {

    // ==================== 用户管理（管理员） ====================

    /** 分页查询用户列表（支持用户名/昵称关键字模糊搜索），按创建时间倒序 */
    PageResult<UserAdminVO> pageUsers(long page, long size, String keyword);

    /** 启用/禁用用户：禁用时立即清除该用户的 Redis 登录态（踢下线） */
    void updateStatus(Long id, Integer status, Long operatorId);

    /** 重置用户密码为随机强密码，返回明文（仅本次展示），并踢下线 */
    String resetPassword(Long id, Long operatorId);

    /** 删除用户（逻辑删除 + 清除登录态）；用户名下仍有文章时拒绝（409） */
    void deleteUser(Long id, Long operatorId);
}
