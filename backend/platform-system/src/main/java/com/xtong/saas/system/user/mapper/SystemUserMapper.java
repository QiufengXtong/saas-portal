package com.xtong.saas.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.user.entity.SystemUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
