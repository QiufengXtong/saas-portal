package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Set;

/** 提供租户角色数据访问，管理员资格与审计删除的复杂 SQL 由 XML 实现。 */
@Mapper
public interface SystemRoleMapper extends BaseMapper<SystemRole> {

    /** 包含逻辑删除记录检查指定租户内的角色编码占用数量。 */
    long countByTenantAndCodeIncludingDeleted(
            @Param("tenantId") long tenantId, @Param("roleCode") String roleCode);

    /** 判断用户是否关联本租户启用且未删除的内置管理员角色，不检查用户状态。 */
    boolean existsTenantAdminRole(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 统计非空 ID 集合中属于本租户且启用、未删除的角色数量。 */
    long countByTenantAndIds(@Param("tenantId") long tenantId, @Param("roleIds") Set<Long> roleIds);

    /** 判断非空 ID 集合是否包含本租户启用且未删除的内置管理员角色。 */
    boolean containsTenantAdminRole(@Param("tenantId") long tenantId, @Param("roleIds") Set<Long> roleIds);

    /** 统计本租户启用且未删除、具备有效内置管理员角色的去重用户数量。 */
    long countEnabledTenantAdminUsers(@Param("tenantId") long tenantId);

    /** 逻辑删除租户内未删除角色，同时保存真实审计人和更新时间。 */
    int logicalDeleteWithAudit(
            @Param("tenantId") long tenantId,
            @Param("roleId") long roleId,
            @Param("auditorId") long auditorId,
            @Param("updatedAt") LocalDateTime updatedAt);
}
