package com.xtong.saas.system.menu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xtong.saas.common.exception.BusinessException;
import com.xtong.saas.common.mybatis.AuditorProvider;
import com.xtong.saas.system.auth.api.SessionRevocationService;
import com.xtong.saas.system.menu.dto.CreateMenuDTO;
import com.xtong.saas.system.menu.dto.MenuTreeNodeVO;
import com.xtong.saas.system.menu.dto.UpdateMenuDTO;
import com.xtong.saas.system.menu.entity.SystemMenu;
import com.xtong.saas.system.menu.enums.MenuStatus;
import com.xtong.saas.system.menu.enums.MenuType;
import com.xtong.saas.system.menu.exception.MenuErrorCode;
import com.xtong.saas.system.menu.mapper.SystemMenuMapper;
import com.xtong.saas.system.menu.model.MenuAffectedUser;
import com.xtong.saas.system.menu.service.MenuService;
import com.xtong.saas.system.menu.vo.MenuVO;
import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.tenant.context.TenantScope;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 维护全局菜单和权限资源，并在权限语义变化后撤销受影响用户会话。 */
@Service
public class MenuServiceImpl implements MenuService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuServiceImpl.class);
    private static final Comparator<SystemMenu> MENU_ORDER = Comparator
            .comparing(SystemMenu::getSortOrder, Comparator.nullsFirst(Integer::compareTo))
            .thenComparing(SystemMenu::getId, Comparator.nullsFirst(Long::compareTo));

    private final SystemMenuMapper menuMapper;
    private final SystemRoleMenuMapper roleMenuMapper;
    private final SystemUserMapper userMapper;
    private final SessionRevocationService sessionRevocationService;
    private final AuditorProvider auditorProvider;

    /** 创建菜单服务并注入菜单、角色关联、用户会话及审计依赖。 */
    public MenuServiceImpl(
            SystemMenuMapper menuMapper,
            SystemRoleMenuMapper roleMenuMapper,
            SystemUserMapper userMapper,
            SessionRevocationService sessionRevocationService,
            AuditorProvider auditorProvider) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userMapper = userMapper;
        this.sessionRevocationService = sessionRevocationService;
        this.auditorProvider = auditorProvider;
    }

    /** 加载启用菜单并构造成稳定排序的授权树。 */
    @Override
    public List<MenuTreeNodeVO> getTree() {
        return buildTree(loadMenus(true));
    }

    /** 加载全部未删除菜单并构造成包含管理状态的稳定树。 */
    @Override
    public List<MenuTreeNodeVO> getManagementTree() {
        return buildTree(loadMenus(false));
    }

    /** 加载全部启用按钮资源并返回稳定排序的权限码集合。 */
    @Override
    public Set<String> getPermissionCodes() {
        LinkedHashSet<String> permissionCodes = new LinkedHashSet<>();
        loadMenus(true).stream()
                .filter(menu -> menu.getType() == MenuType.BUTTON)
                .map(SystemMenu::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .sorted()
                .forEach(permissionCodes::add);
        return Collections.unmodifiableSet(permissionCodes);
    }

    /** 获取指定未删除菜单的完整管理视图。 */
    @Override
    public MenuVO get(long menuId) {
        return MenuVO.from(requireMenu(menuId));
    }

    /** 校验并创建非内置菜单，新增按钮后刷新全部租户管理员权限快照。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(CreateMenuDTO command) {
        Objects.requireNonNull(command, "command must not be null");
        MenuValues values = normalize(command.parentId(), command.name(), command.type(), command.routePath(),
                command.component(), command.icon(), command.permissionCode(), command.sortOrder(), command.visible());
        validateValues(null, MenuStatus.ENABLED, values);
        assertPermissionCodeAvailable(values.permissionCode(), null);

        SystemMenu menu = new SystemMenu();
        applyValues(menu, values);
        menu.setStatus(MenuStatus.ENABLED);
        menu.setBuiltIn(false);
        try {
            menuMapper.insert(menu);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(MenuErrorCode.PERMISSION_CODE_EXISTS);
        }
        if (values.type() == MenuType.BUTTON) {
            invalidateUsers(roleMenuMapper.selectTenantAdminUsers());
        }
        return menu.getId().toString();
    }

    /** 更新菜单配置，并在按钮权限语义变化后刷新受影响用户权限快照。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(long menuId, UpdateMenuDTO command) {
        Objects.requireNonNull(command, "command must not be null");
        SystemMenu menu = requireMenu(menuId);
        MenuValues values = normalize(command.parentId(), command.name(), command.type(), command.routePath(),
                command.component(), command.icon(), command.permissionCode(), command.sortOrder(), command.visible());
        assertBuiltInUpdateSafe(menu, values);
        validateValues(menuId, menu.getStatus(), values);
        validateChildren(menuId, values.type());
        assertPermissionCodeAvailable(values.permissionCode(), menuId);

        boolean permissionChanged = affectsPermissionSnapshot(menu, values);
        List<MenuAffectedUser> affectedUsers = permissionChanged
                ? roleMenuMapper.selectUsersAffectedByMenu(menuId) : List.of();
        applyValues(menu, values);
        try {
            menuMapper.updateById(menu);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(MenuErrorCode.PERMISSION_CODE_EXISTS);
        }
        invalidateUsers(affectedUsers);
    }

    /** 启用菜单，按钮重新生效时刷新其关联用户和租户管理员权限。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(long menuId) {
        SystemMenu menu = requireMenu(menuId);
        if (menu.getStatus() == MenuStatus.ENABLED) {
            return;
        }
        validateValues(menuId, MenuStatus.ENABLED, valuesOf(menu));
        List<MenuAffectedUser> affectedUsers = menu.getType() == MenuType.BUTTON
                ? roleMenuMapper.selectUsersAffectedByMenu(menuId) : List.of();
        menu.setStatus(MenuStatus.ENABLED);
        menuMapper.updateById(menu);
        invalidateUsers(affectedUsers);
    }

    /** 停用非内置叶子菜单，按钮失效时刷新其关联用户和租户管理员权限。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(long menuId) {
        SystemMenu menu = requireMenu(menuId);
        assertNotBuiltIn(menu);
        if (menu.getStatus() == MenuStatus.DISABLED) {
            return;
        }
        if (countChildren(menuId, true) > 0) {
            throw new BusinessException(MenuErrorCode.MENU_HAS_CHILDREN);
        }
        List<MenuAffectedUser> affectedUsers = menu.getType() == MenuType.BUTTON
                ? roleMenuMapper.selectUsersAffectedByMenu(menuId) : List.of();
        menu.setStatus(MenuStatus.DISABLED);
        menuMapper.updateById(menu);
        invalidateUsers(affectedUsers);
    }

    /** 删除无子节点和角色关联的非内置菜单，并刷新管理员权限快照。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long menuId) {
        SystemMenu menu = requireMenu(menuId);
        assertNotBuiltIn(menu);
        if (countChildren(menuId, false) > 0) {
            throw new BusinessException(MenuErrorCode.MENU_HAS_CHILDREN);
        }
        if (roleMenuMapper.countByMenuId(menuId) > 0) {
            throw new BusinessException(MenuErrorCode.MENU_ASSIGNED_TO_ROLE);
        }
        List<MenuAffectedUser> affectedUsers = menu.getType() == MenuType.BUTTON
                ? roleMenuMapper.selectTenantAdminUsers() : List.of();
        int deleted = menuMapper.logicalDeleteWithAudit(menuId, currentAuditorId(), LocalDateTime.now());
        if (deleted != 1) {
            throw new BusinessException(MenuErrorCode.MENU_NOT_FOUND);
        }
        invalidateUsers(affectedUsers);
    }

    /** 按排序号和 ID 加载全部未删除菜单，并按需限定启用状态。 */
    private List<SystemMenu> loadMenus(boolean enabledOnly) {
        List<SystemMenu> menus = menuMapper.selectList(Wrappers.<SystemMenu>query().lambda()
                .eq(SystemMenu::getDeleted, false)
                .eq(enabledOnly, SystemMenu::getStatus, MenuStatus.ENABLED)
                .orderByAsc(SystemMenu::getSortOrder)
                .orderByAsc(SystemMenu::getId));
        return menus.stream()
                .filter(menu -> !Boolean.TRUE.equals(menu.getDeleted()))
                .filter(menu -> !enabledOnly || menu.getStatus() == MenuStatus.ENABLED)
                .sorted(MENU_ORDER)
                .toList();
    }

    /** 将扁平菜单按父子关系组装为树，孤立节点不会进入结果。 */
    private List<MenuTreeNodeVO> buildTree(List<SystemMenu> menus) {
        Map<Long, MutableMenuNode> nodesById = new LinkedHashMap<>();
        menus.forEach(menu -> nodesById.put(menu.getId(), new MutableMenuNode(menu)));
        List<MutableMenuNode> roots = new ArrayList<>();
        for (MutableMenuNode node : nodesById.values()) {
            Long parentId = node.menu().getParentId();
            if (parentId == null) {
                roots.add(node);
            } else if (nodesById.containsKey(parentId)) {
                nodesById.get(parentId).children().add(node);
            } else {
                LOGGER.warn("Security alert: skip orphan menu node id={} because parent id={} is unavailable",
                        node.menu().getId(), parentId);
            }
        }
        return roots.stream().map(this::toView).toList();
    }

    /** 递归将内部可变菜单节点转换为只读树节点。 */
    private MenuTreeNodeVO toView(MutableMenuNode node) {
        SystemMenu menu = node.menu();
        return new MenuTreeNodeVO(
                menu.getId().toString(),
                menu.getParentId() == null ? null : menu.getParentId().toString(),
                menu.getName(), menu.getType(), menu.getRoutePath(), menu.getComponent(), menu.getIcon(),
                menu.getPermissionCode(), menu.getSortOrder(), menu.getVisible(), menu.getStatus(),
                Boolean.TRUE.equals(menu.getBuiltIn()), node.children().stream().map(this::toView).toList());
    }

    /** 加载指定未删除菜单，不存在时返回稳定业务错误。 */
    private SystemMenu requireMenu(long menuId) {
        SystemMenu menu = menuMapper.selectOne(Wrappers.<SystemMenu>query().lambda()
                .eq(SystemMenu::getId, menuId)
                .eq(SystemMenu::getDeleted, false));
        if (menu == null) {
            throw new BusinessException(MenuErrorCode.MENU_NOT_FOUND);
        }
        return menu;
    }

    /** 规范化文本字段，避免空白字符串绕过类型字段约束。 */
    private MenuValues normalize(Long parentId, String name, MenuType type, String routePath,
            String component, String icon, String permissionCode, Integer sortOrder, Boolean visible) {
        return new MenuValues(parentId, strip(name), type, stripToNull(routePath), stripToNull(component),
                stripToNull(icon), stripToNull(permissionCode), sortOrder, visible);
    }

    /** 校验节点字段、父子类型、父节点状态及循环层级。 */
    private void validateValues(Long menuId, MenuStatus status, MenuValues values) {
        if (values.name() == null || values.type() == null || values.sortOrder() == null || values.visible() == null) {
            throw new BusinessException(MenuErrorCode.INVALID_MENU_FIELDS);
        }
        validateTypeFields(values);
        if (values.parentId() == null) {
            if (values.type() != MenuType.DIRECTORY) {
                throw new BusinessException(MenuErrorCode.INVALID_PARENT);
            }
            return;
        }
        SystemMenu parent = requireMenu(values.parentId());
        if (!canContain(parent.getType(), values.type())
                || status == MenuStatus.ENABLED && parent.getStatus() != MenuStatus.ENABLED) {
            throw new BusinessException(MenuErrorCode.INVALID_PARENT);
        }
        if (menuId != null && createsCycle(menuId, parent)) {
            throw new BusinessException(MenuErrorCode.MENU_CYCLE);
        }
    }

    /** 校验目录、菜单和按钮各自允许及必需的配置字段。 */
    private void validateTypeFields(MenuValues values) {
        boolean valid = switch (values.type()) {
            case DIRECTORY -> values.component() == null && values.permissionCode() == null;
            case MENU -> values.parentId() != null && values.routePath() != null
                    && values.component() != null && values.permissionCode() == null;
            case BUTTON -> values.parentId() != null && values.routePath() == null
                    && values.component() == null && values.icon() == null
                    && values.permissionCode() != null && !values.visible();
        };
        if (!valid) {
            throw new BusinessException(MenuErrorCode.INVALID_MENU_FIELDS);
        }
    }

    /** 校验更新后的节点类型仍能合法承载全部直接子节点。 */
    private void validateChildren(long menuId, MenuType parentType) {
        List<SystemMenu> children = menuMapper.selectList(Wrappers.<SystemMenu>query().lambda()
                .eq(SystemMenu::getParentId, menuId)
                .eq(SystemMenu::getDeleted, false));
        if (children.stream().anyMatch(child -> !canContain(parentType, child.getType()))) {
            throw new BusinessException(MenuErrorCode.INVALID_MENU_FIELDS);
        }
    }

    /** 判断父节点类型是否允许承载指定子节点类型。 */
    private boolean canContain(MenuType parentType, MenuType childType) {
        return parentType == MenuType.DIRECTORY
                ? childType == MenuType.DIRECTORY || childType == MenuType.MENU
                : parentType == MenuType.MENU && childType == MenuType.BUTTON;
    }

    /** 沿父链判断更新后的父节点是否会回到当前节点。 */
    private boolean createsCycle(long menuId, SystemMenu parent) {
        Set<Long> visited = new LinkedHashSet<>();
        SystemMenu current = parent;
        while (current != null && visited.add(current.getId())) {
            if (current.getId() == menuId) {
                return true;
            }
            current = current.getParentId() == null ? null : requireMenu(current.getParentId());
        }
        return current != null;
    }

    /** 阻止内置菜单改变层级、类型、路由组件或权限码等核心配置。 */
    private void assertBuiltInUpdateSafe(SystemMenu menu, MenuValues values) {
        if (!Boolean.TRUE.equals(menu.getBuiltIn())) {
            return;
        }
        if (!Objects.equals(menu.getParentId(), values.parentId())
                || menu.getType() != values.type()
                || !Objects.equals(menu.getRoutePath(), values.routePath())
                || !Objects.equals(menu.getComponent(), values.component())
                || !Objects.equals(menu.getPermissionCode(), values.permissionCode())) {
            throw new BusinessException(MenuErrorCode.BUILT_IN_MENU_PROTECTED);
        }
    }

    /** 阻止停用或删除内置核心菜单和权限资源。 */
    private void assertNotBuiltIn(SystemMenu menu) {
        if (Boolean.TRUE.equals(menu.getBuiltIn())) {
            throw new BusinessException(MenuErrorCode.BUILT_IN_MENU_PROTECTED);
        }
    }

    /** 包含逻辑删除记录校验权限码数据库唯一性。 */
    private void assertPermissionCodeAvailable(String permissionCode, Long excludedMenuId) {
        if (permissionCode != null
                && menuMapper.countByPermissionCodeIncludingDeleted(permissionCode, excludedMenuId) > 0) {
            throw new BusinessException(MenuErrorCode.PERMISSION_CODE_EXISTS);
        }
    }

    /** 统计指定菜单的直接子节点数量，可选择仅统计启用节点。 */
    private long countChildren(long menuId, boolean enabledOnly) {
        return menuMapper.selectCount(Wrappers.<SystemMenu>query().lambda()
                .eq(SystemMenu::getParentId, menuId)
                .eq(SystemMenu::getDeleted, false)
                .eq(enabledOnly, SystemMenu::getStatus, MenuStatus.ENABLED));
    }

    /** 判断更新是否改变用户会话中缓存的按钮权限集合。 */
    private boolean affectsPermissionSnapshot(SystemMenu menu, MenuValues values) {
        return (menu.getType() == MenuType.BUTTON || values.type() == MenuType.BUTTON)
                && (menu.getType() != values.type()
                || !Objects.equals(menu.getPermissionCode(), values.permissionCode()));
    }

    /** 将已校验字段完整应用到菜单实体。 */
    private void applyValues(SystemMenu menu, MenuValues values) {
        menu.setParentId(values.parentId());
        menu.setName(values.name());
        menu.setType(values.type());
        menu.setRoutePath(values.routePath());
        menu.setComponent(values.component());
        menu.setIcon(values.icon());
        menu.setPermissionCode(values.permissionCode());
        menu.setSortOrder(values.sortOrder());
        menu.setVisible(values.visible());
    }

    /** 从持久化实体提取标准化校验值。 */
    private MenuValues valuesOf(SystemMenu menu) {
        return normalize(menu.getParentId(), menu.getName(), menu.getType(), menu.getRoutePath(),
                menu.getComponent(), menu.getIcon(), menu.getPermissionCode(), menu.getSortOrder(), menu.getVisible());
    }

    /** 在当前事务内递增受影响用户认证版本，并在提交后撤销其 Redis 会话。 */
    private void invalidateUsers(List<MenuAffectedUser> affectedUsers) {
        List<MenuAffectedUser> distinctUsers = affectedUsers.stream().distinct().toList();
        Map<Long, List<Long>> usersByTenant = distinctUsers.stream().collect(Collectors.groupingBy(
                MenuAffectedUser::tenantId, LinkedHashMap::new,
                Collectors.mapping(MenuAffectedUser::userId, Collectors.toList())));
        usersByTenant.forEach((tenantId, userIds) -> TenantScope.run(tenantId,
                () -> userMapper.incrementAuthVersions(tenantId, userIds.stream().distinct().toList())));
        registerRevocationsAfterCommit(distinctUsers);
    }

    /** 在事务提交后执行外部会话撤销，失败时由数据库认证版本继续兜底。 */
    private void registerRevocationsAfterCommit(List<MenuAffectedUser> users) {
        if (users.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 数据库事务成功后逐用户撤销会话。 */
                @Override
                public void afterCommit() {
                    users.forEach(MenuServiceImpl.this::safelyRevoke);
                }
            });
            return;
        }
        users.forEach(this::safelyRevoke);
    }

    /** 尽力撤销单个用户会话，Redis 异常不会掩盖已提交的数据库安全版本。 */
    private void safelyRevoke(MenuAffectedUser user) {
        try {
            sessionRevocationService.revokeAllUserSessions(user.tenantId(), user.userId());
        } catch (RuntimeException exception) {
            LOGGER.warn("菜单权限变化后的会话清理失败，认证版本仍会阻止旧会话: tenantId={}, userId={}",
                    user.tenantId(), user.userId(), exception);
        }
    }

    /** 获取当前认证审计用户，匿名调用不能执行菜单写操作。 */
    private long currentAuditorId() {
        return auditorProvider.currentAuditorId()
                .orElseThrow(() -> new IllegalStateException("Menu management requires an authenticated auditor"));
    }

    /** 保留空字符串语义并移除文本首尾空白。 */
    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    /** 移除文本首尾空白，并将空文本统一转换为空值。 */
    private static String stripToNull(String value) {
        String stripped = strip(value);
        return stripped == null || stripped.isEmpty() ? null : stripped;
    }

    /** 保存建树期间的可变子节点集合，不向接口层泄露可变状态。 */
    private record MutableMenuNode(SystemMenu menu, List<MutableMenuNode> children) {
        /** 创建尚未挂载子节点的内部树节点。 */
        private MutableMenuNode(SystemMenu menu) {
            this(menu, new ArrayList<>());
        }
    }

    /** 保存已经标准化的菜单字段，确保创建与更新使用同一校验规则。 */
    private record MenuValues(
            Long parentId, String name, MenuType type, String routePath, String component,
            String icon, String permissionCode, Integer sortOrder, Boolean visible) {
    }
}
