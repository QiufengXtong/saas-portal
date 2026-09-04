package com.xtong.saas.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.role.entity.SystemRoleMenu;
import org.apache.ibatis.annotations.Mapper;

/** 提供角色菜单物理关联的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemRoleMenuMapper extends BaseMapper<SystemRoleMenu> {
}
