<!-- 文件作用：记录 Common/System IAM Task 9 的 JWT、Refresh Token、Redis 会话及登录失败限制实现与验证证据。 -->

# Task 9 实施记录

## 范围

- 新增 `AuthProperties`，在构造与配置绑定阶段校验 JWT secret UTF-8 字节长度、正 TTL、Access/Refresh TTL 顺序及登录失败阈值。
- 新增 HS256 Access JWT 签发/验证、32 字节安全随机 Refresh Token 和小写 SHA-256 摘要服务；JWT 只携带租户、用户、会话、用户名及时间声明，不携带权限或 Refresh Token。
- 新增 Redis 会话协议：会话 JSON、Refresh 摘要索引、用户多设备 sessionId 集合、统一 TTL，以及 Lua 原子创建、轮换、单会话和用户全部会话撤销。
- `RedisSessionStore` 同时实现 `SessionStore` 和 `SessionRevocationService`，并通过条件 Bean 测试确认正式实现覆盖 Task 7 fallback。
- 新增租户编码/用户名规范化的登录失败窗口与锁定限制；身份分量使用长度前缀无歧义编码，失败计数与 TTL 由单个 Lua 脚本维护。
- 注册 `AuthProperties`，并为现有 Boot 上下文测试补充显式测试专用认证配置，保持生产缺少/非法密钥时启动失败。

## 固定裁决落实

- Access JWT 使用 `NimbusJwtEncoder`/`NimbusJwtDecoder` 和 `MacAlgorithm.HS256`，TTL 取经校验的配置（测试固定为 15 分钟）；签名正确但身份 claim 缺失时统一抛 `JwtException`。
- Refresh Token 由 `SecureRandom` 生成 32 字节，URL-safe Base64 无 padding；Redis API 只接收其 64 位小写 SHA-256 摘要，测试枚举 Mock 调用参数确认明文从未作为 Key 或 Value 传入。
- Redis Key 使用 `saas:portal:auth:session:{sessionId}`、`refresh:{hash}`、`user-sessions:{tenantId}:{userId}` 命名空间；登录失败 Key 的两个规范化身份分量分别增加长度前缀，避免分量内冒号产生拼接碰撞；会话由受 Spring 管理的 Jackson `ObjectMapper` 序列化。
- 用户会话 Set 只保存 sessionId，每次创建或轮换均同步 Refresh TTL；多个设备互不覆盖，全部撤销会删除各 session、各 refresh 摘要及用户集合。
- `SessionStore.rotateRefreshToken` 在单个 Redis Lua 脚本内校验并消费旧摘要、确认会话仍存在、写入新摘要及 TTL、更新会话和用户索引；接口不再暴露可拆开调用的 consume/replace 操作。
- 所有新增类型、测试及修改文件均带职责说明；实现未新增日志，未记录 Token、密码或密钥值。

## TDD 记录

### RED

1. 先新增三组 Task 9 测试，聚焦命令在 `testCompile` 因 `AuthProperties`、Token、Session 与登录限制类型不存在而失败，符合预期。
2. 新增“签名正确但缺少必填 claim 必须抛 `JwtException`”测试，实际先以 `NullPointerException` 失败，证明测试覆盖过滤器统一 401 所需边界。
3. 后端 reactor 首次运行在现有 Boot 上下文因 `AuthProperties` 未注册失败；注册后继续因测试未提供必填安全配置失败，明确验证启动期校验生效。

### GREEN

1. 完成最小 Token、Redis 会话、登录失败限制与错误码实现后，Task 9 聚焦测试通过。
2. 必填 claim 改为显式类型/非空校验并抛 `BadJwtException` 后，JWT 测试通过。
3. 显式注册配置属性、在 Boot 测试中提供测试专用值，并标记生产 JWT 构造器后，Boot 上下文测试通过。

## 最终验证

使用 JDK 21、Maven 3.9.1、worktree 仓库 `.superpowers/sdd/maven-repository`：

