package com.xtong.saas.system.menu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;
import com.xtong.saas.system.menu.entity.SystemMenu;
import com.xtong.saas.system.menu.enums.MenuStatus;
import com.xtong.saas.system.menu.enums.MenuType;
import com.xtong.saas.system.menu.mapper.SystemMenuMapper;
import com.xtong.saas.system.menu.service.MenuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从全局菜单资源构建稳定只读树，并只暴露有效按钮权限码。 */
@Service
public class MenuServiceImpl implements MenuService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuServiceImpl.class);
    private static final Comparator<SystemMenu> MENU_ORDER = Comparator
            .comparing(SystemMenu::getSortOrder, Comparator.nullsFirst(Integer::compareTo))
            .thenComparing(SystemMenu::getId, Comparator.nullsFirst(Long::compareTo));

    private final SystemMenuMapper menuMapper;

    /** 创建菜单服务并注入菜单数据访问依赖。 */
    public MenuServiceImpl(SystemMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    /** 加载启用菜单并构造成稳定排序的树形视图。 */
    @Override
    public List<MenuTreeNodeVO> getTree() {
        List<SystemMenu> menus = loadEnabledMenus();
        Map<Long, MutableMenuNode> nodesById = new LinkedHashMap<>();
        for (SystemMenu menu : menus) {
            nodesById.put(menu.getId(), new MutableMenuNode(menu));
        }

        List<MutableMenuNode> roots = new ArrayList<>();
        for (MutableMenuNode node : nodesById.values()) {
            Long parentId = node.menu().getParentId();
            if (parentId == null) {
                roots.add(node);
                continue;
            }
            MutableMenuNode parent = nodesById.get(parentId);
            if (parent == null) {
                LOGGER.warn("Security alert: skip orphan menu node id={} because parent id={} is unavailable", node.menu().getId(), parentId);
                continue;
            }
            parent.children().add(node);
        }
        return roots.stream().map(this::toView).toList();
    }

    /** 加载全部启用按钮资源并返回稳定排序的权限码集合。 */
    @Override
    public Set<String> getPermissionCodes() {
        LinkedHashSet<String> permissionCodes = new LinkedHashSet<>();
        loadEnabledMenus().stream()
                .filter(menu -> menu.getType() == MenuType.BUTTON)
                .map(SystemMenu::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .sorted()
                .forEach(permissionCodes::add);
        return Collections.unmodifiableSet(permissionCodes);
    }

    /** 按排序号和 ID 加载全部启用且未删除的菜单。 */
    private List<SystemMenu> loadEnabledMenus() {
        List<SystemMenu> menus = menuMapper.selectList(Wrappers.<SystemMenu>query().lambda()
                .eq(SystemMenu::getDeleted, false)
                .eq(SystemMenu::getStatus, MenuStatus.ENABLED)
                .orderByAsc(SystemMenu::getSortOrder)
                .orderByAsc(SystemMenu::getId));
        return menus.stream()
                .filter(menu -> !Boolean.TRUE.equals(menu.getDeleted()))
                .filter(menu -> menu.getStatus() == MenuStatus.ENABLED)
                .sorted(MENU_ORDER)
                .toList();
    }

    /** 递归将内部可变菜单节点转换为只读树节点。 */
    private MenuTreeNodeVO toView(MutableMenuNode node) {
        SystemMenu menu = node.menu();
        List<MenuTreeNodeVO> children = node.children().stream().map(this::toView).toList();
        return new MenuTreeNodeVO(
                String.valueOf(menu.getId()),
                menu.getParentId() == null ? null : String.valueOf(menu.getParentId()),
                menu.getName(),
                menu.getType(),
                menu.getRoutePath(),
                menu.getComponent(),
                menu.getIcon(),
                menu.getPermissionCode(),
                menu.getSortOrder(),
                menu.getVisible(),
                children);
    }

    /** 保存建树期间的可变子节点集合，不向接口层泄露可变状态。 */
    private record MutableMenuNode(SystemMenu menu, List<MutableMenuNode> children) {

        /** 创建菜单节点并初始化空子节点集合。 */
        private MutableMenuNode(SystemMenu menu) {
            this(menu, new ArrayList<>());
        }
    }
}
