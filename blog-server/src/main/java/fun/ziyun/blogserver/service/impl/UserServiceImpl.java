package fun.ziyun.blogserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.ziyun.blogserver.common.PageResult;
import fun.ziyun.blogserver.common.ResultCode;
import fun.ziyun.blogserver.entity.Article;
import fun.ziyun.blogserver.entity.User;
import fun.ziyun.blogserver.exception.BusinessException;
import fun.ziyun.blogserver.mapper.ArticleMapper;
import fun.ziyun.blogserver.mapper.UserMapper;
import fun.ziyun.blogserver.service.UserService;
import fun.ziyun.blogserver.vo.UserAdminVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * 用户管理实现。
 *
 * <p>安全规则（管理员操作他人账号时）：
 * <pre>
 * 1. 不能操作自己的账号（防止自锁/自降级）；
 * 2. id=1 是内置超级管理员，禁止禁用/删除（防误操作锁死系统）；
 * 3. 禁用 / 删除 / 重置密码 一律清除 Redis 登录态，立即踢下线；
 * 4. 删除用户前校验其名下文章：有文章则 409 拒绝（保护数据完整性）。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 内置超级管理员 ID（init.sql 预置，禁止禁用/删除） */
    private static final long SUPER_ADMIN_ID = 1L;

    /** Redis 登录态 key 前缀（与 JwtAuthenticationFilter / AuthServiceImpl 一致） */
    private static final String LOGIN_TOKEN_KEY = "login:token:";

    /** 重置密码随机字符集：去易混淆字符（0/O、1/l/I） */
    private static final char[] PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789".toCharArray();

    private final ArticleMapper articleMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult<UserAdminVO> pageUsers(long page, long size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword));
        }
        // 新注册的排前面（管理员最关心最近加入的用户）
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> result = this.page(new Page<>(page, size), wrapper);
        return PageResult.from(result.convert(this::toAdminVO));
    }

    @Override
    public void updateStatus(Long id, Integer status, Long operatorId) {
        checkTargetUser(id, operatorId, true);
        if (id == SUPER_ADMIN_ID) {
            throw new BusinessException(ResultCode.FORBIDDEN, "内置管理员账号不允许禁用");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        this.updateById(update);
        // 禁用立即踢下线：删除 Redis 登录态，该用户所有 token 失效
        if (status != null && status == 0) {
            kickOut(id);
            log.info("管理员 {} 禁用了用户 {}", operatorId, id);
        }
    }

    @Override
    public String resetPassword(Long id, Long operatorId) {
        checkTargetUser(id, operatorId, false);
        String rawPassword = generatePassword();
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(rawPassword));
        this.updateById(update);
        // 重置后强制重新登录（旧密码与旧 token 全部失效）
        kickOut(id);
        log.info("管理员 {} 重置了用户 {} 的密码", operatorId, id);
        return rawPassword;
    }

    @Override
    public void deleteUser(Long id, Long operatorId) {
        checkTargetUser(id, operatorId, true);
        if (id == SUPER_ADMIN_ID) {
            throw new BusinessException(ResultCode.FORBIDDEN, "内置管理员账号不允许删除");
        }
        // 数据完整性：用户名下还有文章时拒绝删除（逻辑外键保护）
        Long articleCount = articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, id));
        if (articleCount != null && articleCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "该用户名下还有 " + articleCount + " 篇文章，请先处理文章再删除用户");
        }
        // @TableLogic 逻辑删除：deleted=1，历史数据保留可审计
        this.removeById(id);
        kickOut(id);
        log.info("管理员 {} 删除了用户 {}", operatorId, id);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验目标用户存在 + 不是操作者本人。
     *
     * @param selfAllowed 是否允许操作自己（当前统一不允许）
     */
    private void checkTargetUser(Long id, Long operatorId, boolean selfAllowed) {
        if (!selfAllowed && id.equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能对自己执行该操作");
        }
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在，id=" + id);
        }
    }

    /** 清除指定用户的 Redis 登录态（踢下线） */
    private void kickOut(Long userId) {
        stringRedisTemplate.delete(LOGIN_TOKEN_KEY + userId);
    }

    /** 生成 10 位随机强密码（字母+数字混合，去易混淆字符） */
    private String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_CHARS[random.nextInt(PASSWORD_CHARS.length)]);
        }
        // 保证同时含字母与数字（循环内随机可能全字母，补位兜底）
        if (sb.chars().noneMatch(Character::isDigit)) {
            sb.setCharAt(random.nextInt(10), "23456789".charAt(random.nextInt(8)));
        }
        if (sb.chars().noneMatch(Character::isLetter)) {
            sb.setCharAt(random.nextInt(10), "ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(random.nextInt(24)));
        }
        return sb.toString();
    }

    private UserAdminVO toAdminVO(User user) {
        UserAdminVO vo = new UserAdminVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
