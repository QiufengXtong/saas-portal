package com.xtong.saas.system.menu.vo;

import com.xtong.saas.system.menu.entity.SystemMenu;
import com.xtong.saas.system.menu.enums.MenuStatus;
import com.xtong.saas.system.menu.enums.MenuType;

import java.time.LocalDateTime;

/** 表示可安全返回客户端的菜单及按钮权限详情，持久化 ID 使用字符串。 */
public record MenuVO(
        String id,
        String parentId,
        String name,
        MenuType type,
        String routePath,
        String component,
        String icon,
        String permissionCode,
        Integer sortOrder,
        boolean visible,
        MenuStatus status,
        boolean builtIn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 将菜单实体转换为不暴露持久化内部字段的接口视图。 */
    public static MenuVO from(SystemMenu menu) {
        return new MenuVO(
                menu.getId().toString(),
                menu.getParentId() == null ? null : menu.getParentId().toString(),
                menu.getName(),
                menu.getType(),
                menu.getRoutePath(),
                menu.getComponent(),
                menu.getIcon(),
                menu.getPermissionCode(),
                menu.getSortOrder(),
                Boolean.TRUE.equals(menu.getVisible()),
                menu.getStatus(),
                Boolean.TRUE.equals(menu.getBuiltIn()),
                menu.getCreatedAt(),
                menu.getUpdatedAt());
    }
}
