<!-- 文件作用：说明 saas-portal 的工程结构、本地开发、部署方式与当前运行限制。 -->

# saas-portal

`saas-portal` 是一个前后端分离的 SaaS 平台。当前已提供多租户 System IAM 管理闭环，包括首租户初始化、登录与会话刷新、权限路由、用户与角色管理、菜单与按钮权限维护、角色授权及 Redis 会话即时失效。

## 技术栈

后端：

- Java 21
- Spring Boot 4.1.1
- Maven 多模块
- MyBatis-Plus 3.5.17
- MySQL、Redis

前端：

- Vue 3 + TypeScript
- Vite
- Vue Router、Pinia、Axios
- Element Plus

## 项目结构

```text
saas-portal/
├── backend/
│   ├── platform-boot/       # 唯一启动模块和可执行 JAR
│   ├── platform-common/     # 通用基础能力
│   ├── platform-system/     # 平台能力边界
│   └── platform-business/   # 核心业务边界
├── frontend/                # Vue 3 前端
├── docs/                    # 设计和实施文档
└── docker-compose.yaml      # 统一 Compose 入口
```

后端依赖方向：

```text
platform-boot
├── platform-system   -> platform-common
└── platform-business -> platform-common
```

最终只有 `platform-boot` 打包为可执行 Spring Boot JAR。

## 本地环境

当前开发机已确认：

```text
JDK    D:\develop\environment\Java\jdk-21.0.12
Maven  D:\develop\environment\apache\maven\apache-maven-3.9.1
NVM    D:\develop\environment\nvm
Node   D:\develop\environment\nodejs（当前 24.19.0）
npm    11.17.0
```

## 后端开发

在项目根目录执行：

```powershell
$env:JAVA_HOME = 'D:\develop\environment\Java\jdk-21.0.12'
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml test
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml package
& 'D:\develop\environment\Java\jdk-21.0.12\bin\java.exe' -jar backend\platform-boot\target\platform-boot-1.0.0-SNAPSHOT.jar
```

后端默认监听 `http://localhost:8080`。

## 前端开发

```powershell
Set-Location frontend
& 'D:\develop\environment\nodejs\npm.cmd' install
& 'D:\develop\environment\nodejs\npm.cmd' run dev
```

开发页面默认访问 `http://localhost:5173`，Vite 会将 `/api` 代理到 `http://localhost:8080`。
登录页和后台顶栏均可切换明亮/暗黑主题，用户选择会保存在浏览器本地并在下次访问时恢复。

质量检查：

```powershell
& 'D:\develop\environment\nodejs\npm.cmd' run lint
& 'D:\develop\environment\nodejs\npm.cmd' run typecheck
& 'D:\develop\environment\nodejs\npm.cmd' run build
```

## 环境变量

复制示例文件：

```powershell
Copy-Item .env.example .env
```

`.env` 包含前后端端口、MySQL、Redis、认证及首次初始化配置，不应提交真实密码。首次启动空库前，至少必须设置：

- `JWT_SECRET`：不少于 32 个 UTF-8 字节的随机密钥；
- `BOOTSTRAP_TENANT_CODE`、`BOOTSTRAP_TENANT_NAME`：首租户编码和名称；
- `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD`：首管理员账号和密码，密码长度为 8 至 72 个 UTF-8 字节。

`.env.example` 仅含非生产占位值，启动前必须更换密码和密钥。`REDIS_PASSWORD` 留空表示无密码，填写后应用和 Compose Redis 会使用同一密码。

## 数据库迁移与首次初始化

应用启动时由 Flyway 按顺序执行 `backend/platform-system/src/main/resources/db/migration` 中的版本脚本：V1 创建 IAM 表，V2 初始化全局菜单与 19 个固定权限码，V3 创建多实例首租户初始化锁，V4 增加用户认证安全版本及角色反向查询索引，V5 增加菜单内置保护及 7 个菜单管理权限码。

仅当数据库中不存在任何租户时，应用才使用 `BOOTSTRAP_*` 创建首租户、管理员、内置 `TENANT_ADMIN` 角色及用户角色关联；已有租户时不会再次创建。迁移脚本是表结构和权限目录的唯一演进入口，请勿修改已发布版本，应新增更高版本脚本。

