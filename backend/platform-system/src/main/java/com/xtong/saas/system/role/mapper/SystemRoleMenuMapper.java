package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 提供角色菜单物理关联的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemRoleMenuMapper extends BaseMapper<SystemRoleMenu> {

    /** 物理删除本租户指定角色的全部菜单关联。 */
    int deleteByRole(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    /** 批量保存已构造主键、租户及创建审计值的非空角色菜单实体列表。 */
    int insertBatch(@Param("relations") List<SystemRoleMenu> relations);

    /** 按菜单 ID 升序查询本租户指定角色的菜单关联。 */
    List<Long> selectMenuIdsByRole(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    /** 按权限码升序加载非空角色列表在本租户内有效、去重的按钮权限。 */
    List<String> selectEnabledPermissionCodesByRoleIds(
            @Param("tenantId") long tenantId, @Param("roleIds") List<Long> roleIds);
}
