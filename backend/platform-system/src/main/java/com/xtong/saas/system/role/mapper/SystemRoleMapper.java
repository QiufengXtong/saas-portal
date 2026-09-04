package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemRole;
import org.apache.ibatis.annotations.Mapper;

/** 提供租户角色的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemRoleMapper extends BaseMapper<SystemRole> {
}
