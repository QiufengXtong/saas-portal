<!-- 文件作用：记录 Common/System IAM Task 8 角色管理与菜单授权领域服务的实现、TDD 证据和验证结果。 -->

# Task 8 实施记录

## 范围

- 新增当前受信租户内角色的分页、详情、创建、更新、启停、删除与菜单整体授权服务、DTO/VO 和稳定错误码。
- 扩展角色、用户角色、角色菜单和全局菜单 Mapper，支持编码冲突检查、关联用户检查、受影响用户查询、全局有效菜单校验，以及角色菜单的物理批量替换。
- 新增 `RoleServiceTest`，覆盖角色关联限制、内置角色保护、菜单边界、事务提交/回滚的会话撤销与异常 Mapper 返回时的租户边界保护。

## 固定裁决落实

- 角色服务只通过 `TenantContextHolder.requireTenantId()` 取得租户；`requireRole` 同时以租户条件查询并复核实体 tenantId，避免 Mapper 异常结果绕过租户边界。
- `TENANT_ADMIN`（即使数据的 `builtIn` 标记异常）和任意 `builtIn=true` 角色均不能停用、删除或重新授权；角色编码仅在创建命令中出现，更新命令不能改变编码。
- 删除角色前按当前租户检查 `sys_user_role`；有关联用户时返回 `ROLE_IN_USE`，不执行逻辑删除。
- 菜单授权仅接受未删除、启用的全局菜单。旧 `sys_role_menu` 关系物理删除后在同一事务批量插入新关系，每条关联使用独立雪花主键。
- 角色停用或菜单权限变化会收集当前租户受影响用户；事务同步可用时仅 `afterCommit` 批量撤销其全部会话，回滚不撤销；复用 Task 7 的 `SessionRevocationService` 端口。

## TDD 记录

### RED

1. 先新增 `RoleServiceTest`，执行 `-Dtest=RoleServiceTest` 后在 test compile 阶段因 `RoleService`、角色错误码及实现类不存在而失败，符合预期。
2. 先新增“Mapper 返回其他租户角色也必须拒绝”的边界测试；聚焦测试以“预期抛出异常但未抛出”失败，证明该测试可捕捉跨租户泄露。

### GREEN

1. 最小实现服务、DTO/VO、错误码和 Mapper 后，`RoleServiceTest` 6/6 通过。
2. 在 `requireRole` 增加实体 tenantId 复核后，边界测试通过，聚焦测试 7/7 通过。

## 验证

使用 JDK 21、Maven 3.9.1、仓库 `.superpowers/sdd/maven-repository`：

- `-pl platform-system -am -Dtest=RoleServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：7/7 通过。
- `-pl platform-system -am test`：Common 16/16、System 44/44 通过。
- 后端 reactor `test`：Common 16/16、System 44/44、Boot 测试通过；Business 模块无测试，命令以 `BUILD SUCCESS` 结束。
- `git diff --check`：无空白错误。

## 注意事项

- Maven 全局 `settings.xml` 仍报告 `servers` 节点位置警告，Mockito 在 JDK 21 仍报告动态附加 agent 的未来兼容性警告；所有测试均以成功结束。

## 复审修复（Changes requested）

### 修复内容

- `RoleService.delete`、`assignMenus`、`enable` 和 `disable` 现在均在读取角色、检查关联或修改状态之前调用 Task 7 已使用的 `SystemTenantMapper.lockByIdForAdminInvariant(tenantId)`。该查询以当前 `sys_tenant` 行的 `FOR UPDATE` 锁串行化同一租户的用户角色分配、角色删除、菜单整体授权及状态敏感变更，不引入新的锁协议。
- 新增 delete 与菜单整体授权的 `InOrder` 回归测试，验证租户锁先于角色读取、关联检查及关联物理替换。
- 新增启用、停用状态变化的提交后撤销测试，以及启用、停用回滚不撤销和无状态变化不撤销测试；受影响用户集合仅在 `afterCommit` 调用 Task 7 会话撤销端口。

### 复审 TDD 记录

1. **RED**：先将租户锁依赖和顺序/会话测试写入 `RoleServiceTest`；聚焦构建在 test compile 阶段因 `RoleServiceImpl` 尚未接收 `SystemTenantMapper` 失败，符合预期。
2. **GREEN**：注入并复用 Task 7 的锁 Mapper，且将锁前置到 delete、assignMenus 与状态变更路径；`RoleServiceTest` 14/14 通过。

### 复审验证

- `-pl platform-system -am -Dtest=RoleServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：14/14 通过。
- `-pl platform-system -am test`：Common 16/16、System 51/51 通过。
- 后端 reactor `test`：以 `BUILD SUCCESS` 结束；Common 16/16、System 51/51，Business 无测试，Boot 测试通过。
