package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.util.Set;

/** 提供租户角色的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemRoleMapper extends BaseMapper<SystemRole> {

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM sys_user_role ur
                INNER JOIN sys_role r ON r.id = ur.role_id
                    AND r.tenant_id = ur.tenant_id
                WHERE ur.tenant_id = #{tenantId}
                  AND ur.user_id = #{userId}
                  AND r.role_code = 'TENANT_ADMIN'
                  AND r.built_in = 1
                  AND r.status = 'ENABLED'
                  AND r.deleted = 0
            )
            """)
    boolean existsTenantAdminRole(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_role
            WHERE tenant_id = #{tenantId}
              AND deleted = 0
              AND status = 'ENABLED'
              AND id IN
              <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
              </foreach>
            </script>
            """)
    long countByTenantAndIds(@Param("tenantId") long tenantId, @Param("roleIds") Set<Long> roleIds);

    @Select("""
            <script>
            SELECT EXISTS (
                SELECT 1
                FROM sys_role
                WHERE tenant_id = #{tenantId}
                  AND deleted = 0
                  AND status = 'ENABLED'
                  AND built_in = 1
                  AND role_code = 'TENANT_ADMIN'
                  AND id IN
                  <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                    #{roleId}
                  </foreach>
            )
            </script>
            """)
    boolean containsTenantAdminRole(@Param("tenantId") long tenantId, @Param("roleIds") Set<Long> roleIds);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM sys_user u
            INNER JOIN sys_user_role ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
            INNER JOIN sys_role r ON r.tenant_id = ur.tenant_id AND r.id = ur.role_id
            WHERE u.tenant_id = #{tenantId}
              AND u.deleted = 0
              AND u.status = 'ENABLED'
              AND r.deleted = 0
              AND r.status = 'ENABLED'
              AND r.built_in = 1
              AND r.role_code = 'TENANT_ADMIN'
            """)
    long countEnabledTenantAdminUsers(@Param("tenantId") long tenantId);
}
