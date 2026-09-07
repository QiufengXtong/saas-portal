<!-- 文件作用：将 System 模块 MyBatis 查询可读性设计拆分为可测试、可提交的实施步骤。 -->

# MyBatis Query Readability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 `platform-system` 的简单单表操作统一为 Service 中的 MyBatis-Plus Lambda Wrapper，并将复杂 SQL 全部迁移到约定的 Mapper XML，同时保持租户隔离、锁、审计和认证版本语义不变。

**Architecture:** Service 负责表达简单单表查询条件；Mapper Java 接口只声明必须由数据库完成的复杂操作；XML 承载联表、锁、动态集合、原子更新、精确物理/逻辑删除及绕过逻辑删除的查询。通过静态边界测试约束代码形态，通过 H2 MySQL 模式集成测试验证 XML 加载、参数绑定和关键持久化语义。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis-Plus、MyBatis XML、Flyway、H2 MySQL Mode、JUnit 5、AssertJ、Maven 3.9.1

**Spec:** `docs/superpowers/specs/2026-09-07-mybatis-query-readability-design.md`

## Global Constraints

- 每个新增或修改文件都保留/增加文件职责注释；Mapper 方法增加说明业务用途的 Javadoc，XML 每条语句前增加注释。
- 不改变公开接口、事务注解、异常码、锁顺序、租户上下文、会话撤销时机和数据库迁移。
- `sys_tenant`、`sys_menu`、`sys_bootstrap_lock` 是全局表；`sys_user`、`sys_role`、`sys_user_role`、`sys_role_menu` 继续由租户拦截器保护。
- 简单查询必须使用 `Wrappers.<Entity>query().lambda()` 和实体方法引用，不保留字符串列名。
- `count...IncludingDeleted` 必须保留 XML：MyBatis-Plus 的逻辑删除会给普通 Wrapper 自动追加 `deleted = 0`，无法满足“包含已删除记录”的唯一性检查。
- 批量插入在调用前继续拒绝空集合；XML 用 `<foreach>` 绑定值，主键在 Java 中预先生成，禁止把集合值拼接到 SQL 字符串。
- 只提交本计划涉及的文件；保留并排除当前四个 Controller 的未提交 Javadoc 修改。
- 本机无 Docker；只运行 H2、单元测试和 Maven 打包，不声称已验证 Docker、真实 MySQL 或真实 Redis。

---

## Task 1: 建立简单查询的 Lambda Wrapper 边界测试

**Files:**

- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperSqlBoundaryTest.java`

- [ ] **Step 1: 编写会失败的简单查询源码边界测试**

新增测试，递归扫描 Service 实现源码，禁止字符串列名 Wrapper：

```java
/** 验证 System 模块只在 XML 中保存复杂 SQL，并为七个 Mapper 提供匹配资源。 */
class MapperSqlBoundaryTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    @Test
    void systemQueriesShouldUseLambdaColumnReferences() throws IOException {
        String sources = readAll(MAIN_JAVA.resolve("com/xtong/saas/system"), ".java");
        assertThat(sources)
                .doesNotContain("new QueryWrapper")
                .doesNotMatch("(?s).*\\.(eq|ne|gt|ge|lt|le|like|orderByAsc|orderByDesc)\\([^\\n]*\\\"[a-z_]+\\\".*");
    }

}
```

同时实现私有 `readAll(Path, String)`，第二个参数按文件名后缀筛选，使用 `Files.walk`、UTF-8 读取并关闭流；类和辅助方法都要有职责注释。

- [ ] **Step 2: 运行测试并确认按预期失败**

Run:

```powershell
$env:JAVA_HOME='D:\develop\environment\Java\jdk-21.0.12'
$env:Path="$env:JAVA_HOME\bin;D:\develop\environment\apache\maven\apache-maven-3.9.1\bin;$env:Path"
mvn.cmd -f backend/pom.xml -pl platform-system -am -Dtest=MapperSqlBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 失败，报告 Service 仍有 `new QueryWrapper` 或字符串列名 Wrapper。

