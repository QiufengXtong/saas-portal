package com.xtong.saas.system.user.controller;

import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.auth.model.AuthenticatedUser;
import com.xtong.saas.system.user.dto.AssignUserRolesDTO;
import com.xtong.saas.system.user.dto.CreateUserDTO;
import com.xtong.saas.system.user.dto.ResetPasswordDTO;
import com.xtong.saas.system.user.dto.UpdateUserDTO;
import com.xtong.saas.system.user.dto.UserQueryDTO;
import com.xtong.saas.system.user.service.UserService;
import com.xtong.saas.system.user.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 暴露当前受信租户内用户查询、维护和角色分配的 HTTP API。 */
@Validated
@RestController
@RequestMapping("/api/v1/system/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<PageResult<UserVO>> page(@Valid UserQueryDTO query) {
        return Result.success(userService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:detail')")
    public Result<UserVO> get(@PathVariable long id) {
        return Result.success(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    public Result<String> create(@Valid @RequestBody CreateUserDTO command) {
        return Result.success(userService.create(command));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody UpdateUserDTO command) {
        userService.update(id, command);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('system:user:enable')")
    public Result<Void> enable(@PathVariable long id) {
        userService.enable(id);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('system:user:disable')")
    public Result<Void> disable(
            @PathVariable long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        userService.disable(id, principal.userId());
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset-password')")
    public Result<Void> resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordDTO command) {
        userService.resetPassword(id, command);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public Result<Void> delete(
            @PathVariable long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        userService.delete(id, principal.userId());
        return Result.success();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:assign-role')")
    public Result<Void> assignRoles(@PathVariable long id, @Valid @RequestBody AssignUserRolesDTO command) {
        userService.assignRoles(id, command.roleIds());
        return Result.success();
    }
}
