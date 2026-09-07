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
import org.springframework.web.bind.annotation.*;

/** 暴露当前受信租户内角色查询、维护和全局菜单授权的 HTTP API。 */
@Validated
@RestController
@RequestMapping("/api/v1/system/roles")
public class RoleController {

    private final RoleService roleService;

    /**
     * 创建角色管理控制器。
     *
     * @param roleService 角色管理领域服务
     */
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * 分页查询当前租户内的角色。
     *
     * @param query 角色分页与筛选条件
     * @return 角色分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<PageResult<RoleVO>> page(@Valid RoleQueryDTO query) {
        return Result.success(roleService.page(query));
    }

    /**
     * 查询当前租户内指定角色的详情。
     *
     * @param id 角色 ID
     * @return 角色详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:detail')")
    public Result<RoleVO> get(@PathVariable long id) {
        return Result.success(roleService.get(id));
    }

    /**
     * 在当前租户内创建角色。
     *
     * @param command 角色创建参数
     * @return 新角色的字符串 ID
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    public Result<String> create(@Valid @RequestBody CreateRoleDTO command) {
        return Result.success(roleService.create(command));
    }

    /**
     * 更新当前租户内指定角色的基本信息。
     *
     * @param id 角色 ID
     * @param command 角色更新参数
     * @return 无数据的成功响应
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody UpdateRoleDTO command) {
        roleService.update(id, command);
        return Result.success();
    }

    /**
     * 启用当前租户内的指定角色。
     *
     * @param id 角色 ID
     * @return 无数据的成功响应
     */
    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('system:role:enable')")
    public Result<Void> enable(@PathVariable long id) {
        roleService.enable(id);
        return Result.success();
    }

    /**
     * 停用当前租户内的指定角色。
     *
     * @param id 角色 ID
     * @return 无数据的成功响应
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('system:role:disable')")
    public Result<Void> disable(@PathVariable long id) {
        roleService.disable(id);
        return Result.success();
    }

    /**
     * 删除当前租户内的指定角色。
     *
     * @param id 角色 ID
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public Result<Void> delete(@PathVariable long id) {
        roleService.delete(id);
        return Result.success();
    }

    /**
     * 整体替换当前租户内指定角色可访问的菜单集合。
     *
     * @param id 角色 ID
     * @param command 待分配的菜单 ID 集合
     * @return 无数据的成功响应
     */
    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:assign-menu')")
    public Result<Void> assignMenus(@PathVariable long id, @Valid @RequestBody AssignRoleMenusDTO command) {
        roleService.assignMenus(id, command.menuIds());
        return Result.success();
    }
}
