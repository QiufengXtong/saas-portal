package com.xtong.saas.system.menu.service;

import com.xtong.saas.system.menu.dto.CreateMenuDTO;
import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;
import com.xtong.saas.system.menu.dto.UpdateMenuDTO;
import com.xtong.saas.system.menu.vo.MenuVO;

import java.util.List;
import java.util.Set;

/** 提供全局菜单树、按钮权限目录及菜单资源安全维护能力。 */
public interface MenuService {

    /** 加载由全部启用、租户可分配菜单组成的树形结构。 */
    List<MenuTreeNodeVO> getTree();

    /** 加载全部启用按钮资源的权限码集合。 */
    Set<String> getPermissionCodes();

    /** 加载只能由平台管理员持有的启用按钮权限码集合。 */
    Set<String> getPlatformPermissionCodes();

    /** 加载包含停用节点和内置标识的管理菜单树。 */
    List<MenuTreeNodeVO> getManagementTree();

    /** 获取指定菜单或权限资源详情。 */
    MenuVO get(long menuId);

    /** 创建非内置菜单或按钮权限并返回字符串 ID。 */
    String create(CreateMenuDTO command);

    /** 更新指定菜单或按钮权限的安全配置。 */
    void update(long menuId, UpdateMenuDTO command);

    /** 启用指定菜单或按钮权限。 */
    void enable(long menuId);

    /** 停用非内置且无启用子节点的菜单或按钮权限。 */
    void disable(long menuId);

    /** 删除无子节点、无角色关联且非内置的菜单或按钮权限。 */
    void delete(long menuId);
}
