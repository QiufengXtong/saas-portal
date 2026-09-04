package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemUserRole;
import org.apache.ibatis.annotations.Mapper;

/** 提供用户角色物理关联的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemUserRoleMapper extends BaseMapper<SystemUserRole> {
}
