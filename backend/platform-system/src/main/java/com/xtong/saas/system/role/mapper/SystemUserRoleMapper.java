package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.InsertProvider;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import java.util.List;

/** 提供用户角色物理关联的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemUserRoleMapper extends BaseMapper<SystemUserRole> {

    @Select("""
            SELECT role_id
            FROM sys_user_role
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
            ORDER BY role_id
            """)
    List<Long> selectRoleIdsByUserId(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Delete("""
            DELETE FROM sys_user_role
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
            """)
    int deleteByUser(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            SELECT COUNT(*)
            FROM sys_user_role
            WHERE tenant_id = #{tenantId}
              AND role_id = #{roleId}
            """)
    long countByRole(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @Select("""
            SELECT DISTINCT user_id
            FROM sys_user_role
            WHERE tenant_id = #{tenantId}
              AND role_id = #{roleId}
            ORDER BY user_id
            """)
    List<Long> selectUserIdsByRole(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    @InsertProvider(type = UserRoleSqlProvider.class, method = "insertBatch")
    int insertBatch(
            @Param("tenantId") long tenantId,
            @Param("userId") long userId,
            @Param("roleIds") Set<Long> roleIds,
            @Param("auditorId") long auditorId,
            @Param("createdAt") LocalDateTime createdAt);

    /** 构造显式携带创建审计人的用户角色批量插入语句。 */
    final class UserRoleSqlProvider {
        private UserRoleSqlProvider() {
        }

        @SuppressWarnings("unchecked")
        public static String insertBatch(Map<String, Object> parameters) {
            Set<Long> roleIds = (Set<Long>) parameters.get("roleIds");
            StringJoiner values = new StringJoiner(",");
            for (Long roleId : roleIds) {
                values.add("(" + IdWorker.getId()
                        + ", #{tenantId}, #{userId}, " + roleId + ", #{auditorId}, #{createdAt})");
            }
            return "INSERT INTO sys_user_role "
                    + "(id, tenant_id, user_id, role_id, created_by, created_at) VALUES " + values;
        }
    }
}
