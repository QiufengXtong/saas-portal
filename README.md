<!-- 文件作用：说明 saas-portal 的工程结构、本地开发、部署方式与当前运行限制。 -->

# saas-portal

`saas-portal` 是一个前后端分离的 SaaS 平台基础骨架。当前阶段只提供可启动的工程结构、基础配置和健康状态页面，不包含认证、权限、菜单或业务
CRUD。

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

`.env` 包含前后端及基础服务的宿主机端口、MySQL 数据库和密码配置，不应提交真实密码。Redis 当前采用空密码模式。

## Docker Compose 启动

Compose 统一启动前端、后端、MySQL 和 Redis：

```bash
docker compose up --build
```

后端会等待 MySQL 和 Redis 健康后启动，前端会等待后端健康后启动。数据分别持久化到 `mysql/data` 和 `redis/data`，这些运行目录不会提交到版本库。

当前认证会话的 Lua 脚本按项目现有 Compose 与应用配置仅支持单节点 Redis，不支持 Redis Cluster；切换到 Cluster 前需要重新设计跨 Key 的 hash slot 方案。

## 健康检查

```text
GET /api/v1/health
```

欢迎页通过该接口展示：

- `UP`：应用及所检查组件可用；
- `DOWN`：后端可访问，但 MySQL、Redis 或其他必要组件不可用；
- `UNREACHABLE`：前端无法访问后端服务；
- `UNKNOWN`：尚未取得对应组件状态。

健康接口只公开整体和组件状态，不返回连接串、密码或异常栈。

## 验证限制

Docker 配置已生成，但因本机未安装 Docker，未进行 Compose 解析、镜像构建或容器运行验证。
