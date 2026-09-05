package com.xtong.saas.system.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 提供全局租户目录的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemTenantMapper extends BaseMapper<SystemTenant> {

    @Select("""
            SELECT id
            FROM sys_tenant
            WHERE id = #{tenantId}
              AND deleted = 0
            FOR UPDATE
            """)
    Long lockByIdForAdminInvariant(@Param("tenantId") long tenantId);

    @Select("""
            SELECT * FROM sys_tenant
            WHERE id = #{tenantId} AND tenant_code = #{tenantCode} AND deleted = 0
            FOR UPDATE
            """)
    SystemTenant lockByIdAndCodeForAuthentication(
            @Param("tenantId") long tenantId, @Param("tenantCode") String tenantCode);

    @Select("""
            SELECT * FROM sys_tenant
            WHERE id = #{tenantId} AND deleted = 0
            FOR UPDATE
            """)
    SystemTenant lockByIdForAuthentication(@Param("tenantId") long tenantId);
}
