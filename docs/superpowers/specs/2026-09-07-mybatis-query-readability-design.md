<!-- 文件作用：定义 System 模块数据库查询可读性重构的边界、目录结构和验收标准。 -->

# MyBatis 查询可读性重构设计

## 目标

统一 `platform-system` 的数据库访问方式，使简单单表操作直接通过 MyBatis-Plus Lambda Wrapper 表达，使复杂 SQL 集中保存在 Mapper XML 中，消除 SQL 注解、SQL Provider 与业务代码混杂造成的阅读成本。

本次只重构持久层表达方式，不改变 HTTP 接口、业务规则、事务范围、租户隔离、锁顺序、认证状态机或数据库结构。

## 当前问题

System 模块目前同时存在以下数据库访问方式：

- Service 中的 MyBatis-Plus Wrapper；
- Mapper 接口上的 `@Select`、`@Update`、`@Delete`；
- Mapper 内部的 `@InsertProvider` SQL Provider；
- Mapper 接口中的文本块动态 SQL。

同一模块使用多种表达方式，会增加定位 SQL、理解参数绑定和判断租户边界的成本。

## 设计原则

### 简单 SQL 使用 Lambda Wrapper

满足以下条件的操作直接在 Service 中调用 `BaseMapper`：

- 单表查询、分页或计数；
- 条件由实体字段直接组成；
- 普通插入、按 ID 更新或删除；
- 不依赖数据库锁、关联查询、聚合子查询或原子表达式。

统一优先使用下面的形式，避免字符串列名：

```java
userMapper.selectOne(Wrappers.<SystemUser>query().lambda()
        .eq(SystemUser::getUsername, username));
```

Service 中的 Wrapper 应按业务语义就近构造，不为单次查询额外创建无复用价值的 Mapper 方法。

### 复杂 SQL 使用 Mapper XML

出现下列任一特征时，SQL 保留为 Mapper 自定义方法，并迁移到 XML：

- 多表 `JOIN`、`EXISTS`、子查询或聚合；
- `FOR UPDATE` 等数据库锁语义；
- 批量插入、批量更新或动态集合条件；
- `auth_version = auth_version + 1` 等原子字段表达式；
- 需要精确控制关联表物理删除或审计字段；
- 使用 Wrapper 会显著降低 SQL 意图的可读性。

Mapper Java 接口只保留方法签名和 Javadoc，不再包含 SQL 注解、文本块 SQL 或 SQL Provider 内部类。

## XML 目录结构

XML 统一放在 `platform-system` 资源目录：

```text
backend/platform-system/src/main/resources/mapper/system/
├── bootstrap/SystemBootstrapLockMapper.xml
├── menu/SystemMenuMapper.xml
├── role/SystemRoleMapper.xml
├── role/SystemRoleMenuMapper.xml
├── role/SystemUserRoleMapper.xml
├── tenant/SystemTenantMapper.xml
└── user/SystemUserMapper.xml
```

每个 XML 文件必须满足：

- `namespace` 与 Mapper 接口全限定名完全一致；
- 文件头注释说明文件职责；
- 每个 SQL 语句前有简短注释说明业务用途；
- 参数名与 Mapper 的 `@Param` 名称一致；
- 复用明确的 result type，不创建无必要的 result map；
- 不把租户 ID、状态值或审计用户写成硬编码业务常量。

应用显式配置：

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
```

## Mapper 迁移范围

本次覆盖 System 模块全部七个 Mapper：

- `SystemBootstrapLockMapper`
- `SystemMenuMapper`
- `SystemRoleMapper`
- `SystemRoleMenuMapper`
- `SystemUserRoleMapper`
- `SystemTenantMapper`
- `SystemUserMapper`

迁移完成后，`platform-system/src/main/java` 中不得再出现 MyBatis SQL 注解、`<script>` 文本块或 SQL Provider。

## 行为与安全约束

重构必须保持以下行为不变：

- `sys_tenant`、`sys_menu`、`sys_bootstrap_lock` 继续作为全局表；
- 其他 System 表继续接受租户拦截器保护；
- 租户拦截器仍先于分页拦截器；
- 用户与角色敏感写操作继续遵守租户行 `FOR UPDATE` 锁顺序；
- `auth_version` 必须使用数据库原子递增；
- 实体表逻辑删除、关联表物理删除语义不变；
- 关联表批量写入继续保存真实审计用户和审计时间；
- Bootstrap 锁、权限加载、管理员不变量和会话撤销流程不变。

## 测试策略

### 静态边界检查

- 扫描 System Java 源码，确保不存在 `@Select`、`@Insert`、`@Update`、`@Delete`、`@*Provider` 和 SQL `<script>`；
- 检查七个 Mapper 均有对应 XML；
- 检查 XML namespace 和 Mapper 接口一致；
- 检查新增或修改文件均有职责注释。

### Mapper 集成测试

使用 H2 MySQL 模式和 Flyway V1–V4：

- 验证所有 Mapper XML 能被 Spring/MyBatis 加载；
- 验证复杂查询参数绑定和返回类型；
- 验证 Bootstrap `FOR UPDATE` 与租户锁查询；
- 验证角色权限关联查询、批量关联写入和物理删除；
- 验证 `auth_version` 原子递增；
- 验证租户拦截器仍对租户表生效且跳过全局表。

### 回归验证

- 运行 Common/System 聚焦测试；
- 运行后端全量测试；
- 执行 Maven 打包；
- 执行 `git diff --check` 和旧 SQL 注解扫描；
- 本机无 Docker，不执行 Docker、真实 MySQL 或真实 Redis 验证，也不将 H2 测试描述为容器验证。

## 验收标准

- 简单数据库操作均由 Service 使用 Lambda Wrapper 表达；
- 复杂 SQL 全部位于约定的 Mapper XML；
- Mapper Java 接口不包含 SQL 实现；
- 七个 Mapper XML 能正确加载并通过集成测试；
- 原有业务、安全、租户与审计测试全部通过；
- 后端全量测试和打包成功；
- 所有新增、修改文件包含职责注释；
- Controller 注释的现有未提交修改不被本设计提交夹带。
