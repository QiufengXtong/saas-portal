package com.xtong.saas.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 将分页查询结果映射为独立于持久化实现的统一 API 分页响应。
 *
 * @param records 当前页记录
 * @param total 符合条件的总记录数
 * @param pageNum 当前页码
 * @param pageSize 每页记录数
 * @param <T> API 记录类型
 */
public record PageResult<T>(List<T> records, long total, long pageNum, long pageSize) {

    /**
     * 映射 MyBatis-Plus 分页记录，同时保留分页元数据。
     *
     * @param page MyBatis-Plus 分页查询结果
     * @param mapper 持久化记录到 API 记录的映射函数
     * @param <S> 原始记录类型
     * @param <T> 映射后的记录类型
     * @return 统一分页响应
     */
    public static <S, T> PageResult<T> from(IPage<S> page, Function<S, T> mapper) {
        return new PageResult<>(
                page.getRecords().stream().map(mapper).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize());
    }
}
