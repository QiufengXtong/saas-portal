package com.xtong.saas.common.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 提供跨模块复用且限制单页大小的 MyBatis-Plus 分页组件。
 */
@Configuration(proxyBeanMethods = false)
public class MyBatisCommonConfig {

    /**
     * 创建面向 MySQL 的分页拦截器。
     *
     * @return 最大分页大小为 500 的分页拦截器
     */
    @Bean
    public PaginationInnerInterceptor paginationInnerInterceptor() {
        PaginationInnerInterceptor interceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        interceptor.setMaxLimit(500L);
        return interceptor;
    }
}
