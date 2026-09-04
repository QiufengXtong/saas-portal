package com.xtong.saas.system.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtong.saas.system.tenant.entity.SystemTenant;
import org.apache.ibatis.annotations.Mapper;

/** 提供全局租户目录的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemTenantMapper extends BaseMapper<SystemTenant> {
}
