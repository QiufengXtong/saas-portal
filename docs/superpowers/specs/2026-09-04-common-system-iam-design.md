<!-- 文件作用：定义 platform-common 与 platform-system 第一阶段 IAM 能力的架构、数据、接口、安全及验收设计。 -->

# Common 与 System IAM 第一阶段设计

## 1. 背景与目标

项目骨架已经完成，下一阶段建设 `platform-common` 通用技术基础，以及 `platform-system` 的基础多租户 IAM 闭环。

本阶段目标：

- 完善统一响应、分页、异常、基础实体和通用 MyBatis/Web 能力；
- 建立基础租户、用户、角色、全局菜单和权限码模型；
- 实现登录、令牌刷新、退出、当前用户查询和 Redis 会话管理；
- 实现租户内用户、角色及角色授权管理接口；
- 使用 Flyway 管理系统表和全局权限资源；
- 使用环境变量安全引导初始化首个租户管理员；
- 为下一阶段前端登录和管理页面提供稳定接口契约。

本阶段不包含前端页面、组织、字典、文件、通知、套餐、复杂租户运营、数据权限、登录日志和操作日志。

## 2. 技术方案

采用 Spring Security 原生方案：

- Spring Security 负责认证上下文、请求过滤和方法级授权；
- Access Token 使用 HMAC-SHA256 JWT；
- Refresh Token 使用高强度随机值；
- Redis 保存登录会话、权限集合、Refresh Token 摘要和登录失败状态；
- MyBatis-Plus 负责数据访问、分页、雪花 ID、逻辑删除和租户 SQL 拦截；
- Flyway 负责数据库版本迁移；
- 密码使用 BCrypt；
- 测试数据库使用 H2 MySQL 兼容模式。

不引入 Sa-Token、Spring Authorization Server、OAuth2 授权服务器或额外安全框架。

## 3. 模块边界

### 3.1 platform-common

`platform-common` 只包含业务无关的技术能力：

```text
com.xtong.saas.common
├── result
│   ├── Result
│   └── PageResult
├── exception
│   ├── ErrorCode
│   ├── CommonErrorCode
│   ├── BusinessException
│   └── GlobalExceptionHandler
├── model
│   └── BaseEntity
├── mybatis
│   ├── MyBatisConfig
│   ├── AuditMetaObjectHandler
│   └── AuditorProvider
└── web
    └── 通用 Web 异常与序列化配置
```

`ErrorCode` 是错误码契约接口。`platform-common` 只定义通用错误码，认证、租户、用户、角色和菜单错误码由 `platform-system` 定义。

`platform-common` 不保存租户、用户或权限上下文，不依赖 `platform-system` 或 `platform-business`。

### 3.2 platform-system

`platform-system` 按领域组织：

```text
com.xtong.saas.system
├── auth
├── tenant
├── user
├── role
├── menu
└── bootstrap
```

每个领域仅按实际代码需要建立 `api`、`controller`、`service`、`service.impl`、`mapper`、`entity`、`dto`、`vo`、`enums`、`config` 或 `handler`，不创建空目录。

`platform-system` 对后续业务模块公开当前用户、当前租户和权限判断 API。业务模块不得直接读取系统模块的 Mapper、Entity、Redis Key 或内部实现。

### 3.3 platform-boot

`platform-boot` 保持唯一启动和装配模块，不承载用户、角色或认证业务逻辑。最终仍只生成一个可执行 Spring Boot JAR。

## 4. 文件注释规则

本阶段所有新增或修改文件都必须包含职责说明：

- Java 类、接口、枚举、Record 使用类型级 Javadoc，说明职责、边界和用途；
- SQL 文件头说明迁移版本和数据作用；
- YAML、XML、Docker 或其他配置使用对应格式注释说明配置目的；
- Markdown 文件使用文件头注释说明用途；
- 不为显而易见的 getter、构造器和简单语句添加重复注释。

## 5. Common 契约

### 5.1 统一响应

`Result<T>` 字段：

```text
code
message
data
```

成功码固定为 `0`，提供成功和失败工厂方法。Actuator 健康端点保持原生响应，不包裹 `Result<T>`。

`PageResult<T>` 字段：

```text
records
total
pageNum
pageSize
```

分页请求统一使用 `pageNum`、`pageSize`。页码最小为 1，页大小设置合理上限，越界时返回参数错误。

