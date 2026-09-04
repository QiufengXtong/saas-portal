package com.xtong.saas.common.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证通用 MyBatis 配置提供受限的 MySQL 分页拦截器。
 */
class MyBatisCommonConfigTest {

    @Test
    void shouldConfigureMysqlPaginationWithMaximumPageSizeOf500() {
        PaginationInnerInterceptor interceptor = new MyBatisCommonConfig().paginationInnerInterceptor();

        assertEquals(DbType.MYSQL, interceptor.getDbType());
        assertEquals(500L, interceptor.getMaxLimit());
    }
}
