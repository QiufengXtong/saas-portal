package com.xtong.saas.system.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.menu.entity.SystemMenu;
import org.apache.ibatis.annotations.Mapper;

/** 提供全局菜单和权限资源的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemMenuMapper extends BaseMapper<SystemMenu> {
}
