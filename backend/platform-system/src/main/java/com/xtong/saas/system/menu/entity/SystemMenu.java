package com.xtong.saas.system.menu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xtong.saas.common.model.BaseEntity;
import com.xtong.saas.system.menu.enums.MenuStatus;
import com.xtong.saas.system.menu.enums.MenuType;
import com.xtong.saas.system.menu.enums.PermissionScope;
import lombok.Getter;
import lombok.Setter;

/** 表示全平台共享的菜单树节点和权限资源持久化模型。 */
@TableName("sys_menu")
@Getter
@Setter
public class SystemMenu extends BaseEntity {

    private Long parentId;
    private String name;
    private MenuType type;
    private String routePath;
    private String component;
    private String icon;
    private String permissionCode;
    private Integer sortOrder;
    private Boolean visible;
    private MenuStatus status;
    private Boolean builtIn;
    private PermissionScope permissionScope;
}
