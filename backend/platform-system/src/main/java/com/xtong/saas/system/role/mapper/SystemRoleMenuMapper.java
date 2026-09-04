package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 提供角色菜单物理关联的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemRoleMenuMapper extends BaseMapper<SystemRoleMenu> {

    @Select("""
            <script>
            SELECT DISTINCT m.permission_code
            FROM sys_role_menu rm
            INNER JOIN sys_role r ON r.id = rm.role_id
                AND r.tenant_id = rm.tenant_id
            INNER JOIN sys_menu m ON m.id = rm.menu_id
            WHERE rm.tenant_id = #{tenantId}
              AND r.tenant_id = #{tenantId}
              AND r.status = 'ENABLED'
              AND r.deleted = 0
              AND m.status = 'ENABLED'
              AND m.deleted = 0
              AND m.type = 'BUTTON'
              AND m.permission_code IS NOT NULL
              AND rm.role_id IN
              <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
              </foreach>
            ORDER BY m.permission_code
            </script>
            """)
    List<String> selectEnabledPermissionCodesByRoleIds(
            @Param("tenantId") long tenantId, @Param("roleIds") List<Long> roleIds);
}
