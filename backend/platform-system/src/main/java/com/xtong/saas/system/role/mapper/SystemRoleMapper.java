package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

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
}
