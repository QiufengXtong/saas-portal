package com.xtong.saas.system.menu.controller;

import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.menu.dto.CreateMenuDTO;
import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;
import com.xtong.saas.system.menu.dto.UpdateMenuDTO;
import com.xtong.saas.system.menu.service.MenuService;
import com.xtong.saas.system.menu.vo.MenuVO;
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

import java.util.List;
import java.util.Set;

/** 暴露全局菜单树、权限码目录及菜单权限资源维护 HTTP API。 */
@Validated
@RestController
@RequestMapping("/api/v1/system/menus")
public class MenuController {

    private final MenuService menuService;

    /**
     * 创建菜单查询控制器。
     *
     * @param menuService 菜单目录领域服务
     */
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 查询启用状态的全局菜单树。
     *
     * @return 按层级和顺序组织的菜单树
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:tree')")
    public Result<List<MenuTreeNodeVO>> tree() {
        return Result.success(menuService.getTree());
    }

    /**
     * 查询系统定义的有效权限码集合。
     *
     * @return 有效权限码集合
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system:permission:list')")
    public Result<Set<String>> permissions() {
        return Result.success(menuService.getPermissionCodes());
    }

    /**
     * 查询包含停用节点和内置标识的菜单管理树。
     *
     * @return 完整菜单管理树
     */
    @GetMapping("/management-tree")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public Result<List<MenuTreeNodeVO>> managementTree() {
        return Result.success(menuService.getManagementTree());
    }

    /**
     * 查询指定菜单或按钮权限详情。
     *
     * @param id 菜单 ID
     * @return 菜单详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:detail')")
    public Result<MenuVO> get(@PathVariable long id) {
        return Result.success(menuService.get(id));
    }

    /**
     * 创建目录、菜单或按钮权限资源。
     *
     * @param command 菜单创建参数
     * @return 新菜单字符串 ID
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:create')")
    public Result<String> create(@Valid @RequestBody CreateMenuDTO command) {
        return Result.success(menuService.create(command));
    }

    /**
     * 更新指定菜单或按钮权限配置。
     *
     * @param id 菜单 ID
     * @param command 菜单更新参数
     * @return 无数据的成功响应
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody UpdateMenuDTO command) {
        menuService.update(id, command);
        return Result.success();
    }

    /**
     * 启用指定菜单或按钮权限。
     *
     * @param id 菜单 ID
     * @return 无数据的成功响应
     */
    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('system:menu:enable')")
    public Result<Void> enable(@PathVariable long id) {
        menuService.enable(id);
        return Result.success();
    }

    /**
     * 停用指定非内置菜单或按钮权限。
     *
     * @param id 菜单 ID
     * @return 无数据的成功响应
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('system:menu:disable')")
    public Result<Void> disable(@PathVariable long id) {
        menuService.disable(id);
        return Result.success();
    }

    /**
     * 删除无子节点和角色关联的非内置菜单。
     *
     * @param id 菜单 ID
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    public Result<Void> delete(@PathVariable long id) {
        menuService.delete(id);
        return Result.success();
    }
}
