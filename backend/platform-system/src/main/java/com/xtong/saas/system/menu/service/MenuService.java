package com.xtong.saas.system.menu.service;

import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;

import java.util.List;
import java.util.Set;

/** 提供全局有效菜单树及固定按钮权限码的只读领域能力。 */
public interface MenuService {

    List<MenuTreeNodeVO> getTree();

    Set<String> getPermissionCodes();
}
