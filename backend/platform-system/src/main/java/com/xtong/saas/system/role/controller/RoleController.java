package com.xtong.saas.system.role.controller;

import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.role.dto.AssignRoleMenusDTO;
import com.xtong.saas.system.role.dto.CreateRoleDTO;
import com.xtong.saas.system.role.dto.RoleQueryDTO;
import com.xtong.saas.system.role.dto.UpdateRoleDTO;
import com.xtong.saas.system.role.service.RoleService;
import com.xtong.saas.system.role.vo.RoleVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 暴露当前受信租户内角色查询、维护和全局菜单授权的 HTTP API。 */
@Validated
@RestController
@RequestMapping("/api/v1/system/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<PageResult<RoleVO>> page(@Valid RoleQueryDTO query) {
        return Result.success(roleService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:detail')")
    public Result<RoleVO> get(@PathVariable long id) {
        return Result.success(roleService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    public Result<String> create(@Valid @RequestBody CreateRoleDTO command) {
        return Result.success(roleService.create(command));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody UpdateRoleDTO command) {
        roleService.update(id, command);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('system:role:enable')")
    public Result<Void> enable(@PathVariable long id) {
        roleService.enable(id);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('system:role:disable')")
    public Result<Void> disable(@PathVariable long id) {
        roleService.disable(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public Result<Void> delete(@PathVariable long id) {
        roleService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:assign-menu')")
    public Result<Void> assignMenus(@PathVariable long id, @Valid @RequestBody AssignRoleMenusDTO command) {
        roleService.assignMenus(id, command.menuIds());
        return Result.success();
    }
}