- [ ] **Step 3: 保留红灯测试并立即进入 Task 2**

本步不提交失败状态；Task 2 将实现 Lambda Wrapper 并把测试和实现一起提交。

---

## Task 2: 将简单单表查询改为 Lambda Wrapper

**Files:**

- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/service/impl/MenuServiceImpl.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/service/impl/TenantServiceImpl.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/user/service/impl/UserServiceImpl.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/service/impl/RoleServiceImpl.java`
- Verify: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/service/impl/DatabaseSessionPrincipalValidator.java`
- Test: existing service tests under `backend/platform-system/src/test/java/com/xtong/saas/system/{menu,tenant,user,role,auth}/`

- [ ] **Step 1: 先运行简单查询的静态边界与现有业务测试**

Run:

```powershell
mvn.cmd -f backend/pom.xml -pl platform-system -am -Dtest=MapperSqlBoundaryTest,MenuServiceTest,TenantServiceTest,UserServiceTest,RoleServiceTest,DatabaseSessionPrincipalValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `systemQueriesShouldUseLambdaColumnReferences` 因 `new QueryWrapper` 或字符串列名失败；现有业务断言仍通过。理由：Lambda 和字符串 Wrapper 通常生成相同 SQL，仅靠运行时 SQL 断言无法证明源码采用了方法引用。

- [ ] **Step 2: 把所有普通 Wrapper 改为 Lambda Wrapper**

使用下列形态替换字符串列名，保留原条件顺序与动态条件：

```java
menuMapper.selectList(Wrappers.<SystemMenu>query().lambda()
        .eq(SystemMenu::getDeleted, false)
        .eq(SystemMenu::getStatus, MenuStatus.ENABLED)
        .orderByAsc(SystemMenu::getSortOrder)
        .orderByAsc(SystemMenu::getId));

tenantMapper.selectOne(Wrappers.<SystemTenant>query().lambda()
        .eq(SystemTenant::getTenantCode, normalizedCode)
        .eq(SystemTenant::getDeleted, false));

userMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()),
        Wrappers.<SystemUser>query().lambda()
                .eq(SystemUser::getTenantId, tenantId)
                .eq(SystemUser::getDeleted, false)
                .like(hasUsername, SystemUser::getUsername, query.username())
                .eq(query.status() != null, SystemUser::getStatus, query.status())
                .orderByAsc(SystemUser::getId));
```

`RoleServiceImpl`、`UserServiceImpl.requireUser`、`requireEnabledForLogin`、`TenantServiceImpl.hasAnyTenant` 和 `DatabaseSessionPrincipalValidator` 同样逐项改成 `.lambda()` 与 getter 方法引用。删除 `MenuServiceImpl` 的 `QueryWrapper` import，改为 `Wrappers`。

- [ ] **Step 3: 运行聚焦测试并确认简单查询边界通过**

Run: 重复 Step 1 命令。

Expected: 简单查询源码边界和现有业务测试全部通过。

- [ ] **Step 4: 提交简单查询迁移**

```powershell
git add backend/platform-system/src/main/java/com/xtong/saas/system/menu/service/impl/MenuServiceImpl.java backend/platform-system/src/main/java/com/xtong/saas/system/tenant/service/impl/TenantServiceImpl.java backend/platform-system/src/main/java/com/xtong/saas/system/user/service/impl/UserServiceImpl.java backend/platform-system/src/main/java/com/xtong/saas/system/role/service/impl/RoleServiceImpl.java backend/platform-system/src/main/java/com/xtong/saas/system/auth/service/impl/DatabaseSessionPrincipalValidator.java backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperSqlBoundaryTest.java
git commit -m "refactor: 使用Lambda Wrapper表达简单查询"
```

提交前用 `git diff --cached --name-only` 确认没有四个 Controller 文件；若某个测试文件未实际修改，不加入暂存区。

---

## Task 3: 建立 Mapper XML 集成测试夹具并迁移锁查询

**Files:**

- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperXmlIntegrationTest.java`
- Create: `backend/platform-system/src/main/resources/mapper/system/bootstrap/SystemBootstrapLockMapper.xml`
- Create: `backend/platform-system/src/main/resources/mapper/system/tenant/SystemTenantMapper.xml`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/bootstrap/mapper/SystemBootstrapLockMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/mapper/SystemTenantMapper.java`
- Modify: `backend/platform-boot/src/main/resources/application.yml`

- [ ] **Step 1: 编写会因 XML 未加载而失败的 Mapper 集成测试**

测试配置使用 H2 MySQL 模式、Flyway V1–V4、`MybatisSqlSessionFactoryBean`、`PathMatchingResourcePatternResolver("classpath*:mapper/**/*.xml")`、`SpringManagedTransactionFactory` 和 `MapperScannerConfigurer`。注册生产 `MybatisPlusInterceptor`，并在需要租户表时用 `TenantScope.run/call` 设置租户上下文。

首批用例：

```java
/** 在 H2 MySQL 模式中验证 Mapper XML 的加载、参数绑定和租户边界。 */
class MapperXmlIntegrationTest {

