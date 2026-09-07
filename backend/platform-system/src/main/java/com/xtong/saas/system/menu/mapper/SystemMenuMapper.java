package com.xtong.saas.system.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.menu.entity.SystemMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Set;

/** 提供全局菜单和权限资源的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemMenuMapper extends BaseMapper<SystemMenu> {

    /** 统计非空 ID 集合中启用且未删除的全局菜单及权限资源。 */
    long countEnabledByIds(@Param("menuIds") Set<Long> menuIds);
}
