package com.xtong.saas.system.menu.dto;

import com.xtong.saas.system.menu.enums.MenuStatus;
import com.xtong.saas.system.menu.enums.MenuType;
import com.xtong.saas.system.menu.enums.PermissionScope;

import java.util.List;

/** 作为只读菜单树接口的节点视图，使用字符串 ID 避免前端数值精度丢失。 */
public record MenuTreeNodeVO(
        String id,
        String parentId,
        String name,
        MenuType type,
        String routePath,
        String component,
        String icon,
        String permissionCode,
        PermissionScope permissionScope,
        Integer sortOrder,
        Boolean visible,
        MenuStatus status,
        boolean builtIn,
        List<MenuTreeNodeVO> children) {
}
