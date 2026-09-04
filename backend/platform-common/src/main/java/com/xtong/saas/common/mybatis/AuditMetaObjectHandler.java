package com.xtong.saas.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.OptionalLong;

/**
 * 为 MyBatis-Plus 持久化操作自动填充创建、更新及逻辑删除审计字段。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final long SYSTEM_AUDITOR_ID = 0L;

    private final ObjectProvider<AuditorProvider> auditorProvider;

    public AuditMetaObjectHandler(ObjectProvider<AuditorProvider> auditorProvider) {
        this.auditorProvider = auditorProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        long auditorId = currentAuditorId();
        LocalDateTime now = LocalDateTime.now();
        fillStrategy(metaObject, "createdBy", auditorId);
        fillStrategy(metaObject, "createdAt", now);
        fillStrategy(metaObject, "updatedBy", auditorId);
        fillStrategy(metaObject, "updatedAt", now);
        fillStrategy(metaObject, "deleted", Boolean.FALSE);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        long auditorId = currentAuditorId();
        fillStrategy(metaObject, "updatedBy", auditorId);
        fillStrategy(metaObject, "updatedAt", LocalDateTime.now());
    }

    private long currentAuditorId() {
        AuditorProvider provider = auditorProvider.getIfAvailable();
        if (provider == null) {
            return SYSTEM_AUDITOR_ID;
        }
        OptionalLong auditorId = provider.currentAuditorId();
        return auditorId != null && auditorId.isPresent() ? auditorId.getAsLong() : SYSTEM_AUDITOR_ID;
    }
}