- `-pl platform-system -am -Dtest=JwtAccessTokenServiceTest,RedisSessionStoreTest,RedisLoginFailureServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：20/20 通过。
- `-pl platform-system -am test`：Common 16/16、System 74/74 通过。
- 后端 reactor `test`：Common 16/16、System 74/74、Boot 1/1 通过，Business 无测试；以 `BUILD SUCCESS` 结束。

## 已知环境噪声

- Maven 全局 `settings.xml` 仍报告 `servers` 节点位置警告；Mockito/JDK 仍报告动态 agent 的未来兼容性警告，均为基线已有且不影响测试结果。
- Redis 行为按设计使用 Mock 验证；未声明或执行真实 Redis/Docker 联调。

## 复审修复追加记录

### 原子会话状态机

- `create` 改为返回碰撞结果的原子操作；Lua 同时检查 sessionId 与 Refresh 摘要，使用 `SET ... NX PX` 写入并同步用户 Set，任何碰撞均不覆盖既有身份。
- 新增服务端 `SessionIdGenerator`，使用 `SecureRandom` 生成 32 字节、Base64URL 无 padding 的会话 ID；Task 10 登录流程应注入此生成器，并在极低概率 `create=false` 时重新生成整组会话凭据。
- 删除可被调用方拆开的 `consumeRefreshToken`/`replaceRefreshToken`，改为返回轮换后会话或空结果的 `rotateRefreshToken`。同一 Lua 脚本校验旧摘要映射、会话存在性与会话当前摘要，拒绝新摘要碰撞，写入新状态后删除旧摘要；撤销与轮换在 Redis 脚本执行序列上具有单一线性化点。
- `delete`、`deleteAll` 也改为 Lua 原子操作，一次维护 session、当前 Refresh 摘要和用户 sessionId Set；因此 create/rotate/delete/deleteAll 不再以多个客户端命令暴露中间不一致状态。
- Redis JSON 内将 tenantId/userId 保存为十进制字符串，并将权限集合保存为布尔对象键，原因是避免 Lua JSON 往返时损失 64 位 ID 精度或把空数组重编码为空对象；领域层仍返回 `long` 与不可变 `Set<String>`。

### 原子登录失败计数

- `recordFailure` 只执行一次 Lua：`INCR` 后读取 `PTTL`，首次计数设置统计窗口，已有但无 TTL 的计数恢复统计窗口，达到或超过阈值后设置锁定 TTL。
- 登录失败 Key 对规范化后的每个分量独立做 `长度:内容` 编码；测试明确覆盖 `a:b`/`c` 与 `a`/`b:c` 两组身份不会映射到同一 Key。

### 配置与 TDD 证据

- `AuthProperties` 为 Access/Refresh TTL 增加绑定默认值 `15m`/`7d`，保留正值、顺序和密钥长度校验；测试在省略两项配置时确认默认值成功绑定。
- RED：复审测试最初在 `testCompile` 因旧 `void create`、缺少原子 `rotateRefreshToken` 与 `SessionIdGenerator` 而失败；随后实现新契约。首次 GREEN 尝试有两条 Lua 文本断言因括号格式过度精确失败，核对实际脚本后只收紧测试断言，生产语义未因该失败调整。
- Mock 测试验证每类状态迁移只调用一次 `StringRedisTemplate.execute`、完整 KEYS/ARGV/TTL 契约、脚本返回语义、旧摘要删除命令、无 TTL 修复分支和撤销/轮换调用顺序。按设计未在本机伪称真实 Redis 集成验证；建议后续在具备 Redis/Docker 的环境补充同脚本并发集成测试。

### 复审最终验证

使用 JDK 21、Maven 3.9.1 和 worktree `.superpowers/sdd/maven-repository`，从本次最终代码执行：

- 聚焦：`-Dtest=JwtAccessTokenServiceTest,RedisSessionStoreTest,RedisLoginFailureServiceTest -Dsurefire.failIfNoSpecifiedTests=false`，27/27 通过。
- System：Common 16/16、System 81/81 通过，`BUILD SUCCESS`。
- 后端全 reactor：Common 16/16、System 81/81、Boot 1/1 通过，Business 无测试，`BUILD SUCCESS`。

## 毫秒 TTL 边界复审追加记录

- `AuthProperties` 的 Access、Refresh、登录失败窗口和锁定期在构造/绑定边界统一要求 `Duration.toMillis() >= 1`，并拒绝无法安全转换为毫秒的溢出值；原有 `15m`/`7d` 默认值与跨字段顺序校验保持不变。
- `RedisSessionStore` 在 create/rotate 执行 Lua 前重新校验 Session TTL，`RedisLoginFailureService` 在组装 KEYS/ARGV 前重新校验窗口和锁定 TTL；因此亚毫秒 Duration 不会被截断为 `0` 后传给 `PEXPIRE`。
- RED：新增 1ns 测试后，配置构造未抛异常、Session 创建向 Mock Redis 发送了 `0`、登录失败路径执行 Lua 后才以空结果失败，共 3 条测试按预期失败。
- GREEN：按毫秒下限实现构造与命令边界校验后，聚焦测试 30/30 通过。
- Common+System 全测：Common 16/16、System 84/84 通过，`BUILD SUCCESS`。
- 后端全 reactor：Common 16/16、System 84/84、Boot 1/1 通过，Business 无测试，`BUILD SUCCESS`。
- 当前 `docker-compose.yaml` 与应用配置为单节点 Redis，本期明确不支持 Redis Cluster；没有为跨命名空间 Lua Key 重构 hash slot。此部署限制同时记录在根 README，后续若引入 Cluster，需专项设计 Redis hash tag 与迁移兼容方案。
