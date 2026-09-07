package com.xtong.saas.system.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.menu.entity.SystemMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Set;

/** 提供全局菜单和权限资源的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemMenuMapper extends BaseMapper<SystemMenu> {

    /** 统计非空 ID 集合中启用且未删除的全局菜单及权限资源。 */
    long countEnabledByIds(@Param("menuIds") Set<Long> menuIds);

    /** 包含逻辑删除记录检查权限码是否被其他菜单占用。 */
    long countByPermissionCodeIncludingDeleted(
            @Param("permissionCode") String permissionCode, @Param("excludedMenuId") Long excludedMenuId);

    /** 逻辑删除未删除菜单，并保存真实审计用户和更新时间。 */
    int logicalDeleteWithAudit(
            @Param("menuId") long menuId,
            @Param("auditorId") long auditorId,
            @Param("updatedAt") LocalDateTime updatedAt);
}
