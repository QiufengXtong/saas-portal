# Common 与 System IAM 第一阶段实施计划

<!-- 文件作用：将已确认的 Common 与 System IAM 设计拆解为可测试、可提交的实施步骤。 -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 `platform-common` 通用基础能力以及 `platform-system` 的基础多租户、用户、角色、权限菜单和 JWT/Redis 认证闭环。

**Architecture:** 保持 Maven 模块化单体结构，`platform-common` 提供业务无关契约，`platform-system` 按 tenant、user、role、menu、auth、bootstrap 领域实现平台能力。Spring Security 验证 HMAC JWT，并通过 Redis 会话实现即时失效；MyBatis-Plus 和 Flyway 负责租户隔离、持久化与版本迁移。

**Tech Stack:** Java 21、Spring Boot 4.1.1、Spring Security、Spring Data Redis、MyBatis-Plus 3.5.17、Flyway、MySQL 8.4、H2、JUnit 5、Mockito。

**Spec:** `docs/superpowers/specs/2026-09-04-common-system-iam-design.md`

## Global Constraints

- Java 根包名固定为 `com.xtong.saas`，不得恢复为 `com.xtong.saasportal`。
- 只实现后端 Common 与 System IAM，不实现前端页面、组织、字典、文件、通知、套餐、数据权限或日志中心。
- `platform-common` 不得依赖 `platform-system` 或 `platform-business`，具体业务错误码不得放进 Common。
- 所有新增或修改文件必须包含文件职责注释；Java 使用类型级 Javadoc，SQL/YAML/XML/Markdown 使用对应格式文件头注释。
- 所有租户数据访问必须从认证或受控租户上下文获取 `tenant_id`，不得信任客户端传入的租户 ID。
- HTTP API 中雪花 ID 输出为字符串；分页参数固定为 `pageNum`、`pageSize`。
- Access Token 使用 HS256，默认 `15m`；Refresh Token 默认 `7d`，Redis 只保存 SHA-256 摘要且刷新后轮换。
- Redis Key 必须以 `saas:portal:` 开头；密码、JWT 密钥和完整 Token 不得写入日志。
- 数据库结构只通过 Flyway 迁移，禁止运行时自动建表。
- 本机没有 Docker；实施时执行 Maven/H2/Mock 测试，Docker 配置只做静态一致性检查并明确未进行容器实测。

---

## 文件结构总览

计划新增的主要文件：

```text
backend/
├── platform-common/src/main/java/com/xtong/saas/common/
│   ├── result/{Result,PageResult}.java
│   ├── exception/{ErrorCode,CommonErrorCode,BusinessException,GlobalExceptionHandler}.java
│   ├── model/BaseEntity.java
│   └── mybatis/{AuditorProvider,AuditMetaObjectHandler,MyBatisCommonConfig}.java
├── platform-system/src/main/java/com/xtong/saas/system/
│   ├── tenant/...
│   ├── user/...
│   ├── role/...
│   ├── menu/...
│   ├── auth/...
│   └── bootstrap/...
├── platform-system/src/main/resources/db/migration/
│   ├── V1__init_system_schema.sql
│   ├── V2__init_system_permissions.sql
│   ├── V3__add_bootstrap_lock.sql
│   └── V4__add_auth_version_and_role_lookup_index.sql
└── platform-boot/src/test/resources/application-test.yml
```

关联表 `sys_user_role`、`sys_role_menu` 使用物理删除和唯一约束；租户、用户、角色、菜单实体表使用 `deleted` 逻辑删除。这样角色重复授权可以安全删除后重建，又不会产生 `(业务键, deleted)` 的历史唯一键冲突。

---

### Task 1: Common 响应、分页与错误码契约

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/platform-common/pom.xml`
- Modify: `backend/platform-common/src/main/java/com/xtong/saas/common/result/Result.java`
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/result/PageResult.java`
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/exception/ErrorCode.java`
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/exception/CommonErrorCode.java`
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/exception/BusinessException.java`
- Modify: `backend/platform-common/src/test/java/com/xtong/saas/common/result/ResultTest.java`
- Create: `backend/platform-common/src/test/java/com/xtong/saas/common/result/PageResultTest.java`
- Create: `backend/platform-common/src/test/java/com/xtong/saas/common/exception/BusinessExceptionTest.java`

**Interfaces:**
- Consumes: 已有 `Result<T>(int code, String message, T data)`。
- Produces: `ErrorCode`、`BusinessException`、完整 `Result<T>` 工厂方法和 `PageResult<T>`，供所有后续任务使用。

- [ ] **Step 1: 补充父 POM 和 Common 编译依赖**

在父 POM properties 中集中声明测试、Flyway 和 H2 所需版本只在 Spring Boot BOM 未管理时才显式添加。`platform-common/pom.xml` 加入实际使用的 Spring Web、Validation、MyBatis-Plus、Lombok 和测试依赖；每段新增依赖前添加 XML 注释说明用途。

```xml
<!-- Common 的 Web 异常、参数校验与 HTTP 状态契约。 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 2: 先扩展失败测试**

测试必须覆盖成功无数据、成功有数据、按错误码失败、分页映射和业务异常保留错误码。

```java
@Test
void shouldCreateFailureResultFromErrorCode() {
    Result<Void> result = Result.failure(CommonErrorCode.INVALID_PARAMETER);
    assertThat(result.code()).isEqualTo(1001);
    assertThat(result.message()).isEqualTo("参数不合法");
    assertThat(result.data()).isNull();
}

@Test
void shouldMapPageRecords() {
    Page<String> page = new Page<>(2, 20, 41);
    page.setRecords(List.of("1", "2"));
    PageResult<Integer> result = PageResult.from(page, Integer::valueOf);
    assertThat(result).isEqualTo(new PageResult<>(List.of(1, 2), 41, 2, 20));
}
```

- [ ] **Step 3: 运行测试确认失败**

Run:

```powershell
$env:JAVA_HOME='D:\develop\environment\Java\jdk-21.0.12'
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-common -am test
```

Expected: FAIL，原因是错误码、失败工厂和分页类型尚不存在。

- [ ] **Step 4: 实现最小契约**