### 5.2 错误码与异常

错误码分段：

```text
0       成功
1000+   通用参数和系统错误
1100+   认证与会话错误
1200+   租户错误
1300+   用户错误
1400+   角色错误
1500+   菜单与权限错误
```

`BusinessException` 持有 `ErrorCode`，可携带安全的覆盖消息，但不得包含敏感配置或内部异常详情。

`GlobalExceptionHandler` 处理业务异常、Jakarta Validation、JSON 格式、请求参数和未预期异常。未预期异常记录服务端日志，对外只返回通用错误信息。Spring Security 过滤器中的 401 和 403 由专用处理器输出相同 `Result<Void>` 格式。

### 5.3 基础实体与审计

`BaseEntity` 包含：

```text
id          Long
createdBy   Long
createdAt   LocalDateTime
updatedBy   Long
updatedAt   LocalDateTime
deleted     Boolean
```

主键使用 MyBatis-Plus 雪花 ID，数据库字段使用 `BIGINT`。实体不直接作为 HTTP 响应；VO 中的 ID 使用字符串，避免 JavaScript 数字精度丢失。

创建和更新时间由 MyBatis-Plus 自动填充。审计用户通过 `AuditorProvider` 接口获取，`platform-system/auth` 提供基于认证上下文的实现，匿名或初始化场景使用明确的系统审计值。

## 6. 数据模型

### 6.1 核心表

第一阶段建立：

```text
sys_tenant
sys_user
sys_role
sys_menu
sys_user_role
sys_role_menu
```

`sys_tenant` 保存租户编码、名称和状态。租户编码全局唯一。

`sys_user` 保存 `tenant_id`、用户名、密码摘要、显示名称、联系方式、状态、密码修改时间、最后登录时间和认证安全版本。`tenant_id + username` 唯一；用户名更新时必须复用统一身份规范化规则，并递增认证安全版本。

`sys_role` 保存 `tenant_id`、角色编码、名称、状态和内置标记。`tenant_id + role_code` 唯一，角色编码创建后不可修改。

`sys_menu` 是全平台共享的权限资源，不带 `tenant_id`。类型包括目录、菜单和按钮，按需保存父节点、名称、路由、组件、图标、权限码、排序、可见性和状态。

`sys_user_role` 和 `sys_role_menu` 均包含 `tenant_id`，并对关联组合建立唯一约束。写入关联关系前必须校验两端数据属于当前租户。

所有实体表包含主键、审计和逻辑删除字段。Flyway 脚本必须明确字段长度、NULL 语义、默认值、唯一约束、查询索引以及表字段注释。

### 6.2 租户隔离

MyBatis-Plus 租户拦截器自动为租户表追加 `tenant_id` 条件。`sys_tenant` 和全局 `sys_menu` 不参与租户拦截。

登录时先通过租户编码查询租户，再在受控租户上下文中查询用户。认证后的租户 ID 只能从认证上下文取得，业务接口不接受可改变当前租户的参数或请求头。

请求过滤器必须在 `finally` 中清理线程租户上下文。Service 仍需校验跨表关联的租户归属，不能只依赖 SQL 拦截器。

## 7. Flyway 与初始化

迁移文件：

```text
V1__init_system_schema.sql
V2__init_system_permissions.sql
V3__add_bootstrap_lock.sql
V4__add_auth_version_and_role_lookup_index.sql
```

V1 创建系统表、索引和约束；V2 初始化全局菜单、按钮和权限码，不写入默认密码；V3 创建多实例首次初始化锁；V4 增加用户认证安全版本 `auth_version` 和 `idx_user_role_role(tenant_id, role_id, user_id)` 角色反向查询索引。

首次启动初始化器在 Flyway 完成后执行：

1. 检查 `sys_tenant` 是否为空；
2. 为空时校验所有引导环境变量；
3. 创建初始租户；
4. 创建内置 `TENANT_ADMIN` 角色；
5. 使用 BCrypt 处理管理员密码并创建管理员；
6. 建立管理员与内置角色关联；
7. 整体在事务中完成；
8. 非空时不重复初始化。

缺少必要配置时应用启动失败并指出缺失配置名称，但不得记录管理员密码或 JWT 密钥。

## 8. 认证与会话