租户编码和用户名会先去除首尾空白并按 `Locale.ROOT` 转为小写，之后必须匹配 ASCII 规则 `[a-z0-9][a-z0-9._-]*`，最大 64 个字符；冒号、内部空白、重音字符及其他 Unicode 字符均不允许。数据库只保存规范化值，登录失败键也只使用已规范化的合法身份。

用户会话索引使用版本化 Redis ZSET 键 `saas:portal:auth:v2:user-sessions:{tenantId}:{userId}`，成员过期时间决定索引的绝对 TTL。Lua 使用 Redis `TIME` 计算当前毫秒，Java 仅传相对 Refresh TTL，避免应用节点时钟漂移。滚动升级不会对旧 `saas:portal:auth:user-sessions:*` SET 执行 ZSET 命令：旧 session/refresh 仍可使用，旧 Refresh 轮换后自动进入 v2 索引；用户认证状态变化则由数据库 `auth_version` 的逐请求与刷新校验兜底，避免旧索引未清理导致权限复活。该 Lua 协议仍限定单节点 Redis。

## 认证与授权

匿名接口：

```text
POST /api/v1/auth/login    使用 tenantCode、username、password 登录
POST /api/v1/auth/refresh  轮换刷新令牌并签发新的令牌对
GET  /api/v1/health        健康状态
```

认证接口：

```text
POST /api/v1/auth/logout   注销当前会话
GET  /api/v1/auth/me       读取当前用户
/api/v1/system/users       用户管理
/api/v1/system/roles       角色管理
/api/v1/system/menus       菜单树、权限码目录及菜单资源维护
```

角色菜单采用整体替换语义，前端通过 `GET /api/v1/system/roles/{id}/menus` 读取现有授权后再提交。全局菜单遵循“目录 → 目录/页面菜单 → 按钮权限”的层级；内置节点不可停用、删除或修改核心标识，自定义节点删除前必须没有子节点和角色关联。按钮权限新增、修改、启停或删除后，会递增所有受影响租户用户的认证版本并在事务提交后撤销会话。登录令牌保存在当前标签页的 `sessionStorage`，关闭标签页后自动清除；访问令牌过期时，统一请求层会单飞轮换 Refresh Token 并重放等待中的请求。

访问认证接口时使用 HTTP 请求头 `Authorization: Bearer <access-token>`；示例中的 `<access-token>` 是占位符。刷新令牌按会话在 Redis 中保存摘要并轮换，支持同一用户多设备登录。用户状态、密码或权限变化会递增数据库认证版本；即使 Redis 清理暂时失败，旧会话也会在请求和刷新时被拒绝。权限码采用 `领域:资源:动作` 格式，例如 `system:user:list`；内置 `TENANT_ADMIN` 角色拥有全部已启用权限，其他角色通过菜单关联获得权限。

## Docker Compose 启动

Compose 入口只支持全栈模式，统一启动前端、后端、MySQL 和 Redis，不支持仅启动其中部分服务；因此 Compose 会在解析阶段强制检查 IAM 必填变量：

```bash
docker compose up --build
```

后端会等待 MySQL 和 Redis 健康后启动，前端会等待后端健康后启动。数据分别持久化到 `mysql/data` 和 `redis/data`，这些运行目录不会提交到版本库。

当前认证会话 Lua 脚本只支持单节点 Redis，不支持 Redis Cluster；切换到 Cluster 前需要重新设计跨 Key 的 hash slot 方案。

## 健康检查

```text
GET /api/v1/health
```

健康接口状态含义：

- `UP`：应用及所检查组件可用；
- `DOWN`：后端可访问，但 MySQL、Redis 或其他必要组件不可用；
- `UNREACHABLE`：前端无法访问后端服务；
- `UNKNOWN`：尚未取得对应组件状态。

健康接口只公开整体和组件状态，不返回连接串、密码或异常栈。

## 验证限制

后端验证命令：

```powershell
$env:JAVA_HOME = 'D:\develop\environment\Java\jdk-21.0.12'
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml test
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml package
```

本机未安装 Docker，因此未执行 `docker compose config`、镜像构建、MySQL/Redis 容器联调；Maven/H2 测试不代表 Docker 验证。
