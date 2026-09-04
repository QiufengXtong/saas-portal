<!-- 文件作用：记录 Common/System IAM Task 9 的 JWT、Refresh Token、Redis 会话及登录失败限制实现与验证证据。 -->

# Task 9 实施记录

## 范围

- 新增 `AuthProperties`，在构造与配置绑定阶段校验 JWT secret UTF-8 字节长度、正 TTL、Access/Refresh TTL 顺序及登录失败阈值。
- 新增 HS256 Access JWT 签发/验证、32 字节安全随机 Refresh Token 和小写 SHA-256 摘要服务；JWT 只携带租户、用户、会话、用户名及时间声明，不携带权限或 Refresh Token。
- 新增 Redis 会话协议：会话 JSON、Refresh 摘要索引、用户多设备 sessionId 集合、统一 TTL、`GETDEL` 单次消费、轮换、单会话及用户全部会话撤销。
- `RedisSessionStore` 同时实现 `SessionStore` 和 `SessionRevocationService`，并通过条件 Bean 测试确认正式实现覆盖 Task 7 fallback。
- 新增租户编码/用户名规范化的登录失败窗口与锁定限制；同一 `login-failure` Key 在达到阈值后切换到锁定 TTL。
- 注册 `AuthProperties`，并为现有 Boot 上下文测试补充显式测试专用认证配置，保持生产缺少/非法密钥时启动失败。

## 固定裁决落实

- Access JWT 使用 `NimbusJwtEncoder`/`NimbusJwtDecoder` 和 `MacAlgorithm.HS256`，TTL 取经校验的配置（测试固定为 15 分钟）；签名正确但身份 claim 缺失时统一抛 `JwtException`。
- Refresh Token 由 `SecureRandom` 生成 32 字节，URL-safe Base64 无 padding；Redis API 只接收其 64 位小写 SHA-256 摘要，测试枚举 Mock 调用参数确认明文从未作为 Key 或 Value 传入。
- Redis Key 精确使用 `saas:portal:auth:session:{sessionId}`、`refresh:{hash}`、`user-sessions:{tenantId}:{userId}` 和 `login-failure:{tenantCode}:{username}` 命名空间；会话由受 Spring 管理的 Jackson `ObjectMapper` 序列化。
- 用户会话 Set 只保存 sessionId，每次创建或轮换均同步 Refresh TTL；多个设备互不覆盖，全部撤销会删除各 session、各 refresh 摘要及用户集合。
- `consumeRefreshToken` 直接使用 `ValueOperations.getAndDelete`，旧摘要消费后不可再次取得，未依赖本机 Redis 或 Docker。
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
