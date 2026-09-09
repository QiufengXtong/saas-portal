package com.xtong.saas.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.user.entity.SystemUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 提供租户用户数据访问，复杂查询与认证版本写入由 XML 实现。 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {

    /** 判断指定租户用户是否为有效平台管理员。 */
    boolean existsPlatformAdmin(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 包含逻辑删除记录检查指定租户内的用户名占用数量。 */
    long countByTenantAndUsernameIncludingDeleted(
            @Param("tenantId") long tenantId, @Param("username") String username);

    /** 原子递增指定租户内未删除用户的认证版本，使既有会话失效。 */
    int incrementAuthVersion(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 原子递增指定租户内一组未删除用户的认证版本，调用方保证集合非空。 */
    int incrementAuthVersions(@Param("tenantId") long tenantId, @Param("userIds") List<Long> userIds);

    /** 逻辑删除租户内未删除用户，同时写入真实审计人、时间并原子递增认证版本。 */
    int logicalDeleteWithAudit(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("auditorId") long auditorId,
            @Param("updatedAt") LocalDateTime updatedAt);
}
