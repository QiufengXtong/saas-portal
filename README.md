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

`.env` 包含 MySQL、Redis 和端口配置，不应提交真实密码。模式 1 使用 Compose 服务名 `mysql` 和 `redis`；模式 2
改为后端容器能够访问的外部主机地址。

## Docker Compose 模式

### 模式 1：完整本地环境

启动前端、后端、MySQL、Redis：

```bash
docker compose --profile local-infra up --build
```

`.env` 中保持：

```dotenv
MYSQL_HOST=mysql
REDIS_HOST=redis
```

### 模式 2：使用外部 MySQL 和 Redis

将 `.env` 中的 `MYSQL_HOST`、`REDIS_HOST`、端口和凭据改成外部服务配置，然后执行：

```bash
docker compose up --build
```

该模式只启动前端和后端，不启动本地 MySQL、Redis。

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
