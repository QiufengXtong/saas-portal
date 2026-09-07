package com.xtong.saas.system.menu.controller;

import com.xtong.saas.common.result.Result;
import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;
import com.xtong.saas.system.menu.service.MenuService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** 暴露由数据库迁移维护的全局菜单树和固定权限码目录只读 HTTP API。 */
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
}
