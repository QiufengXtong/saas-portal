package com.xtong.saas.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证分页响应从 MyBatis-Plus 分页对象映射为 API 契约的行为。
 */
class PageResultTest {

    @Test
    void shouldMapPageRecords() {
        Page<String> page = new Page<>(2, 20, 41);
        page.setRecords(List.of("1", "2"));

        PageResult<Integer> result = PageResult.from(page, Integer::valueOf);

        assertEquals(new PageResult<>(List.of(1, 2), 41, 2, 20), result);
    }
}