```java
/** 定义可映射到统一响应和 HTTP 状态的错误码契约。 */
public interface ErrorCode {
    int code();
    String message();
    HttpStatus httpStatus();
}

/** 提供与具体业务无关的通用错误码。 */
public enum CommonErrorCode implements ErrorCode {
    INVALID_PARAMETER(1001, "参数不合法", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(1002, "请求内容格式错误", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(1003, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR);
}

/** 表示可安全返回给调用方的业务异常。 */
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    public BusinessException(ErrorCode errorCode) { super(errorCode.message()); this.errorCode = errorCode; }
    public ErrorCode getErrorCode() { return errorCode; }
}

/** 表示统一的分页响应。 */
public record PageResult<T>(List<T> records, long total, long pageNum, long pageSize) {
    public static <S, T> PageResult<T> from(IPage<S> page, Function<S, T> mapper) {
        return new PageResult<>(page.getRecords().stream().map(mapper).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
```

`Result<T>` 增加 `success()`、`success(T)`、`failure(ErrorCode)` 和 `failure(int, String)`，并保持成功码为 0。

- [ ] **Step 5: 运行 Common 测试并提交**

Run: Task 1 Step 3 命令。

Expected: PASS，0 failures。

```powershell
git add backend/pom.xml backend/platform-common
git commit -m "feat(common): 完善统一响应和错误码契约"
```

---

### Task 2: Common 全局异常处理

**Files:**
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/exception/GlobalExceptionHandler.java`
- Create: `backend/platform-common/src/test/java/com/xtong/saas/common/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ErrorCode`、`CommonErrorCode`、`BusinessException`、`Result.failure(...)`。
- Produces: Controller 层统一异常响应，Security 过滤器外异常仍由后续专用处理器负责。

- [ ] **Step 1: 写异常映射测试**

```java
@Test
void shouldMapBusinessExceptionToDeclaredStatus() {
    ResponseEntity<Result<Void>> response = handler.handleBusinessException(
            new BusinessException(CommonErrorCode.INVALID_PARAMETER));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo(1001);
}

@Test
void shouldHideUnexpectedExceptionDetails() {
    ResponseEntity<Result<Void>> response = handler.handleUnexpectedException(
            new IllegalStateException("jdbc:mysql://user:password@host"));
    assertThat(response.getBody().message()).isEqualTo("系统内部错误");
}
```

同时构造 `MethodArgumentNotValidException`、`ConstraintViolationException` 和 `HttpMessageNotReadableException`，断言返回 400 且不包含堆栈。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-common -am -Dtest=GlobalExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，`GlobalExceptionHandler` 不存在。

- [ ] **Step 3: 实现异常处理器**

```java
/** 将 Controller 链路异常转换为统一且不泄露内部信息的响应。 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(code.httpStatus()).body(Result.failure(code));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled request exception", exception);
        ErrorCode code = CommonErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.httpStatus()).body(Result.failure(code));
    }
}
```

Validation 错误只返回第一个稳定、可读的字段消息；不得拼接完整请求体或敏感 rejected value。

- [ ] **Step 4: 运行 Common 全量测试并提交**

Run: Task 1 Step 3 命令。

Expected: PASS。

```powershell
git add backend/platform-common
git commit -m "feat(common): 增加全局异常处理"
```

---

### Task 3: BaseEntity、审计和 MyBatis 通用配置

**Files:**
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/model/BaseEntity.java`
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/mybatis/AuditorProvider.java`
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/mybatis/AuditMetaObjectHandler.java`
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/mybatis/MyBatisCommonConfig.java`
- Create: `backend/platform-common/src/test/java/com/xtong/saas/common/mybatis/AuditMetaObjectHandlerTest.java`
- Create: `backend/platform-common/src/test/java/com/xtong/saas/common/mybatis/MyBatisCommonConfigTest.java`

**Interfaces:**
- Consumes: MyBatis-Plus 注解和 Spring `ObjectProvider`。
- Produces: `BaseEntity`、`AuditorProvider.currentAuditorId()`、分页拦截器与自动审计填充。

- [ ] **Step 1: 写审计和配置测试**

```java
@Test
void shouldFillCreateAndUpdateAuditFields() {
    TestEntity entity = new TestEntity();
    MetaObject metaObject = SystemMetaObject.forObject(entity);
    handler.insertFill(metaObject);
    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
    assertThat(entity.getCreatedBy()).isEqualTo(42L);
    assertThat(entity.getDeleted()).isFalse();
}
```

配置测试断言 `PaginationInnerInterceptor` 使用 `DbType.MYSQL` 且最大分页大小为 500。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-common -am -Dtest=AuditMetaObjectHandlerTest,MyBatisCommonConfigTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，基础实体和配置类不存在。

- [ ] **Step 3: 实现基础实体和审计 SPI**

```java
/** 为持久化实体提供统一主键、审计时间和逻辑删除字段。 */
@Getter
@Setter
public abstract class BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;
}

/** 为通用审计填充提供当前操作者 ID，具体认证实现由上层模块注入。 */
public interface AuditorProvider {
    OptionalLong currentAuditorId();
}
```

匿名和系统初始化场景统一使用 `0L`。插入时同时填充 created/updated 字段，更新时只更新 updated 字段。

- [ ] **Step 4: 实现分页配置**

```java
/** 提供跨模块复用的 MyBatis-Plus 分页组件。 */
@Configuration(proxyBeanMethods = false)
public class MyBatisCommonConfig {
    @Bean
    public PaginationInnerInterceptor paginationInnerInterceptor() {
        PaginationInnerInterceptor interceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        interceptor.setMaxLimit(500L);
        return interceptor;
    }
}
```

- [ ] **Step 5: 运行测试并提交**

Run: Task 1 Step 3 命令。

Expected: PASS。

```powershell
git add backend/platform-common
git commit -m "feat(common): 增加实体审计和分页配置"
```

---

### Task 4: System 依赖、Flyway、表结构和持久化模型

**Files:**
- Modify: `backend/platform-system/pom.xml`
- Modify: `backend/platform-boot/pom.xml`
- Create: `backend/platform-system/src/main/resources/db/migration/V1__init_system_schema.sql`
- Create: `backend/platform-system/src/main/resources/db/migration/V2__init_system_permissions.sql`
- Create: `backend/platform-system/src/main/resources/db/migration/V3__add_bootstrap_lock.sql`
- Create: `backend/platform-system/src/main/resources/db/migration/V4__add_auth_version_and_role_lookup_index.sql`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/entity/SystemTenant.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/entity/SystemUser.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/entity/SystemRole.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/entity/SystemMenu.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/entity/SystemUserRole.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/entity/SystemRoleMenu.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/enums/TenantStatus.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/enums/UserStatus.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/enums/RoleStatus.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/enums/MenuStatus.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/enums/MenuType.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/mapper/SystemTenantMapper.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/mapper/SystemUserMapper.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMapper.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/mapper/SystemMenuMapper.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemUserRoleMapper.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMenuMapper.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/migration/SystemMigrationTest.java`

