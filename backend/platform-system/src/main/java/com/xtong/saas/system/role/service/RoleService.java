package com.xtong.saas.system.role.service;

import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.system.role.dto.CreateRoleDTO;
import com.xtong.saas.system.role.dto.RoleQueryDTO;
import com.xtong.saas.system.role.dto.UpdateRoleDTO;
import com.xtong.saas.system.role.vo.RoleVO;

import java.util.Set;
import java.util.List;

/** 定义当前受信租户内的角色管理和全局菜单授权业务边界。 */
public interface RoleService {

    /** 分页查询当前租户内符合条件的角色。 */
    PageResult<RoleVO> page(RoleQueryDTO query);

    /** 获取当前租户内指定角色的详情。 */
    RoleVO get(long roleId);

    /** 获取当前租户内指定角色已授权的菜单 ID。 */
    List<String> getMenuIds(long roleId);

    /** 在当前租户内创建角色并返回角色 ID。 */
    String create(CreateRoleDTO command);

    /** 更新当前租户内指定角色的基础资料。 */
    void update(long roleId, UpdateRoleDTO command);

    /** 启用当前租户内指定角色。 */
    void enable(long roleId);

    /** 禁用当前租户内指定角色。 */
    void disable(long roleId);

    /** 删除当前租户内指定角色。 */
    void delete(long roleId);

    /** 替换指定角色的全部菜单和权限资源授权。 */
    void assignMenus(long roleId, Set<Long> menuIds);
}
