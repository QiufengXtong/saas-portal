package com.xtong.saas.system.menu.service;

import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;

import java.util.List;
import java.util.Set;

/** 提供全局有效菜单树及固定按钮权限码的只读领域能力。 */
public interface MenuService {

    /** 加载由全部启用菜单组成的树形结构。 */
    List<MenuTreeNodeVO> getTree();

    /** 加载全部启用按钮资源的权限码集合。 */
    Set<String> getPermissionCodes();
}
