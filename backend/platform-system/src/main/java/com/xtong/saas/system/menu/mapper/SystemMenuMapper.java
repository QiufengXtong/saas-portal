package com.xtong.saas.system.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.menu.entity.SystemMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

/** 提供全局菜单和权限资源的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemMenuMapper extends BaseMapper<SystemMenu> {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_menu
            WHERE deleted = 0
              AND status = 'ENABLED'
              AND id IN
              <foreach collection="menuIds" item="menuId" open="(" separator="," close=")">
                #{menuId}
              </foreach>
            </script>
            """)
    long countEnabledByIds(@Param("menuIds") Set<Long> menuIds);
}