**Interfaces:**
- Consumes: Common `BaseEntity` 和 MyBatis-Plus。
- Produces: 六张系统表、19 个权限码、领域实体和 Mapper，供租户/RBAC/Auth 服务使用。

- [ ] **Step 1: 增加 System 实际依赖**

`platform-system` 加入 Security、OAuth2 JOSE、Redis、Validation、MyBatis-Plus、Flyway Core、Flyway MySQL、Lombok；测试加入 H2、Spring Boot Test、Spring Security Test。`platform-boot` 删除被 System 已传递提供的重复依赖时，必须先用 dependency tree 确认启动依赖仍完整。

```xml
<!-- System 的认证、方法授权与 JWT 编解码。 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>
<!-- System 的版本化数据库迁移。 -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

- [ ] **Step 2: 先写空库迁移测试**

```java
@Test
void shouldCreateSystemSchemaAndPermissionCatalog() throws Exception {
    DataSource dataSource = new JdbcDataSource();
    ((JdbcDataSource) dataSource).setURL("jdbc:h2:mem:migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
    assertThat(flyway.migrate().migrationsExecuted()).isEqualTo(4);
    try (Connection connection = dataSource.getConnection()) {
        assertThat(queryForInt(connection, "select count(*) from information_schema.tables where table_name like 'sys_%'"))
                .isEqualTo(7);
        assertThat(queryForInt(connection, "select count(*) from sys_menu where permission_code is not null"))
                .isEqualTo(19);
    }
}
```

- [ ] **Step 3: 运行迁移测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am -Dtest=SystemMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，迁移脚本不存在。

- [ ] **Step 4: 编写 V1 表结构**

V1 精确创建以下约束和索引：

```text
uk_sys_tenant_code(tenant_code)
uk_sys_user_tenant_username(tenant_id, username)
idx_sys_user_tenant_status(tenant_id, status)
uk_sys_role_tenant_code(tenant_id, role_code)
idx_sys_role_tenant_status(tenant_id, status)
uk_sys_menu_permission(permission_code)
idx_sys_menu_parent_sort(parent_id, sort_order)
uk_sys_user_role(tenant_id, user_id, role_id)
idx_user_role_role(tenant_id, role_id, user_id)
uk_sys_role_menu(tenant_id, role_id, menu_id)
```

实体表使用 `BIGINT` 主键、`TIMESTAMP(3)` 审计时间和 `TINYINT` 逻辑删除；关联表包含独立雪花 ID、tenant_id、关联 ID 和创建审计字段，使用物理删除。

- [ ] **Step 5: 编写 V2-V4 增量迁移**

V2 使用固定、可重复追踪的正整数 ID 插入系统管理目录、用户管理、角色管理及 19 个按钮权限。权限码必须与设计文档逐字一致，SQL 不插入租户、用户或密码。V3 创建多实例首次初始化锁；V4 为 `sys_user` 增加 `auth_version BIGINT NOT NULL DEFAULT 0`，并增加角色反向用户查询索引 `idx_user_role_role(tenant_id, role_id, user_id)`。

- [ ] **Step 6: 实现实体、枚举和 Mapper**

```java
/** 表示租户内可登录的系统用户持久化模型。 */
@TableName("sys_user")
@Getter
@Setter
public class SystemUser extends BaseEntity {
    private Long tenantId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String mobile;
    private UserStatus status;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime lastLoginAt;
    private Long authVersion;
}

/** 提供系统用户的 MyBatis-Plus 数据访问入口。 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {}
```

所有枚举数据库值固定使用英文名称 `ENABLED`、`DISABLED`、`DIRECTORY`、`MENU`、`BUTTON`。

- [ ] **Step 7: 运行迁移测试和模块测试并提交**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am test
```

Expected: PASS，迁移四版、6 张业务表、Bootstrap 锁、用户认证安全版本、角色反向索引和 19 个权限码。

```powershell
git add backend/pom.xml backend/platform-common/pom.xml backend/platform-system backend/platform-boot/pom.xml
git commit -m "feat(system): 建立 IAM 表结构和持久化模型"
```

---

### Task 5: 租户上下文与 SQL 隔离

**Files:**
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/context/TenantContextHolder.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/context/TenantScope.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/config/TenantMyBatisConfig.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/service/TenantService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/service/impl/TenantServiceImpl.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/tenant/exception/TenantErrorCode.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/tenant/context/TenantScopeTest.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/tenant/config/TenantLineHandlerTest.java`

**Interfaces:**
- Consumes: `SystemTenantMapper`、`PaginationInnerInterceptor`。
- Produces: `TenantContextHolder.requireTenantId()`、`TenantScope.call/run(...)`、租户 SQL 拦截器和租户查询服务。

- [ ] **Step 1: 写上下文清理和排除表测试**

```java
@Test
void shouldRestoreOuterTenantAfterNestedScope() {
    TenantScope.run(10L, () -> {
        assertThat(TenantContextHolder.requireTenantId()).isEqualTo(10L);
        TenantScope.run(20L, () -> assertThat(TenantContextHolder.requireTenantId()).isEqualTo(20L));
        assertThat(TenantContextHolder.requireTenantId()).isEqualTo(10L);
    });
    assertThat(TenantContextHolder.currentTenantId()).isEmpty();
}

@Test
void shouldIgnoreOnlyGlobalTables() {
    assertThat(handler.ignoreTable("sys_tenant")).isTrue();
    assertThat(handler.ignoreTable("sys_menu")).isTrue();
    assertThat(handler.ignoreTable("sys_user")).isFalse();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am -Dtest=TenantScopeTest,TenantLineHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，上下文和 Handler 不存在。

- [ ] **Step 3: 实现可嵌套租户作用域**

```java
/** 保存当前线程受信任的租户 ID，并提供强制读取和清理能力。 */
public final class TenantContextHolder {
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    public static OptionalLong currentTenantId() { /* 返回 OptionalLong */ }
    public static long requireTenantId() { /* 缺失时抛 TenantContextMissing */ }
    static void set(long tenantId) { TENANT_ID.set(tenantId); }
    static void clear() { TENANT_ID.remove(); }
}
```

`TenantScope` 在进入时保存旧值，在 `finally` 中恢复旧值或清理，禁止裸 set 后遗漏清理。

- [ ] **Step 4: 配置租户拦截器且保证顺序**

```java
/** 将受信任租户上下文自动追加到所有租户表 SQL。 */
@Configuration(proxyBeanMethods = false)
public class TenantMyBatisConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(PaginationInnerInterceptor pagination) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler()));
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
```

租户拦截必须先于分页。`sys_tenant`、`sys_menu` 和 Flyway 历史表排除，其他 `sys_*` 关联表均不排除。

- [ ] **Step 5: 实现租户查询服务**

```java
public interface TenantService {
    SystemTenant requireEnabledByCode(String tenantCode);
    boolean hasAnyTenant();
}
```

编码查询必须排除逻辑删除并校验 `ENABLED`，不存在与禁用分别返回稳定租户错误码。

- [ ] **Step 6: 运行测试并提交**

Run: Task 4 Step 7 命令。

Expected: PASS。

```powershell
git add backend/platform-system
git commit -m "feat(system): 增加租户上下文和数据隔离"
```

---

### Task 6: 菜单树与权限加载

**Files:**
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemUserRoleMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMenuMapper.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/dto/MenuTreeNodeVO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/service/MenuService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/service/impl/MenuServiceImpl.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/service/PermissionService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/service/impl/PermissionServiceImpl.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/exception/MenuErrorCode.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/menu/service/MenuServiceTest.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/menu/service/PermissionServiceTest.java`

