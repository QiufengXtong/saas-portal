package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
