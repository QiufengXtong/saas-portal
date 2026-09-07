package com.xtong.saas.system.bootstrap.mapper;

import org.apache.ibatis.annotations.Mapper;

/** 获取首租户初始化的数据库级全局行锁，跨进程串行化空库检查与创建。 */
@Mapper
public interface SystemBootstrapLockMapper {

    /** 锁定固定初始化行，在当前事务内串行化首租户创建。 */
    Long lockInitialization();
}