### 8.1 登录

```text
POST /api/v1/auth/login
```

登录参数为 `tenantCode + username + password`。身份规范化后先做一次失败限制预检；查到有效租户候选后取得租户行 `FOR UPDATE` 锁，并在锁内再次检查失败限制，再校验租户、用户状态、密码和权限。失败计数也在该锁区间内写入，因此同一已知租户的并发尝试达到阈值后，排队请求不会继续查询用户或执行 BCrypt。未知租户仍执行前置限制检查、等价密码工作和失败计数，并统一返回无效凭据。

同一用户允许多设备登录，每次登录生成独立 `sessionId`，支持单会话退出和用户全部会话失效。

### 8.2 Token

Access Token：

- 使用 HMAC-SHA256 JWT；
- 默认有效期 `15m`；
- 包含 `userId`、`tenantId`、`sessionId`、`username`、签发和过期时间；
- 不包含密码、Refresh Token 或权限列表。

Refresh Token：

- 使用安全随机值；
- 默认有效期 `7d`；
- Redis 只保存 SHA-256 摘要；
- 每次刷新后立即轮换，旧令牌只能使用一次。

JWT 签名密钥来自环境变量，UTF-8 编码后不得少于 32 字节。密码使用 BCrypt，默认 strength 为 12；密码至少 8 个字符，UTF-8 编码后不得超过 72 字节。

### 8.3 Redis Key

```text
saas:portal:auth:session:{sessionId}
saas:portal:auth:refresh:{refreshTokenHash}
saas:portal:auth:v2:user-sessions:{tenantId}:{userId}
saas:portal:auth:login-failure:{tenantCode}:{username}
```

Access Token 校验通过后，还必须确认 Redis 会话存在，并从会话加载当前权限集合；随后在租户上下文中校验数据库用户启用、未删除且 `auth_version` 与会话一致。Refresh 先只读定位会话，取得租户行锁并完成相同状态/版本校验后，才用 Lua 原子轮换摘要。注销、用户禁用、密码重置或角色权限变化时删除对应会话；即使 Redis 清理失败，事务内递增的 `auth_version` 仍会使旧 Access/Refresh Token 失效。

用户会话索引为 ZSET，成员是 sessionId、score 是绝对过期 epoch millis。Lua 通过 Redis `TIME` 的秒和微秒计算当前毫秒，Java 只传相对 Refresh TTL，避免应用节点时钟漂移；当前 epoch 毫秒处于 Lua 双精度整数安全范围。每个 Lua 操作先裁剪过期成员，并以清理后最大 score 设置 `PEXPIREAT`，避免缩短新配置 TTL 时提前删除仍有效的旧成员。滚动升级不读取或改写旧 `saas:portal:auth:user-sessions:*` SET；旧 session/refresh 仍可校验，旧会话缺失的安全版本按 `0` 兼容，旧 Refresh 轮换成功后进入 v2 索引。

登录连续失败默认达到 5 次后锁定 15 分钟，失败统计窗口为 15 分钟。成功登录后清除失败状态。

