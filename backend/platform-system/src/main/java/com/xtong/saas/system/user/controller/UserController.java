package com.xtong.saas.system.user.controller;

import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.user.dto.*;
import com.xtong.saas.system.user.service.UserService;
import com.xtong.saas.system.user.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 暴露当前受信租户内用户查询、维护和角色分配的 HTTP API。 */
@Validated
@RestController
@RequestMapping("/api/v1/system/users")
public class UserController {

    private final UserService userService;

    /**
     * 创建用户管理控制器。
     *
     * @param userService 用户管理领域服务
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询当前租户内的用户。
     *
     * @param query 用户分页与筛选条件
     * @return 用户分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<PageResult<UserVO>> page(@Valid UserQueryDTO query) {
        return Result.success(userService.page(query));
    }

    /**
     * 查询当前租户内指定用户的详情。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:detail')")
    public Result<UserVO> get(@PathVariable long id) {
        return Result.success(userService.get(id));
    }

    /**
     * 在当前租户内创建用户。
     *
     * @param command 用户创建参数
     * @return 新用户的字符串 ID
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    public Result<String> create(@Valid @RequestBody CreateUserDTO command) {
        return Result.success(userService.create(command));
    }

    /**
     * 更新当前租户内指定用户的基本信息。
     *
     * @param id 用户 ID
     * @param command 用户更新参数
     * @return 无数据的成功响应
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody UpdateUserDTO command) {
        userService.update(id, command);
        return Result.success();
    }

    /**
     * 启用当前租户内的指定用户。
     *
     * @param id 用户 ID
     * @return 无数据的成功响应
     */
    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('system:user:enable')")
    public Result<Void> enable(@PathVariable long id) {
        userService.enable(id);
        return Result.success();
    }

    /**
     * 停用当前租户内的指定用户，并防止当前用户停用自身。
     *
     * @param id 待停用的用户 ID
     * @param principal 当前认证用户
     * @return 无数据的成功响应
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('system:user:disable')")
    public Result<Void> disable(
            @PathVariable long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        userService.disable(id, principal.userId());
        return Result.success();
    }

    /**
     * 重置当前租户内指定用户的登录密码。
     *
     * @param id 用户 ID
     * @param command 新密码参数
     * @return 无数据的成功响应
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset-password')")
    public Result<Void> resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordDTO command) {
        userService.resetPassword(id, command);
        return Result.success();
    }

    /**
     * 删除当前租户内的指定用户，并防止当前用户删除自身。
     *
     * @param id 待删除的用户 ID
     * @param principal 当前认证用户
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public Result<Void> delete(
            @PathVariable long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        userService.delete(id, principal.userId());
        return Result.success();
    }

    /**
     * 整体替换当前租户内指定用户的角色集合。
     *
     * @param id 用户 ID
     * @param command 待分配的角色 ID 集合
     * @return 无数据的成功响应
     */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:assign-role')")
    public Result<Void> assignRoles(@PathVariable long id, @Valid @RequestBody AssignUserRolesDTO command) {
        userService.assignRoles(id, command.roleIds());
        return Result.success();
    }
}
