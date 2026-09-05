package com.xtong.saas.common.mybatis;

import com.xtong.saas.common.model.BaseEntity;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.LocalDateTime;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 MyBatis 审计处理器向持久化实体填充新增和更新审计字段的契约。
 */
class AuditMetaObjectHandlerTest {

    @Test
    void shouldFillCreateAndUpdateAuditFieldsOnInsert() {
        AuditMetaObjectHandler handler = handlerFor(42L);
        TestEntity entity = new TestEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(42L, entity.getCreatedBy());
        assertEquals(42L, entity.getUpdatedBy());
        assertFalse(entity.getDeleted());
    }

    @Test
    void shouldFillOnlyUpdateAuditFieldsOnUpdate() {
        AuditMetaObjectHandler handler = handlerFor(42L);
        TestEntity entity = new TestEntity();
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 4, 9, 0);
        entity.setCreatedBy(7L);
        entity.setCreatedAt(createdAt);
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.updateFill(metaObject);

        assertEquals(7L, entity.getCreatedBy());
        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals(42L, entity.getUpdatedBy());
        assertNotNull(entity.getUpdatedAt());
        assertNull(entity.getDeleted());
    }

    @Test
    void shouldOverwriteStaleUpdateAuditFieldsOnUpdate() {
        AuditMetaObjectHandler handler = handlerFor(42L);
        TestEntity entity = new TestEntity();
        LocalDateTime stale = LocalDateTime.of(2020, 1, 1, 0, 0);
        entity.setUpdatedBy(7L);
        entity.setUpdatedAt(stale);

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertEquals(42L, entity.getUpdatedBy());
        org.junit.jupiter.api.Assertions.assertTrue(entity.getUpdatedAt().isAfter(stale));
    }

    @Test
    void shouldUseSystemAuditorWhenNoAuditorProviderIsRegistered() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        AuditMetaObjectHandler handler = new AuditMetaObjectHandler(beanFactory.getBeanProvider(AuditorProvider.class));
        TestEntity entity = new TestEntity();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertEquals(0L, entity.getCreatedBy());
        assertEquals(0L, entity.getUpdatedBy());
    }

    private AuditMetaObjectHandler handlerFor(long auditorId) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("auditorProvider", (AuditorProvider) () -> OptionalLong.of(auditorId));
        return new AuditMetaObjectHandler(beanFactory.getBeanProvider(AuditorProvider.class));
    }

    /**
     * 为审计填充测试提供具备 BaseEntity 字段的最小持久化实体。
     */
    private static final class TestEntity extends BaseEntity {
    }
}