### 8.4 认证接口

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/auth/me
```

登录、刷新和健康检查允许匿名访问，其余 `/api/v1/**` 默认需要认证。未认证返回 HTTP 401，权限不足返回 HTTP 403。

## 9. RBAC

用户通过角色获得权限，普通角色通过 `sys_role_menu` 关联菜单和按钮。按钮节点的 `permission_code` 是后端授权和后续前端按钮控制的统一语义。

后端使用方法级权限校验，例如：

```java
@PreAuthorize("hasAuthority('system:user:list')")
```

内置租户管理员角色编码为 `TENANT_ADMIN`：

- 自动拥有所有有效权限码；
- 不允许删除、停用或修改编码；
- 每个租户至少保留一个有效租户管理员；
- 不通过角色菜单接口重新授权。

菜单和权限码由 Flyway 管理。租户管理员可以读取菜单树并为普通角色授权，但第一阶段不能增删改全局菜单。

第一阶段权限码固定为：

```text
system:user:list
system:user:detail
system:user:create
system:user:update
system:user:enable
system:user:disable
system:user:reset-password
system:user:delete
system:user:assign-role

system:role:list
system:role:detail
system:role:create
system:role:update
system:role:enable
system:role:disable
system:role:delete
system:role:assign-menu

system:menu:tree
system:permission:list
```

认证用户访问自己的 `/api/v1/auth/me` 不需要额外权限码。每个管理接口只能使用上面与操作语义对应的权限码，不使用模糊的通配权限替代。

## 10. HTTP API

### 10.1 用户

```text
GET    /api/v1/system/users
GET    /api/v1/system/users/{id}
POST   /api/v1/system/users
PUT    /api/v1/system/users/{id}
POST   /api/v1/system/users/{id}/enable
POST   /api/v1/system/users/{id}/disable
POST   /api/v1/system/users/{id}/reset-password
DELETE /api/v1/system/users/{id}
PUT    /api/v1/system/users/{id}/roles
```

用户查询始终限定当前租户。用户名更新后立即使旧会话失效；不能停用或删除当前用户；删除使用逻辑删除并撤销会话；重置密码后撤销目标用户全部会话；用户角色只能来自当前租户。

### 10.2 角色

```text
GET    /api/v1/system/roles
GET    /api/v1/system/roles/{id}
POST   /api/v1/system/roles
PUT    /api/v1/system/roles/{id}
POST   /api/v1/system/roles/{id}/enable
POST   /api/v1/system/roles/{id}/disable
DELETE /api/v1/system/roles/{id}
PUT    /api/v1/system/roles/{id}/menus
```

角色查询始终限定当前租户。角色编码不可修改；有关联用户的角色不可删除；内置角色不可删除、停用或重新授权；角色菜单授权在单个事务中整体替换。授权变化后撤销受影响用户的全部会话。

### 10.3 菜单与权限

```text
GET /api/v1/system/menus/tree
GET /api/v1/system/menus/permissions
```

第一阶段菜单接口只读。所有雪花 ID 在 HTTP JSON 中使用字符串表达。

## 11. 配置

新增并同步到后端配置、`.env.example` 和 `docker-compose.yaml`：

```text
JWT_SECRET
ACCESS_TOKEN_TTL=15m
REFRESH_TOKEN_TTL=7d
LOGIN_FAILURE_LIMIT=5
LOGIN_FAILURE_WINDOW=15m
LOGIN_LOCK_DURATION=15m
BOOTSTRAP_TENANT_CODE
BOOTSTRAP_TENANT_NAME
BOOTSTRAP_ADMIN_USERNAME
BOOTSTRAP_ADMIN_PASSWORD
```

Redis 是否启用密码继续由现有 `REDIS_PASSWORD` 环境变量控制。

## 12. 测试设计

### 12.1 platform-common

- `Result` 和 `PageResult` 工厂方法及边界；
- 错误码和 `BusinessException`；
- 全局异常响应；
- 分页参数校验。

### 12.2 platform-system

- 登录成功以及租户、用户、密码、状态异常；
- JWT 签发、验证和过期；
- Refresh Token 轮换和旧令牌失效；
- 多设备独立会话；
- 退出和用户全部会话失效；
- 登录失败限制；
- 用户、角色和授权业务规则；
- 内置角色保护；
- 401、403 和权限码校验；
- 跨租户查询和关联写入隔离。

### 12.3 platform-boot

- Spring Context 启动；
- H2 MySQL 兼容模式执行 Flyway 迁移；
- 首次管理员初始化；
- 初始化器重复执行幂等性。

Redis 相关 Service 使用 Mock 完成单元测试。真实 Redis 和 MySQL 容器联调留给具备 Docker 的环境，不把本机无 Docker 误报为容器验证成功。

## 13. 验收标准

1. Maven 全模块编译、测试和打包通过。
2. Flyway 能从空数据库创建系统表、约束、索引和权限数据。
3. 初始管理员能通过租户编码、用户名和密码登录。
4. Access Token 能访问授权接口，无令牌返回 401，无权限返回 403。
5. Refresh Token 只能使用一次，刷新后旧令牌失效。
6. 注销后对应会话的 Access Token 立即失效。
7. 同一用户可以保留多个独立会话。
8. 用户和角色不能查询或关联其他租户数据。
9. 用户、角色及授权接口遵守状态、内置角色和关联限制。
10. 所有新增和修改文件均包含文件职责注释。
11. `.env.example`、Docker Compose 和后端配置变量保持一致。
12. 不包含本阶段明确排除的前端和其他系统能力。