    @Test
    void shouldLoadLockMapperStatements() {
        assertThat(configuration.getMappedStatementNames()).contains(
                "com.xtong.saas.system.bootstrap.mapper.SystemBootstrapLockMapper.lockInitialization",
                "com.xtong.saas.system.tenant.mapper.SystemTenantMapper.lockByIdForAuthentication");
    }

    @Test
    void shouldLockBootstrapAndTenantRows() {
        assertThat(bootstrapLockMapper.lockInitialization()).isEqualTo(1L);
        insertTenant(1L, "acme");
        assertThat(tenantMapper.lockByIdAndCodeForAuthentication(1L, "acme").getId()).isEqualTo(1L);
        assertThat(tenantMapper.lockByIdForAuthentication(1L).getTenantCode()).isEqualTo("acme");
        assertThat(tenantMapper.lockByIdForAdminInvariant(1L)).isEqualTo(1L);
    }
}
```

夹具每个测试使用唯一内存库或在 `@BeforeEach` 清理数据，避免测试顺序依赖。全局表查询不设置租户上下文，以证明拦截器确实跳过全局表。

- [ ] **Step 2: 运行测试并确认因 statement 缺失失败**

```powershell
mvn.cmd -f backend/pom.xml -pl platform-system -am -Dtest=MapperXmlIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `BindingException: Invalid bound statement` 或 mapped statement 缺失断言失败。

- [ ] **Step 3: 新增 XML 并清理 Mapper Java 注解**

两个 XML 使用标准 MyBatis 头，namespace 与接口完全一致。例如：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<!-- 文件作用：保存租户目录中带数据库行锁语义的复杂查询。 -->
<mapper namespace="com.xtong.saas.system.tenant.mapper.SystemTenantMapper">
    <!-- 锁定指定有效租户，串行化管理员不变量检查。 -->
    <select id="lockByIdForAdminInvariant" resultType="java.lang.Long">
        SELECT id FROM sys_tenant
        WHERE id = #{tenantId} AND deleted = 0
        FOR UPDATE
    </select>
    <!-- 按租户 ID 和标准化编码锁定有效租户，用于登录。 -->
    <select id="lockByIdAndCodeForAuthentication"
            resultType="com.xtong.saas.system.tenant.entity.SystemTenant">
        SELECT * FROM sys_tenant
        WHERE id = #{tenantId} AND tenant_code = #{tenantCode} AND deleted = 0
        FOR UPDATE
    </select>
    <!-- 按租户 ID 锁定有效租户，用于刷新认证。 -->
    <select id="lockByIdForAuthentication"
            resultType="com.xtong.saas.system.tenant.entity.SystemTenant">
        SELECT * FROM sys_tenant
        WHERE id = #{tenantId} AND deleted = 0
        FOR UPDATE
    </select>
</mapper>
```

Bootstrap XML 同样迁移 `SELECT id FROM sys_bootstrap_lock WHERE id = 1 FOR UPDATE`。Java Mapper 删除 `@Select` import 和注解，只保留方法签名，并为每个方法添加 Javadoc。

- [ ] **Step 4: 显式配置 XML 扫描位置**

在 `application.yml` 中加入：

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
```

