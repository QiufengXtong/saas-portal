package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xtong.saas.system.role.entity.SystemRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/** 提供角色菜单物理关联的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemRoleMenuMapper extends BaseMapper<SystemRoleMenu> {

    @Delete("""
            DELETE FROM sys_role_menu
            WHERE tenant_id = #{tenantId}
              AND role_id = #{roleId}
            """)
    int deleteByRole(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @InsertProvider(type = RoleMenuSqlProvider.class, method = "insertBatch")
    int insertBatch(
            @Param("tenantId") long tenantId, @Param("roleId") long roleId, @Param("menuIds") Set<Long> menuIds);

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

    /** 构造安全的批量关联插入 SQL，并为每条物理关联分配独立雪花主键。 */
    final class RoleMenuSqlProvider {

        private RoleMenuSqlProvider() {
        }

        @SuppressWarnings("unchecked")
        public static String insertBatch(Map<String, Object> parameters) {
            Set<Long> menuIds = (Set<Long>) parameters.get("menuIds");
            StringJoiner values = new StringJoiner(",");
            for (Long menuId : menuIds) {
                values.add("(" + IdWorker.getId() + ", #{tenantId}, #{roleId}, " + menuId + ")");
            }
            return "INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id) VALUES " + values;
        }
    }
}
