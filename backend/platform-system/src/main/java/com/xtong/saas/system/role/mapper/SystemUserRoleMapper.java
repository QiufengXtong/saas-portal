package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 提供用户角色物理关联的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemUserRoleMapper extends BaseMapper<SystemUserRole> {

    /** 按角色 ID 升序查询本租户指定用户的角色关联。 */
    List<Long> selectRoleIdsByUserId(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 物理删除本租户指定用户的全部角色关联。 */
    int deleteByUser(@Param("tenantId") long tenantId, @Param("userId") long userId);

    /** 统计本租户指定角色的用户关联数量。 */
    long countByRole(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    /** 按用户 ID 升序查询本租户拥有指定角色的去重用户。 */
    List<Long> selectUserIdsByRole(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    /** 批量保存已构造主键、租户及创建审计值的非空用户角色实体列表。 */
    int insertBatch(@Param("relations") List<SystemUserRole> relations);
}
