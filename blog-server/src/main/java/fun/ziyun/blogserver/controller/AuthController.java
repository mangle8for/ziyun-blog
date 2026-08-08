package fun.ziyun.blogserver.controller;

import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.dto.LoginDTO;
import fun.ziyun.blogserver.dto.RegisterDTO;
import fun.ziyun.blogserver.dto.UpdatePasswordDTO;
import fun.ziyun.blogserver.dto.UpdateProfileDTO;
import fun.ziyun.blogserver.security.AuthUser;
import fun.ziyun.blogserver.service.AuthService;
import fun.ziyun.blogserver.vo.LoginVO;
import fun.ziyun.blogserver.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证接口（login/register 在 Security 白名单，其余需要登录态）。
 *
 * <p>设计说明（me 与 logout 如何取当前用户）：</p>
 * <pre>
 * JwtAuthenticationFilter 认证成功后，把 AuthUser 放进了
 * SecurityContextHolder（线程绑定）。Controller 通过方法参数
 * Authentication 直接拿到 —— 相比手动 SecurityContextHolder.getContext()
 * 少一步静态访问，且 Spring 自动注入更可测。
 * AuthUser 里带着 id/role，无需再查库（token 本身可信）。
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 公开注册（为未来评论系统预留，注册用户无管理权限） */
    @PostMapping("/register")
    public Result<Long> register(@RequestBody @Valid RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    /** 公开登录：成功返回 token + 用户信息 */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    /** 登出：删除 Redis 登录态，此后旧 token 全部失效 */
    @PostMapping("/logout")
    public Result<Void> logout(Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        authService.logout(authUser.getId());
        return Result.ok();
    }

    /** 当前用户信息（前端刷新页面后恢复登录态用） */
    @GetMapping("/me")
    public Result<UserVO> me(Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        return Result.ok(authService.getCurrentUser(authUser.getId()));
    }

    /** 修改个人信息（本人，任意登录角色）：返回更新后的用户信息 */
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@RequestBody @Valid UpdateProfileDTO dto,
                                        Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        return Result.ok(authService.updateProfile(authUser.getId(), dto));
    }

    /**
     * 修改密码（本人）：校验原密码 + 更新哈希 + 清除登录态。
     * 成功后当前与历史所有会话全部失效，前端需引导重新登录。
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody @Valid UpdatePasswordDTO dto,
                                       Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        authService.updatePassword(authUser.getId(), dto);
        return Result.ok();
    }

    /**
     * 上传头像（本人，任意登录角色）：每月限 3 次。
     * 成功返回新头像 URL（用户资料已同步更新）。
     * 校验与存储走 FileService（图片类型白名单 + 5MB）。
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                       Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        return Result.ok(authService.uploadAvatar(authUser.getId(), file));
    }
}
