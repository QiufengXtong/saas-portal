package com.xtong.saas.system.migration;

import com.xtong.saas.system.role.mapper.SystemRoleMenuMapper;
import com.xtong.saas.system.role.mapper.SystemUserRoleMapper;
import com.xtong.saas.system.role.mapper.SystemRoleMapper;
import com.xtong.saas.system.user.mapper.SystemUserMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 以生成 SQL 和 Mapper 注解证明关联写入及逻辑删除显式携带真实审计字段。 */
class MapperAuditSqlTest {

    @Test
    void relationshipBatchSqlShouldWriteCreatedAuditColumns() {
        String userRoleSql = SystemUserRoleMapper.UserRoleSqlProvider.insertBatch(
                Map.of("roleIds", Set.of(8L)));
        String roleMenuSql = SystemRoleMenuMapper.RoleMenuSqlProvider.insertBatch(
                Map.of("menuIds", Set.of(101L)));

        assertThat(userRoleSql).contains("created_by", "created_at", "#{auditorId}", "#{createdAt}");
        assertThat(roleMenuSql).contains("created_by", "created_at", "#{auditorId}", "#{createdAt}");
    }

    @Test
    void logicalDeleteSqlShouldWriteUpdateAuditColumns() throws Exception {
        Update userDelete = SystemUserMapper.class.getMethod(
                "logicalDeleteWithAudit", long.class, long.class, long.class, LocalDateTime.class)
                .getAnnotation(Update.class);
        Update roleDelete = SystemRoleMapper.class.getMethod(
                "logicalDeleteWithAudit", long.class, long.class, long.class, LocalDateTime.class)
                .getAnnotation(Update.class);

        assertThat(String.join(" ", userDelete.value())).contains("deleted = 1", "updated_by", "updated_at");
        assertThat(String.join(" ", roleDelete.value())).contains("deleted = 1", "updated_by", "updated_at");
    }
}