**Interfaces:**
- Consumes: `SystemMenuMapper`、角色及关联 Mapper。
- Produces: `MenuService.getTree()`、`MenuService.getPermissionCodes()`、`PermissionService.loadUserPermissions(tenantId, userId)`。

- [ ] **Step 1: 写菜单树和管理员权限测试**

```java
@Test
void shouldBuildStableTreeBySortOrder() {
    when(menuMapper.selectList(any())).thenReturn(List.of(root, secondChild, firstChild));
    List<MenuTreeNodeVO> tree = service.getTree();
    assertThat(tree.getFirst().children()).extracting(MenuTreeNodeVO::id)
            .containsExactly(firstChild.getId().toString(), secondChild.getId().toString());
}

@Test
void tenantAdminShouldReceiveAllEnabledPermissions() {
    when(roleMapper.existsTenantAdminRole(1L, 2L)).thenReturn(true);
    assertThat(permissionService.loadUserPermissions(1L, 2L))
            .containsExactlyInAnyOrderElementsOf(allPermissionCodes);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am -Dtest=MenuServiceTest,PermissionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，菜单和权限服务不存在。

- [ ] **Step 3: 实现菜单树**

`MenuTreeNodeVO` 的 `id`、`parentId` 使用字符串，包含 name、type、routePath、component、icon、permissionCode、sortOrder、visible、children。只返回未删除且启用节点，按 `sortOrder`、`id` 稳定排序，孤儿节点不静默提升为根节点而是记录安全告警并跳过。

- [ ] **Step 4: 实现权限加载**

```java
public interface PermissionService {
    Set<String> loadUserPermissions(long tenantId, long userId);
}
```

若用户拥有有效 `TENANT_ADMIN` 内置角色，返回所有启用按钮权限码；否则只查询有效用户角色、有效角色及有效菜单的交集。方法在 `TenantScope.call(tenantId, ...)` 内执行。

- [ ] **Step 5: 运行测试并提交**

Run: Task 4 Step 7 命令。

Expected: PASS。

```powershell
git add backend/platform-system
git commit -m "feat(system): 实现菜单树和权限加载"
```

---

### Task 7: 用户管理领域服务

**Files:**
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/user/mapper/SystemUserMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemUserRoleMapper.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/dto/UserQueryDTO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/dto/CreateUserDTO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/dto/UpdateUserDTO.java`（用户名存在时复用统一身份规范化并使旧会话失效）
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/dto/ResetPasswordDTO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/dto/AssignUserRolesDTO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/vo/UserVO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/service/UserService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/service/impl/UserServiceImpl.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/exception/UserErrorCode.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/user/service/UserServiceTest.java`

**Interfaces:**
- Consumes: 用户/角色 Mapper、`TenantContextHolder`、Spring `PasswordEncoder`、后续 `SessionService` 接口。
- Produces: 用户分页、详情、创建、更新、启停、密码重置、删除和角色分配业务能力。

- [ ] **Step 1: 定义会话失效端口并写用户规则测试**

为避免用户服务依赖 Redis 实现，在 `auth/api/SessionRevocationService.java` 定义：

```java
/** 向用户和角色领域公开会话撤销能力，隐藏 Redis 存储细节。 */
public interface SessionRevocationService {
    void revokeSession(String sessionId);
    void revokeAllUserSessions(long tenantId, long userId);
}
```

测试创建重复用户名、跨租户角色、删除当前用户、重置密码撤销会话和分页上限。

```java
@Test
void shouldRejectRoleFromAnotherTenant() {
    when(roleMapper.countByTenantAndIds(1L, Set.of(99L))).thenReturn(0L);
    assertThatThrownBy(() -> service.assignRoles(10L, Set.of(99L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_ROLE_ASSIGNMENT);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am -Dtest=UserServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，DTO、Service 和错误码不存在。

- [ ] **Step 3: 实现 DTO/VO 和 Service 接口**

```java
public interface UserService {
    PageResult<UserVO> page(UserQueryDTO query);
    UserVO get(long userId);
    String create(CreateUserDTO command);
    void update(long userId, UpdateUserDTO command);
    void enable(long userId);
    void disable(long userId, long currentUserId);
    void resetPassword(long userId, ResetPasswordDTO command);
    void delete(long userId, long currentUserId);
    void assignRoles(long userId, Set<Long> roleIds);
    SystemUser requireEnabledForLogin(long tenantId, String username);
    void recordLoginSuccess(long userId, LocalDateTime loginAt);
}
```

DTO 使用 Jakarta Validation，密码同时校验最少 8 字符和 UTF-8 不超过 72 字节。VO 的所有 ID 转换为字符串，不返回 `passwordHash`。

- [ ] **Step 4: 实现事务和租户规则**

创建用户时检查当前租户用户名唯一、BCrypt 编码密码、校验角色归属并在事务中写入关系。更新 username 时复用统一身份规范化与唯一性校验，并递增认证安全版本使旧会话失效。停用、密码重置和删除成功后撤销该用户全部会话；不能操作当前用户；最后一个有效租户管理员不能被停用或删除。

- [ ] **Step 5: 运行用户测试并提交**

Run: Task 4 Step 7 命令。

Expected: PASS。

```powershell
git add backend/platform-system
git commit -m "feat(system): 实现租户用户管理服务"
```

---

### Task 8: 角色管理与授权领域服务

**Files:**
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemUserRoleMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/role/mapper/SystemRoleMenuMapper.java`
- Modify: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/mapper/SystemMenuMapper.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/dto/RoleQueryDTO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/dto/CreateRoleDTO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/dto/UpdateRoleDTO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/dto/AssignRoleMenusDTO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/vo/RoleVO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/service/RoleService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/service/impl/RoleServiceImpl.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/exception/RoleErrorCode.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/role/service/RoleServiceTest.java`

**Interfaces:**
- Consumes: 角色、用户角色、角色菜单、菜单 Mapper 和 `SessionRevocationService`。
- Produces: 角色分页、详情、创建、更新、启停、删除及菜单整体授权。

- [ ] **Step 1: 写内置角色和关联规则测试**

```java
@Test
void shouldRejectDeletingRoleWithAssignedUsers() {
    when(userRoleMapper.countByRole(1L, 8L)).thenReturn(2L);
    assertThatThrownBy(() -> service.delete(8L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(RoleErrorCode.ROLE_IN_USE);
}

@Test
void shouldReplaceMenusAndRevokeAffectedUsersInOneTransaction() {
    service.assignMenus(8L, Set.of(101L, 102L));
    verify(roleMenuMapper).deleteByRole(1L, 8L);
    verify(roleMenuMapper).insertBatch(1L, 8L, Set.of(101L, 102L));
    verify(sessionRevocationService).revokeAllUserSessions(1L, affectedUserId);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am -Dtest=RoleServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，角色服务不存在。

- [ ] **Step 3: 实现角色接口和事务规则**

```java
public interface RoleService {
    PageResult<RoleVO> page(RoleQueryDTO query);
    RoleVO get(long roleId);
    String create(CreateRoleDTO command);
    void update(long roleId, UpdateRoleDTO command);
    void enable(long roleId);
    void disable(long roleId);
    void delete(long roleId);
    void assignMenus(long roleId, Set<Long> menuIds);
}
```

角色 code 创建后不可变。`TENANT_ADMIN` 或 `builtIn=true` 角色拒绝停用、删除和重新授权。删除前检查用户关联。菜单 ID 必须全部是未删除、启用的全局菜单。

- [ ] **Step 4: 实现授权后的会话撤销**

在同一事务中物理删除旧 `sys_role_menu` 并批量插入新关系。查询当前租户下使用该角色的用户 ID，事务成功后逐用户撤销全部会话；若使用事务同步回调，必须测试只有提交成功才执行撤销。

- [ ] **Step 5: 运行角色测试并提交**

Run: Task 4 Step 7 命令。

Expected: PASS。

```powershell
git add backend/platform-system
git commit -m "feat(system): 实现角色管理和菜单授权"
```

---

### Task 9: JWT、Refresh Token 与 Redis 会话存储

**Files:**
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/config/AuthProperties.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/model/AuthSession.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/model/AuthenticatedUser.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/token/AccessTokenService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/token/JwtAccessTokenService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/token/RefreshTokenGenerator.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/token/TokenHashService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/session/SessionStore.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/session/RedisSessionStore.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/session/LoginFailureService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/session/RedisLoginFailureService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/exception/AuthErrorCode.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/auth/token/JwtAccessTokenServiceTest.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/auth/session/RedisSessionStoreTest.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/auth/session/RedisLoginFailureServiceTest.java`

**Interfaces:**
- Consumes: Spring Security JOSE、`StringRedisTemplate`、受 Spring 管理的 Jackson `ObjectMapper`。
- Produces: JWT 签发验证、随机 Refresh Token、Redis 会话、令牌轮换和登录失败限制。

- [ ] **Step 1: 写 Token 和 Redis 行为测试**

```java
@Test
void shouldRoundTripRequiredJwtClaims() {
    String token = service.issue(new AuthenticatedUser(1L, 2L, "s1", "admin", Set.of("system:user:list")));
    AuthenticatedUser user = service.parse(token);
    assertThat(user.tenantId()).isEqualTo(1L);
    assertThat(user.userId()).isEqualTo(2L);
    assertThat(user.sessionId()).isEqualTo("s1");
}

@Test
void shouldRotateRefreshTokenOnlyOnce() {
    store.create(session, refreshTokenHash, Duration.ofDays(7));
    assertThat(store.peekRefreshSession(refreshTokenHash)).contains(session);
    assertThat(store.rotateRefreshToken(refreshTokenHash, newHash, Duration.ofDays(7))).isPresent();
    assertThat(store.rotateRefreshToken(refreshTokenHash, anotherHash, Duration.ofDays(7))).isEmpty();
}
```

Redis 测试 Mock `StringRedisTemplate`、Value Operations 与 Lua 调用，断言完整 Token 从未作为 Key 或 Value 传入，并校验版本化 ZSET 索引、裁剪和绝对过期协议。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am -Dtest=JwtAccessTokenServiceTest,RedisSessionStoreTest,RedisLoginFailureServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，认证基础类型不存在。

- [ ] **Step 3: 实现并校验 AuthProperties**

```java
/** 集中定义令牌、登录限制和会话有效期配置。 */
@ConfigurationProperties("saas.auth")
@Validated
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @Min(1) int loginFailureLimit,
        @NotNull Duration loginFailureWindow,
        @NotNull Duration loginLockDuration) {}
```

启动时校验 secret UTF-8 字节长度不少于 32、TTL 为正且 Access TTL 小于 Refresh TTL。

- [ ] **Step 4: 实现 JWT 和随机令牌**

使用 `NimbusJwtEncoder`、`NimbusJwtDecoder` 和 `MacAlgorithm.HS256`。`RefreshTokenGenerator` 用 `SecureRandom` 生成至少 32 字节并使用 URL-safe Base64 无 padding 编码；`TokenHashService` 使用 SHA-256 输出小写十六进制。

- [ ] **Step 5: 实现 Redis 会话协议**

```java
public interface SessionStore {
    boolean create(AuthSession session, String refreshTokenHash, Duration ttl);
    Optional<AuthSession> find(String sessionId);
    Optional<AuthSession> peekRefreshSession(String refreshTokenHash);
    Optional<AuthSession> rotateRefreshToken(
            String currentRefreshTokenHash, String newRefreshTokenHash, Duration ttl);
    void delete(String sessionId);
    void deleteAll(long tenantId, long userId);
}
```

`AuthSession` 保存 sessionId、tenantId、userId、username、displayName、权限集合、认证安全版本和当前 refreshTokenHash。`RedisSessionStore` 同时实现 `SessionStore` 与 Task 7 的 `SessionRevocationService`。刷新先只读定位会话，再取得租户行锁并校验租户、用户状态和认证安全版本，最后由 Lua 原子轮换 Refresh 摘要。用户会话索引使用版本化 `saas:portal:auth:v2:user-sessions:{tenantId}:{userId}` ZSET，Lua 通过 Redis `TIME` 计算当前毫秒，Java 仅传相对 TTL；score 为会话绝对过期毫秒，索引 TTL 始终取清理后最大 score。

滚动升级期间不对旧 `saas:portal:auth:user-sessions:*` SET 执行 ZSET 命令。旧 session/refresh 键仍可读取，旧会话缺失的认证安全版本按 `0` 处理；旧 Refresh 成功轮换后加入 v2 索引。管理事务递增数据库 `auth_version`，因此旧索引无法清理时仍由逐请求和刷新校验阻止旧会话复活。

- [ ] **Step 6: 实现登录失败限制**

`LoginFailureService` 暴露 `assertAllowed`、`recordFailure`、`clear`。Key 使用规范化后的 tenantCode 和 username；达到阈值后设置锁定 Key，错误响应不区分用户名或密码错误，防止账号枚举。

- [ ] **Step 7: 运行测试并提交**

Run: Task 4 Step 7 命令。

Expected: PASS。

```powershell
git add backend/platform-system
git commit -m "feat(system): 实现 JWT 和 Redis 会话存储"
```

---

### Task 10: 登录、刷新、退出与首次管理员初始化

**Files:**
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/dto/LoginRequest.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/dto/RefreshTokenRequest.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/vo/TokenResponse.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/vo/CurrentUserVO.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/service/AuthService.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/service/impl/AuthServiceImpl.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/bootstrap/config/BootstrapProperties.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/bootstrap/SystemBootstrapInitializer.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/bootstrap/exception/BootstrapConfigurationException.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/auth/service/AuthServiceTest.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/bootstrap/SystemBootstrapInitializerTest.java`

**Interfaces:**
- Consumes: 租户、用户、权限、Token、Session、登录失败服务。
- Produces: 完整认证用例和安全、幂等的首租户初始化。

- [ ] **Step 1: 写认证流程测试**

```java
@Test
void shouldLoginWithTenantUsernameAndPassword() {
    TokenResponse response = authService.login(new LoginRequest("default", "admin", "Secret123"));
    assertThat(response.tokenType()).isEqualTo("Bearer");
    verify(sessionStore).create(any(AuthSession.class), eq(expectedRefreshHash), eq(Duration.ofDays(7)));
    verify(loginFailureService).clear("default", "admin");
}

@Test
void shouldRotateRefreshToken() {
    TokenResponse refreshed = authService.refresh(new RefreshTokenRequest(oldRefreshToken));
    verify(sessionStore).peekRefreshSession(oldHash);
    verify(tenantService).lockAndRequireEnabled(tenantId);
    verify(sessionStore).rotateRefreshToken(oldHash, newHash, refreshTtl);
    assertThat(refreshed.refreshToken()).isNotEqualTo(oldRefreshToken);
}
```

覆盖租户不存在/禁用、用户不存在/禁用、密码错误、登录锁定、刷新重放、退出当前会话和撤销全部用户会话。

- [ ] **Step 2: 写初始化器测试**

```java
@Test
void shouldInitializeOnlyWhenNoTenantExists() {
    when(tenantService.hasAnyTenant()).thenReturn(false, true);
    initializer.run();
    initializer.run();
    verify(tenantMapper, times(1)).insert(any(SystemTenant.class));
    verify(userMapper, times(1)).insert(any(SystemUser.class));
}
```

另测缺少任一变量时抛 `BootstrapConfigurationException`，异常消息只包含配置名，不包含已提供的密码值。

- [ ] **Step 3: 运行测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am -Dtest=AuthServiceTest,SystemBootstrapInitializerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，认证和初始化用例不存在。

- [ ] **Step 4: 实现认证用例**

```java
public interface AuthService {
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(RefreshTokenRequest request);
    void logout(String sessionId);
    CurrentUserVO currentUser(AuthenticatedUser principal);
}
```

登录先规范化 tenantCode/username 并执行前置限流；已知租户候选取得租户行 `FOR UPDATE` 后在锁内执行二次限流，再校验租户、用户、密码与权限，失败计数也在该锁事务内写入。用户或密码错误返回同一错误码。刷新按 `peek refresh session -> tenant lock -> user status/authVersion -> atomic rotate` 执行；任何失败不得恢复旧 Refresh Token。

- [ ] **Step 5: 实现首次初始化事务**

`BootstrapProperties` 使用前缀 `saas.bootstrap`。初始化器只在 `sys_tenant` 空时运行，在一个事务和明确 TenantScope 中创建租户、`TENANT_ADMIN` 角色、BCrypt 管理员及用户角色关系。管理员用户名和租户编码统一执行 `strip`、`Locale.ROOT` 小写及 ASCII 身份规则校验，密码不 trim。

- [ ] **Step 6: 运行测试并提交**

Run: Task 4 Step 7 命令。

Expected: PASS。

```powershell
git add backend/platform-system
git commit -m "feat(system): 实现认证流程和管理员初始化"
```

---

### Task 11: Spring Security 过滤器与认证 HTTP API

**Files:**
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/config/SecurityConfig.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/filter/JwtAuthenticationFilter.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/handler/RestAuthenticationEntryPoint.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/handler/RestAccessDeniedHandler.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/service/SecurityAuditorProvider.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/auth/controller/AuthController.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/auth/filter/JwtAuthenticationFilterTest.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/auth/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `AccessTokenService`、`SessionStore`、`AuthService`、Common 统一响应。
- Produces: `/api/v1/auth/*`、请求认证上下文、租户上下文、401/403 JSON 和方法授权基础。

- [ ] **Step 1: 写过滤器清理和 HTTP 状态测试**

```java
@Test
void shouldClearTenantContextAfterFilterChain() throws Exception {
    filter.doFilter(requestWithBearer(validToken), response, chain);
    assertThat(TenantContextHolder.currentTenantId()).isEmpty();
}

@Test
void protectedEndpointWithoutTokenShouldReturnUnified401() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(1101));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am -Dtest=JwtAuthenticationFilterTest,AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，Security 配置和 Controller 不存在。

- [ ] **Step 3: 实现 SecurityFilterChain**

```java
/** 定义无状态 API 的认证、匿名端点和方法权限策略。 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

配置专用 EntryPoint 和 AccessDeniedHandler。禁用表单登录、HTTP Basic 和服务端 Session。

在同一配置中使用 `@EnableConfigurationProperties({AuthProperties.class, BootstrapProperties.class})` 注册两组配置属性，避免依赖隐式扫描。

- [ ] **Step 4: 实现 JWT 过滤器**

Bearer 缺失时不报错并继续；格式错误、签名错误、过期、会话缺失统一返回 401。有效请求从 Redis 会话构造 `AuthenticatedUser` 和 authorities，写入 SecurityContext，再进入 `TenantScope.run`。所有路径都在 finally 清理租户上下文。

- [ ] **Step 5: 实现认证 Controller**

```java
@PostMapping("/login")
public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return Result.success(authService.login(request));
}
```

`refresh` 使用请求体；`logout` 从受信 principal 获取 sessionId；`me` 返回当前用户和权限。Controller 不捕获通用异常。

- [ ] **Step 6: 实现审计用户提供器并运行测试**

`SecurityAuditorProvider` 从 SecurityContext 的 `AuthenticatedUser.userId()` 返回审计 ID，无认证返回 empty。

Run: Task 4 Step 7 命令。

Expected: PASS。

- [ ] **Step 7: 提交**

```powershell
git add backend/platform-system
git commit -m "feat(system): 接入 Spring Security 和认证接口"
```

---

### Task 12: 用户、角色和菜单 HTTP API

**Files:**
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/user/controller/UserController.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/role/controller/RoleController.java`
- Create: `backend/platform-system/src/main/java/com/xtong/saas/system/menu/controller/MenuController.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/user/controller/UserControllerTest.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/role/controller/RoleControllerTest.java`
- Create: `backend/platform-system/src/test/java/com/xtong/saas/system/menu/controller/MenuControllerTest.java`

**Interfaces:**
- Consumes: Tasks 6-8 的领域服务和 Task 11 的方法级授权。
- Produces: 设计文档列出的全部用户、角色、菜单与权限 HTTP API。

- [ ] **Step 1: 写权限码映射测试**

```java
@Test
@WithMockUser(authorities = "system:user:list")
void shouldAllowUserListWithMatchingAuthority() throws Exception {
    mockMvc.perform(get("/api/v1/system/users").param("pageNum", "1").param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
}

@Test
@WithMockUser(authorities = "system:user:list")
void shouldRejectUserDeleteWithoutDeleteAuthority() throws Exception {
    mockMvc.perform(delete("/api/v1/system/users/1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(1102));
}
```

为 19 个权限码建立参数化映射测试，确保每个 Controller 方法只使用设计中的精确权限码。

- [ ] **Step 2: 运行 Controller 测试确认失败**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-system -am -Dtest=UserControllerTest,RoleControllerTest,MenuControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，Controller 不存在。

- [ ] **Step 3: 实现用户 Controller**

逐一实现设计中的 9 个用户接口，使用 `@Valid`、`@Validated`、`@PreAuthorize` 和 `Result<T>`。路径 ID 使用 `long` 接收，响应 VO 使用字符串 ID。

```java
@GetMapping
@PreAuthorize("hasAuthority('system:user:list')")
public Result<PageResult<UserVO>> page(@Valid UserQueryDTO query) {
    return Result.success(userService.page(query));
}
```

- [ ] **Step 4: 实现角色 Controller**

逐一实现设计中的 8 个角色接口。`PUT /{id}/menus` 使用 `system:role:assign-menu`，不得复用 update 权限。

- [ ] **Step 5: 实现菜单只读 Controller**

```java
@GetMapping("/tree")
@PreAuthorize("hasAuthority('system:menu:tree')")
public Result<List<MenuTreeNodeVO>> tree() {
    return Result.success(menuService.getTree());
}
```

`GET /permissions` 使用 `system:permission:list`，第一阶段不创建菜单写接口。

- [ ] **Step 6: 运行 Controller 与 System 全量测试并提交**

Run: Task 4 Step 7 命令。

Expected: PASS，所有权限映射和响应断言通过。

```powershell
git add backend/platform-system
git commit -m "feat(system): 提供用户角色和菜单管理接口"
```

---

### Task 13: 配置、启动集成、文档与最终验证

**Files:**
- Modify: `backend/platform-boot/src/main/resources/application.yml`
- Create: `backend/platform-boot/src/test/resources/application-test.yml`
- Modify: `backend/platform-boot/src/test/java/com/xtong/saas/SaasPortalApplicationTest.java`
- Create: `backend/platform-boot/src/test/java/com/xtong/saas/SystemBootstrapIntegrationTest.java`
- Modify: `.env.example`
- Modify: `docker-compose.yaml`
- Modify: `README.md`

**Interfaces:**
- Consumes: 全部 Common/System Beans、Flyway migration、认证和 Bootstrap 配置属性。
- Produces: 可启动应用、测试环境、配置样例、部署传参和最终验收证据。

- [ ] **Step 1: 先写 Boot 集成测试和 test profile**

`application-test.yml` 使用：

```yaml
# 文件作用：为 Boot 集成测试提供隔离的 H2 数据库和安全配置。
spring:
  datasource:
    url: jdbc:h2:mem:saas_portal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  flyway:
    enabled: true
saas:
  auth:
    jwt-secret: test-only-secret-with-at-least-32-bytes
    access-token-ttl: 15m
    refresh-token-ttl: 7d
    login-failure-limit: 5
    login-failure-window: 15m
    login-lock-duration: 15m
  bootstrap:
    tenant-code: test
    tenant-name: Test Tenant
    admin-username: admin
    admin-password: TestPassword123
```

集成测试使用 `@SpringBootTest`、`@ActiveProfiles("test")`，断言 Flyway 四版迁移、首租户/管理员/内置角色存在，并重复调用初始化器不新增数据。

- [ ] **Step 2: 运行 Boot 测试确认配置尚未完整**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-boot -am test
```

Expected: FAIL，生产配置和 Bootstrap 属性尚未接入，或集成断言未满足。

- [ ] **Step 3: 补齐生产配置**

在 `application.yml` 增加带文件头说明的配置：

```yaml
saas:
  auth:
    jwt-secret: ${JWT_SECRET}
    access-token-ttl: ${ACCESS_TOKEN_TTL:15m}
    refresh-token-ttl: ${REFRESH_TOKEN_TTL:7d}
    login-failure-limit: ${LOGIN_FAILURE_LIMIT:5}
    login-failure-window: ${LOGIN_FAILURE_WINDOW:15m}
    login-lock-duration: ${LOGIN_LOCK_DURATION:15m}
  bootstrap:
    tenant-code: ${BOOTSTRAP_TENANT_CODE}
    tenant-name: ${BOOTSTRAP_TENANT_NAME}
    admin-username: ${BOOTSTRAP_ADMIN_USERNAME}
    admin-password: ${BOOTSTRAP_ADMIN_PASSWORD}
```

Flyway locations 明确为 `classpath:db/migration`，MyBatis 枚举和下划线映射显式配置。

所有本任务修改的既有 POM、YAML、`.env.example`、Compose、README 和 Java 文件都补充对应格式的文件职责说明；注释只描述文件用途，不记录密钥或环境值。

- [ ] **Step 4: 同步环境变量和 Compose**

`.env.example` 提供非生产示例并明确必须修改密码和密钥：

```dotenv
JWT_SECRET=change-this-to-a-random-secret-of-at-least-32-bytes
ACCESS_TOKEN_TTL=15m
REFRESH_TOKEN_TTL=7d
LOGIN_FAILURE_LIMIT=5
LOGIN_FAILURE_WINDOW=15m
LOGIN_LOCK_DURATION=15m
BOOTSTRAP_TENANT_CODE=default
BOOTSTRAP_TENANT_NAME=Default Tenant
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=change_me_now
```

Compose 后端 environment 逐项透传上述变量，保留现有可选 `REDIS_PASSWORD` 行为。不得把真实密钥写进 Dockerfile。

- [ ] **Step 5: 更新 README**

增加文件职责说明、数据库迁移说明、首次启动必填变量、认证接口、Bearer 使用方式、权限码规则、测试命令和 Docker 未验证限制。示例不得展示真实 Token 或密码。

- [ ] **Step 6: 运行后端全量测试**

Run:

```powershell
$env:JAVA_HOME='D:\develop\environment\Java\jdk-21.0.12'
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\Admin\AppData\Local\Temp\saas-portal-m2' -f backend\pom.xml test
```

Expected: Reactor 全部 SUCCESS，0 failures，0 errors。

- [ ] **Step 7: 运行生产打包**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\Admin\AppData\Local\Temp\saas-portal-m2' -f backend\pom.xml package
```

Expected: BUILD SUCCESS，生成 `backend/platform-boot/target/platform-boot-1.0.0-SNAPSHOT.jar`。

- [ ] **Step 8: 运行静态一致性检查**

Run:

```powershell
rg -n "TO[D]O|TB[D]|FIXM[E]|com\.xtong\.saasportal" backend .env.example docker-compose.yaml README.md
rg -L "文件作用|/\*\*" backend/platform-common/src backend/platform-system/src backend/platform-boot/src/main/resources .env.example docker-compose.yaml
```

Expected: 第一条无结果；第二条逐项人工复核，只允许 Java package 行位于 Javadoc 前、SQL/YAML/XML/Markdown 使用对应格式文件头，不允许缺少职责说明。

- [ ] **Step 9: 检查 Git diff 和敏感信息**

Run:

```powershell
git status --short
git diff --check
rg -n "password\s*=\s*[^$<{]|jwt-secret:\s*[^$]" backend .env.example docker-compose.yaml
```

Expected: 无 whitespace error；除测试专用值和 `.env.example` 明确占位值外，没有硬编码密码、JWT、Token 或数据库连接密钥。

- [ ] **Step 10: 提交配置与集成验证**

```powershell
git add backend/platform-boot backend/platform-system/pom.xml .env.example docker-compose.yaml README.md
git commit -m "feat(system): 完成 IAM 配置和启动集成"
```

- [ ] **Step 11: 记录 Docker 验证限制**

交付说明必须明确：本机未安装 Docker，因此未执行 `docker compose config`、镜像构建、MySQL/Redis 容器联调；不得将 Maven/H2 测试描述为 Docker 验证。
