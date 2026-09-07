package com.xtong.saas.system.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 提供全局租户目录的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemTenantMapper extends BaseMapper<SystemTenant> {

    /** 锁定指定未删除租户，在当前事务内串行化管理员不变量检查。 */
    Long lockByIdForAdminInvariant(@Param("tenantId") long tenantId);

    /** 按 ID 与标准化编码锁定未删除租户，用于登录认证。 */
    SystemTenant lockByIdAndCodeForAuthentication(
            @Param("tenantId") long tenantId, @Param("tenantCode") String tenantCode);

    /** 按 ID 锁定未删除租户，用于刷新认证。 */
    SystemTenant lockByIdForAuthentication(@Param("tenantId") long tenantId);
}
