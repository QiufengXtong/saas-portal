package com.xtong.saas.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.user.entity.SystemUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 提供租户系统用户的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {

    @Select("""
            SELECT COUNT(*)
            FROM sys_user
            WHERE tenant_id = #{tenantId}
              AND username = #{username}
            """)
    long countByTenantAndUsernameIncludingDeleted(
            @Param("tenantId") long tenantId, @Param("username") String username);

    @Update("""
            UPDATE sys_user
            SET auth_version = auth_version + 1
            WHERE tenant_id = #{tenantId} AND id = #{userId} AND deleted = 0
            """)
    int incrementAuthVersion(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Update("""
            <script>
            UPDATE sys_user SET auth_version = auth_version + 1
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND id IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">#{userId}</foreach>
            </script>
            """)
    int incrementAuthVersions(@Param("tenantId") long tenantId, @Param("userIds") List<Long> userIds);

    @Update("""
            UPDATE sys_user
            SET deleted = 1, auth_version = auth_version + 1,
                updated_by = #{auditorId}, updated_at = #{updatedAt}
            WHERE tenant_id = #{tenantId} AND id = #{userId} AND deleted = 0
            """)
    int logicalDeleteWithAudit(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("auditorId") long auditorId,
            @Param("updatedAt") LocalDateTime updatedAt);
}
