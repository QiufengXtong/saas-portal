package com.xtong.saas.system.role.service;

import com.xtong.saas.common.result.PageResult;
import com.xtong.saas.system.role.dto.CreateRoleDTO;
import com.xtong.saas.system.role.dto.RoleQueryDTO;
import com.xtong.saas.system.role.dto.UpdateRoleDTO;
import com.xtong.saas.system.role.vo.RoleVO;

import java.util.Set;

/** 定义当前受信租户内的角色管理和全局菜单授权业务边界。 */
public interface RoleService {

    PageResult<RoleVO> page(RoleQueryDTO query);

    RoleVO get(long roleId);

    String create(CreateRoleDTO command);

    void update(long roleId, UpdateRoleDTO command);

    void enable(long roleId);

    void disable(long roleId);

    void delete(long roleId);

    void assignMenus(long roleId, Set<Long> menuIds);
}
