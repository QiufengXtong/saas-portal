package com.xtong.saas.system.bootstrap.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 获取首租户初始化的数据库级全局行锁，跨进程串行化空库检查与创建。 */
@Mapper
public interface SystemBootstrapLockMapper {

    @Select("SELECT id FROM sys_bootstrap_lock WHERE id = 1 FOR UPDATE")
    Long lockInitialization();
}