- [ ] **Step 5: 运行集成测试并确认锁查询通过**

Run: 重复 Step 2 命令。

Expected: XML statement 全部可调用，三个租户锁查询和 Bootstrap 锁返回预期数据。

- [ ] **Step 6: 提交锁查询迁移**

```powershell
git add backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperXmlIntegrationTest.java backend/platform-system/src/main/resources/mapper/system/bootstrap/SystemBootstrapLockMapper.xml backend/platform-system/src/main/resources/mapper/system/tenant/SystemTenantMapper.xml backend/platform-system/src/main/java/com/xtong/saas/system/bootstrap/mapper/SystemBootstrapLockMapper.java backend/platform-system/src/main/java/com/xtong/saas/system/tenant/mapper/SystemTenantMapper.java backend/platform-boot/src/main/resources/application.yml
git commit -m "refactor: 迁移数据库锁查询至Mapper XML"
```

---

## Task 4: 迁移用户与角色复杂 SQL

**Files:**

- Create: `backend/platform-system/src/main/resources/mapper/system/user/SystemUserMapper.xml`
- Create: `backend/platform-system/src/main/resources/mapper/system/role/SystemRoleMapper.xml`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/user/mapper/SystemUserMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMapper.java`
- Modify: `backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperXmlIntegrationTest.java`
- Replace: `backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperAuditSqlTest.java`

- [ ] **Step 1: 添加用户/角色 XML 行为测试**

插入两个租户、包含已删除数据的用户/角色、启用/禁用管理员和用户角色关系，测试：

- `countByTenantAndUsernameIncludingDeleted` 与 `countByTenantAndCodeIncludingDeleted` 能看到 `deleted = 1`，且不串租户；
- `incrementAuthVersion` 和 `incrementAuthVersions` 只更新当前租户且原子加一；
- 用户/角色 `logicalDeleteWithAudit` 同时写入 `deleted`、`updated_by`、`updated_at`，用户额外增加 `auth_version`；
- `existsTenantAdminRole`、`countByTenantAndIds`、`containsTenantAdminRole`、`countEnabledTenantAdminUsers` 在状态、内置标记、删除标记及租户边界下返回正确结果。

将 `MapperAuditSqlTest` 从反射 SQL 注解/Provider 的脆弱测试改为资源静态检查，只验证 XML 包含审计列和绑定参数：

```java
/** 验证复杂写入 XML 显式保存真实审计字段。 */
class MapperAuditSqlTest {
    @Test
    void mapperXmlShouldWriteAuditColumns() throws IOException {
        assertThat(read("mapper/system/user/SystemUserMapper.xml"))
                .contains("updated_by = #{auditorId}", "updated_at = #{updatedAt}");
        assertThat(read("mapper/system/role/SystemRoleMapper.xml"))
                .contains("updated_by = #{auditorId}", "updated_at = #{updatedAt}");
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

```powershell
mvn.cmd -f backend/pom.xml -pl platform-system -am -Dtest=MapperXmlIntegrationTest,MapperAuditSqlTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 新 statement 未绑定、旧注解测试不再适配或 XML 资源缺失。

- [ ] **Step 3: 创建用户 XML**

完整迁移以下方法，保持原 SQL 条件：

```xml
<!-- 包含逻辑删除记录检查租户内用户名唯一性。 -->
<select id="countByTenantAndUsernameIncludingDeleted" resultType="long">
    SELECT COUNT(*) FROM sys_user
    WHERE tenant_id = #{tenantId} AND username = #{username}
</select>
<!-- 原子增加单个有效用户认证版本。 -->
<update id="incrementAuthVersion">
    UPDATE sys_user SET auth_version = auth_version + 1
    WHERE tenant_id = #{tenantId} AND id = #{userId} AND deleted = 0
</update>
<!-- 原子增加一组有效用户认证版本。 -->
<update id="incrementAuthVersions">
    UPDATE sys_user SET auth_version = auth_version + 1
    WHERE tenant_id = #{tenantId} AND deleted = 0 AND id IN
    <foreach collection="userIds" item="userId" open="(" separator="," close=")">
        #{userId}
    </foreach>
</update>
<!-- 逻辑删除用户，同时记录审计并使旧会话失效。 -->
<update id="logicalDeleteWithAudit">
    UPDATE sys_user
    SET deleted = 1,
        auth_version = auth_version + 1,
        updated_by = #{auditorId},
        updated_at = #{updatedAt}
    WHERE tenant_id = #{tenantId} AND id = #{userId} AND deleted = 0
</update>
```

- [ ] **Step 4: 创建角色 XML**

逐字迁移六个方法：`countByTenantAndCodeIncludingDeleted`、`existsTenantAdminRole`、`countByTenantAndIds`、`containsTenantAdminRole`、`countEnabledTenantAdminUsers`、`logicalDeleteWithAudit`。动态 ID 集合使用 `<foreach>`；联表条件继续同时关联 `id` 和 `tenant_id`；`TENANT_ADMIN`、`ENABLED` 与 `built_in = 1` 的既有业务判断保持不变。

- [ ] **Step 5: 将两个 Java Mapper 收敛为带 Javadoc 的方法签名**

删除 `@Select`、`@Update` 及相关 import；保留 `@Mapper`、`@Param`、参数/返回类型。每个方法写明“包含逻辑删除”“原子递增”“租户管理员判断”等关键语义。

- [ ] **Step 6: 运行聚焦测试并确认通过**

Run: 重复 Step 2 命令。

Expected: 所有用户/角色 XML 行为与审计检查通过。

- [ ] **Step 7: 提交用户与角色 XML**

```powershell
git add backend/platform-system/src/main/resources/mapper/system/user/SystemUserMapper.xml backend/platform-system/src/main/resources/mapper/system/role/SystemRoleMapper.xml backend/platform-system/src/main/java/com/xtong/saas/system/user/mapper/SystemUserMapper.java backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMapper.java backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperXmlIntegrationTest.java backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperAuditSqlTest.java
git commit -m "refactor: 迁移用户角色复杂SQL至XML"
```

---

## Task 5: 迁移菜单及关联表 SQL，移除 SQL Provider

**Files:**

- Create: `backend/platform-system/src/main/resources/mapper/system/menu/SystemMenuMapper.xml`
- Create: `backend/platform-system/src/main/resources/mapper/system/role/SystemRoleMenuMapper.xml`
- Create: `backend/platform-system/src/main/resources/mapper/system/role/SystemUserRoleMapper.xml`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/mapper/SystemMenuMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMenuMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemUserRoleMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/service/impl/RoleServiceImpl.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/user/service/impl/UserServiceImpl.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/bootstrap/SystemBootstrapInitializer.java`
- Modify: `backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperXmlIntegrationTest.java`
- Modify: `backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperAuditSqlTest.java`
- Modify: `backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperSqlBoundaryTest.java`

- [ ] **Step 1: 扩展完整 SQL 分层边界测试**

在 `MapperSqlBoundaryTest` 增加 `javaSourcesShouldNotContainMyBatisSqlAnnotationsProvidersOrScripts` 与 `everySystemMapperShouldHaveMatchingXmlNamespace`。使用七项 `EXPECTED_MAPPERS` 映射逐个读取 XML；精确检查 `org.apache.ibatis.annotations.Select/Insert/Update/Delete/*Provider` import，避免把 Spring MVC 的 `@DeleteMapping` 误判为 SQL 注解，并检查 XML 文件头/语句注释存在。

- [ ] **Step 2: 添加关联表完整行为测试**

测试以下内容：

- `SystemMenuMapper.countEnabledByIds` 忽略删除/禁用菜单；
- `SystemUserRoleMapper` 批量插入后可按用户查角色、按角色查去重用户、计数并按租户/用户物理删除；
- `SystemRoleMenuMapper` 批量插入后可按角色查菜单、加载有效按钮权限码并按租户/角色物理删除；
- 两种批量插入的每一行主键非空且互不相同，`created_by`、`created_at` 等于调用参数；
- 租户 A 的删除、计数、权限查询不会影响或读取租户 B。

XML 批量插入改为接收实体列表，因此先把 Mapper 签名预期写进测试：

```java
int insertBatch(@Param("relations") List<SystemUserRole> relations);
int insertBatch(@Param("relations") List<SystemRoleMenu> relations);
```

- [ ] **Step 3: 运行测试并确认失败**

```powershell
mvn.cmd -f backend/pom.xml -pl platform-system -am -Dtest=MapperXmlIntegrationTest,MapperAuditSqlTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 新签名/statement 尚未实现，编译或绑定失败。

- [ ] **Step 4: 在 Service/Bootstrap 中安全构造关联实体**

保留当前调用处的空集合保护，在 `replaceRoles`、`assignMenus` 和 Bootstrap 中将 ID 集合映射为实体列表；每项使用 `IdWorker.getId()`，显式设置 `tenantId`、外键、`createdBy` 和同一个 `createdAt`：

```java
LocalDateTime createdAt = LocalDateTime.now();
List<SystemUserRole> relations = roleIds.stream().map(roleId -> {
    SystemUserRole relation = new SystemUserRole();
    relation.setId(IdWorker.getId());
    relation.setTenantId(tenantId);
    relation.setUserId(userId);
    relation.setRoleId(roleId);
    relation.setCreatedBy(auditorId);
    relation.setCreatedAt(createdAt);
    return relation;
}).toList();
userRoleMapper.insertBatch(relations);
```

角色菜单同理。理由：XML 只负责绑定数据，雪花主键生成和审计值来源在类型安全的 Java 代码中清晰可测，彻底删除字符串拼 SQL 的 Provider。

- [ ] **Step 5: 创建三个 XML**

菜单 XML 迁移动态 `id IN (...)` 计数。关联 XML 的批量插入使用：

```xml
<!-- 批量保存已在业务层生成主键和审计值的用户角色关联。 -->
<insert id="insertBatch">
    INSERT INTO sys_user_role
        (id, tenant_id, user_id, role_id, created_by, created_at)
    VALUES
    <foreach collection="relations" item="relation" separator=",">
        (#{relation.id}, #{relation.tenantId}, #{relation.userId},
         #{relation.roleId}, #{relation.createdBy}, #{relation.createdAt})
    </foreach>
</insert>
```

其余语句按现有 SQL 原样迁移：

- `SystemUserRoleMapper.xml`: `selectRoleIdsByUserId`、`deleteByUser`、`countByRole`、`selectUserIdsByRole`；
- `SystemRoleMenuMapper.xml`: `deleteByRole`、`selectMenuIdsByRole`、`selectEnabledPermissionCodesByRoleIds`；
- 权限联表必须保留角色/关联租户等值条件、角色/菜单启用与未删除、`BUTTON`、非空权限码和稳定排序。

- [ ] **Step 6: 清理 Mapper Java**

删除所有 SQL 注解 import、`InsertProvider`、`IdWorker`、`Map`、`StringJoiner` 和两个 Provider 内部类。接口只保留 `BaseMapper`、`@Mapper`、必要 `@Param`、集合/实体类型和方法 Javadoc。

- [ ] **Step 7: 运行聚焦测试与静态边界测试**

```powershell
mvn.cmd -f backend/pom.xml -pl platform-system -am -Dtest=MapperXmlIntegrationTest,MapperAuditSqlTest,MapperSqlBoundaryTest,UserServiceTest,RoleServiceTest,SystemBootstrapInitializerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 集成、审计、静态分层及受影响业务测试全部通过；System Java 源码不再存在 SQL 注解、Provider 或 `<script>`。

- [ ] **Step 8: 提交关联 SQL 迁移**

```powershell
git add backend/platform-system/src/main/resources/mapper/system/menu/SystemMenuMapper.xml backend/platform-system/src/main/resources/mapper/system/role/SystemRoleMenuMapper.xml backend/platform-system/src/main/resources/mapper/system/role/SystemUserRoleMapper.xml backend/platform-system/src/main/java/com/xtong/saas/system/menu/mapper/SystemMenuMapper.java backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMenuMapper.java backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemUserRoleMapper.java backend/platform-system/src/main/java/com/xtong/saas/system/role/service/impl/RoleServiceImpl.java backend/platform-system/src/main/java/com/xtong/saas/system/user/service/impl/UserServiceImpl.java backend/platform-system/src/main/java/com/xtong/saas/system/bootstrap/SystemBootstrapInitializer.java backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperXmlIntegrationTest.java backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperAuditSqlTest.java backend/platform-system/src/test/java/com/xtong/saas/system/migration/MapperSqlBoundaryTest.java
git commit -m "refactor: 迁移菜单关联SQL至XML"
```

---

## Task 6: 回归验证配置、租户隔离与完整后端

**Files:**

- Verify all files changed in Tasks 1–5
- Modify only if a failing test exposes an in-scope defect

- [ ] **Step 1: 运行 Common/System 聚焦测试**

```powershell
mvn.cmd -f backend/pom.xml -pl platform-common,platform-system -am test
```

Expected: 全部测试通过，包括租户拦截器顺序、Service 行为、XML 集成和迁移测试。

- [ ] **Step 2: 运行后端全量测试**

```powershell
mvn.cmd -f backend/pom.xml test
```

Expected: 所有模块测试通过，Boot 集成测试能从 `platform-system` 依赖 JAR 加载 `classpath*:/mapper/**/*.xml`。

- [ ] **Step 3: 运行后端打包**

```powershell
mvn.cmd -f backend/pom.xml package
```

Expected: `BUILD SUCCESS`；不启动 Docker、MySQL 或 Redis。

- [ ] **Step 4: 执行静态验收**

```powershell
rg -n "org\.apache\.ibatis\.annotations\.(Select|Insert|Update|Delete)(Provider)?|<script>" backend/platform-system/src/main/java
rg -n "new QueryWrapper|\.eq\(\"|\.like\([^,]+, \"|\.orderByAsc\(\"" backend/platform-system/src/main/java
rg --files backend/platform-system/src/main/resources/mapper/system
git diff --check
git status --short
```

Expected:

- 前两个 `rg` 无输出；
- 第三个 `rg` 恰好列出七个 XML；
- `git diff --check` 无输出；
- `git status --short` 只保留用户原有 Controller Javadoc 改动，不出现本计划遗漏文件。

- [ ] **Step 5: 检查 XML 打包结果**

```powershell
jar tf backend/platform-system/target/platform-system-1.0.0-SNAPSHOT.jar | Select-String "mapper/system/.+Mapper.xml"
```

Expected: 七个 XML 都位于构建产物的 `mapper/system/**` 下。

- [ ] **Step 6: 如验证阶段产生修复则单独提交**

```powershell
git diff --cached --name-only
git commit -m "test: 完成Mapper XML回归验证"
```

仅在确有修复时提交；没有新增修改则不创建空提交。提交前再次排除四个 Controller 文件。

---

## Final Review Checklist

- [ ] 七个 Mapper 接口均只有方法签名/Javadoc，无 SQL 注解、Provider 或文本块。
- [ ] 七个约定路径 XML 全部存在，namespace、statement id、`@Param` 名称和返回类型匹配。
- [ ] 所有简单查询均采用 `Wrappers.<Entity>query().lambda()` 与 getter 方法引用。
- [ ] 包含逻辑删除的唯一性检查、行锁、联表、动态集合、原子版本递增、审计删除和物理关联删除仍在 XML。
- [ ] 批量关联主键唯一，审计用户/时间来自调用方，无字符串拼接集合值。
- [ ] 全局表无需租户上下文；租户表不会跨租户读写；租户拦截器仍先于分页拦截器。
- [ ] H2 集成测试、Common/System 测试、后端全量测试与 Maven package 全部通过。
- [ ] `git diff --check` 通过，构建 JAR 包含七个 XML。
- [ ] 本次提交没有夹带现有 Controller Javadoc 修改。
