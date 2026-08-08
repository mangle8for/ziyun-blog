package fun.ziyun.blogserver.controller;

import fun.ziyun.blogserver.common.PageResult;
import fun.ziyun.blogserver.common.Result;
import fun.ziyun.blogserver.dto.RegisterDTO;
import fun.ziyun.blogserver.dto.StatusUpdateDTO;
import fun.ziyun.blogserver.dto.UserPageQuery;
import fun.ziyun.blogserver.security.AuthUser;
import fun.ziyun.blogserver.service.UserService;
import fun.ziyun.blogserver.vo.UserAdminVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（仅管理员，由 SecurityConfig 统一拦截 /api/v1/users/**）。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    /** 分页查询用户列表（支持用户名/昵称关键字搜索） */
    @GetMapping
    public Result<PageResult<UserAdminVO>> page(@Valid UserPageQuery query) {
        return Result.ok(userService.pageUsers(query.getPage(), query.getSize(), query.getKeyword()));
    }

    /** 新增游客用户（管理员代建，普通角色；返回新用户 ID） */
    @PostMapping
    public Result<Long> create(@RequestBody @Valid RegisterDTO dto) {
        return Result.ok(userService.createUser(dto));
    }

    /** 启用/禁用用户（禁用立即踢下线） */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable @Min(1) Long id,
                                     @RequestBody @Valid StatusUpdateDTO dto,
                                     Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        userService.updateStatus(id, dto.getStatus(), authUser.getId());
        return Result.ok();
    }

    /** 重置用户密码：返回新生成的随机密码明文（仅本次响应展示一次） */
    @PutMapping("/{id}/password/reset")
    public Result<String> resetPassword(@PathVariable @Min(1) Long id,
                                        Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        return Result.ok(userService.resetPassword(id, authUser.getId()));
    }

    /** 删除用户（逻辑删除 + 踢下线；名下还有文章时返回 409） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id,
                               Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        userService.deleteUser(id, authUser.getId());
        return Result.ok();
    }
}
