# saas-portal 项目骨架设计

日期：2026-09-03  
状态：已确认，待实施计划

## 1. 背景与目标

当前项目目录仅包含项目开发规范和 `AGENT.md`，尚无前端、后端或部署代码，也不是 Git 仓库。

第一阶段建设一个“不含业务功能、但前后端可独立运行”的基础骨架，为后续平台能力和业务模块开发提供稳定边界。骨架需要覆盖：

- Java 21 + Spring Boot 的 Maven 多模块后端；
- Vue 3 + TypeScript + Vite 的极简前端；
- MySQL、Redis 的连接配置；
- 前端调用后端健康接口的最小闭环；
- 支持内置或外部 MySQL/Redis 的两种 Docker Compose 模式；
- 环境变量示例、忽略规则和开发说明。

本阶段不实现登录、用户、角色、权限、菜单或任何核心业务 CRUD，不创建业务表，不初始化 Git 仓库。

## 2. 设计原则

- 遵循根目录 `AGENT.md` 和项目开发规范。
- 后端保持模块化单体，最终部署为一个 Spring Boot JAR。
- 模块按职责和业务边界组织，不按技术层拆 Maven Module。
- 只创建本阶段运行和后续扩展所必需的文件，不创建无用途的占位类。
- 复用 Spring Boot、Vue 和已有生态能力，不为简单功能引入重复框架。
- 敏感配置只通过环境变量注入，仓库不保存真实密码。
- 未实际执行的验证必须如实标注。

## 3. 项目结构

```text
saas-portal/
├── AGENT.md
├── README.md
├── .env.example
├── .gitignore
├── docker-compose.yaml
├── docs/
│   └── superpowers/
│       └── specs/
│           └── 2026-09-03-project-skeleton-design.md
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── platform-boot/
│   ├── platform-common/
│   ├── platform-system/
│   └── platform-business/
└── frontend/
    ├── package.json
    ├── package-lock.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── Dockerfile
    ├── nginx.conf
    ├── public/
    └── src/
        ├── api/
        ├── router/
        ├── types/
        ├── utils/
        ├── views/
        ├── App.vue
        └── main.ts
```

根目录不新增 `server`、`client`、`web`、`api`、`services`、`deploy` 或 `database` 等平行结构。

## 4. 后端架构

### 4.1 基线与坐标

- Java：21，包名基线为 `com.xtong.saas`。
- 构建工具：Maven，多模块聚合构建。
- 应用启动类：`com.xtong.saas.SaasPortalApplication`。
- 框架：Spring Boot、Spring MVC、Spring Boot Actuator。
- 数据访问：MyBatis-Plus、MySQL Driver。
- 缓存：Spring Data Redis。
- 校验：Jakarta Validation。
- 测试：JUnit 5、Spring Boot Test；需要隔离依赖时使用 Mockito。

具体依赖版本在实施时选择与 Java 21 相容的稳定版本，并在根 POM 集中管理。Spring 官方依赖优先由 Spring Boot dependency
management 管理，禁止在各模块重复声明版本。

### 4.2 Maven 模块

```text
backend/pom.xml
├── platform-common
├── platform-system
├── platform-business
└── platform-boot
```

职责：

- `platform-boot`：唯一启动模块，包含 `SaasPortalApplication`、应用配置及最终可执行 JAR 打包配置。
- `platform-common`：通用基础能力。本阶段实现统一 `Result<T>`，为后续业务 API 提供一致响应结构。
- `platform-system`：平台级公共业务能力。本阶段只建立模块边界，不创建用户、权限等未要求功能。
- `platform-business`：核心业务容器。本阶段只建立模块边界，不创建具体业务领域。

依赖方向：

```text
platform-boot
├── platform-system
└── platform-business

platform-system   -> platform-common
platform-business -> platform-common
```

`platform-common` 不反向依赖 system 或 business，system 不依赖 business，不产生循环依赖。只有 `platform-boot` 创建可执行
Spring Boot JAR，其他模块生成普通 JAR。

### 4.3 配置

`platform-boot` 中的 `application.yml` 使用环境变量占位符配置：

- 服务端口；
- MySQL host、port、database、username、password；
- Redis host、port、password；
- Actuator 健康端点路径和信息披露级别。

源码配置提供非敏感默认值，密码等敏感值由部署环境传入。不得在 YAML、Java、Dockerfile 或前端代码中写入真实凭据。

### 4.4 健康接口

健康接口使用 Spring Boot Actuator：

```text
GET /api/v1/health
```

启用应用、数据库和 Redis 的健康贡献项。响应公开整体状态和组件状态，但隐藏连接信息、异常栈、凭据及其他敏感详情。

- 应用、MySQL、Redis 均正常：整体状态为 `UP`，HTTP 200。
- 任一必要组件不可用：对应组件及整体状态为 `DOWN`，使用 Actuator 默认不可用状态码。
- 前端无法连接后端：前端将其识别为 `UNREACHABLE`，与后端返回 `DOWN` 区分。

Actuator 健康接口属于运维端点，不强制包裹为 `Result<T>`。后续业务 Controller 使用 `platform-common` 中的统一响应结构。

### 4.5 数据库范围

本阶段只完成 DataSource 和 MyBatis-Plus 的依赖及连接配置：

- 不创建业务 Entity、Mapper 或 Service；
- 不创建业务表；
- 不引入 Flyway 或 Liquibase；
- Docker 内置 MySQL 仅通过容器环境变量创建空数据库和应用账号。

数据库迁移工具在首次出现真实表结构需求时另行设计和引入。

## 5. 前端架构

### 5.1 基线

- Vue 3；
- TypeScript；
- Vite；
- Vue Router；
- Pinia；
- Axios；
- Element Plus；
- npm 和 `package-lock.json`。

新增 Vue 文件使用 `<script setup lang="ts">`。依赖版本集中记录在 `package.json` 和锁文件中，选择与当前 Node 24 相容的稳定版本。

### 5.2 页面范围

前端只提供极简欢迎页，不建设管理后台布局、登录页或权限体系。页面包含：

- 项目名称；
- 简短的骨架说明；
- 后端应用状态；
- MySQL 状态；
- Redis 状态；
- 手动刷新按钮；
- 请求期间的 loading；
- 后端不可达或基础设施异常时的明确提示。

视觉实现保持简洁，优先使用 Element Plus，不新增其他 UI 框架。

### 5.3 前端分层

- `src/api/health.ts`：封装健康接口请求。
- `src/types/health.ts`：定义 Actuator 健康响应类型，未知扩展字段保持可兼容。
- `src/utils/request.ts`：创建唯一 Axios 实例，统一 baseURL、超时和基础错误处理。
- `src/router/index.ts`：定义欢迎页路由和兜底路由。
- `src/views/HomeView.vue`：展示健康状态，不直接调用原始 Axios。
- `src/App.vue`：只承载 RouterView。
- `src/main.ts`：注册 Vue、Router、Pinia 和 Element Plus。

Pinia 仅完成应用基础注册，不把页面 loading 或健康检查结果放入全局 Store。

### 5.4 请求状态

```text
页面加载或手动刷新
        ↓
调用 GET /api/v1/health
        ↓
HTTP 200             HTTP 503              网络错误/超时
        ↓                    ↓                       ↓
展示各组件状态       解析可用响应并展示 DOWN       展示 UNREACHABLE
```

Axios 默认将 503 识别为异常，健康 API 封装负责从错误响应中读取合法健康数据。若响应不是预期结构，则回退为不可达/未知状态，不在页面展示异常栈。

### 5.5 本地开发代理

Vite 开发服务器将 `/api` 代理到本机后端 `http://localhost:8080`。浏览器始终请求相对路径 `/api/v1/health`
，避免在页面代码中硬编码不同环境的后端地址。

## 6. Docker Compose 设计

### 6.1 服务与 profile

根目录仅使用一个 `docker-compose.yaml` 作为编排入口。

```text
默认服务
├── frontend
└── backend

local-infra profile
├── mysql
└── redis
```

前后端服务不通过 `depends_on` 强绑定 MySQL/Redis，避免模式 2 禁用 `local-infra` 后出现依赖解析问题。依赖实际状态由健康接口反馈。

### 6.2 模式 1：完整本地服务

`.env` 中 MySQL 和 Redis host 使用 Compose 服务名：

```dotenv
MYSQL_HOST=mysql
REDIS_HOST=redis
```

启动命令：

```bash
docker compose --profile local-infra up --build
```

预期启动 frontend、backend、mysql、redis 四个服务。

### 6.3 模式 2：外部基础设施

`.env` 中填写后端容器可访问的外部 MySQL 和 Redis 地址，不使用 `mysql`、`redis` 服务名。

启动命令：

```bash
docker compose up --build
```

预期只启动 frontend 和 backend。外部基础设施的网络、账号、权限和可用性由部署环境负责。

### 6.4 容器访问链路

```text
Browser
  └── http://localhost
        └── frontend / Nginx
              ├── /        -> Vue 静态资源
              └── /api/*   -> backend:8080
```

建议端口映射：

- frontend：`80:80`；
- backend：`8080:8080`；
- mysql：`3306:3306`；
- redis：`6379:6379`。

Nginx 代理和 Vite 代理使前端通过同源路径访问 API，不启用宽泛的全局 CORS。

### 6.5 环境文件

`.env.example` 记录以下变量及安全的示例值：

```text
BACKEND_PORT
FRONTEND_PORT
MYSQL_HOST
MYSQL_PORT
MYSQL_DATABASE
MYSQL_USERNAME
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
```

实际 `.env` 由使用者从 `.env.example` 复制并修改，且必须被 `.gitignore` 排除。

## 7. Dockerfile 设计

### 7.1 后端

后端使用多阶段构建：

1. Maven + JDK 21 镜像构建全部模块；
2. JRE 21 镜像仅复制 `platform-boot` 的可执行 JAR；
3. 容器以非 root 用户运行；
4. 暴露 8080 端口；
5. 不把 `.env` 或凭据复制进镜像。

### 7.2 前端

前端使用多阶段构建：

1. Node 镜像执行 `npm ci` 和生产构建；
2. Nginx 镜像只复制静态产物和代理配置；
3. Nginx 将 `/api` 转发到 `backend:8080`；
4. SPA 路由回退到 `index.html`。

镜像版本在实施时固定到明确的兼容版本，避免使用不受控的 `latest` 标签。

## 8. 本地开发环境

已确认的本机工具：

```text
JDK_HOME   D:\develop\environment\Java\jdk-21.0.12
MAVEN_HOME D:\develop\environment\apache\maven\apache-maven-3.9.1
NVM_HOME   D:\develop\environment\nvm
NODE_HOME  D:\develop\environment\nodejs
Java       21.0.12
Maven      3.9.1
Node       24.19.0（当前启用）
npm        11.17.0
```

本机同时安装 Node 22.23.2 和 24.19.0。本阶段使用当前启用的 Node 24.19.0；如果依赖工具明确不支持该版本，再通过 NVM 切换到
Node 22.23.2，并在 README 中记录。

## 9. 验证策略

### 9.1 后端

验证时显式使用已确认的 JDK 和 Maven：

```powershell
$env:JAVA_HOME = 'D:\develop\environment\Java\jdk-21.0.12'
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' test
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' package
```

检查：

- 四个模块均能编译；
- 单元测试通过；
- 只有 `platform-boot` 生成可执行 Spring Boot JAR；
- 模块依赖方向符合设计；
- 应用配置未硬编码真实凭据。

不依赖真实 MySQL/Redis 的上下文测试不得因基础设施缺失而失败。需要验证真实连接的部分属于后续集成环境验证。

### 9.2 前端

使用当前 NVM 激活的 Node 24.19.0 和 npm 11.17.0：

```bash
npm run lint
npm run typecheck
npm run build
```

检查：

- TypeScript 类型有效；
- ESLint 无错误；
- 极简页面和路由可生产构建；
- API 请求通过统一 Axios 实例；
- loading、DOWN、UNREACHABLE 状态均有明确处理。

### 9.3 Docker

本机没有 Docker，因此本阶段不执行：

- `docker compose config`；
- 镜像构建；
- 容器启动；
- 模式 1 或模式 2 的集成验证。

Docker 相关文件只进行文本级静态检查，包括 YAML 结构、profile、服务引用、变量名称、端口、构建上下文和 Nginx upstream
一致性。交付时必须明确标注“Docker 配置已生成，但未在本机实际验证”，不得宣称 Docker 启动成功。

## 10. README 内容

README 至少包含：

- 项目定位和当前阶段范围；
- 根目录及模块说明；
- 本地 Java、Maven、Node、npm 环境要求；
- 后端和前端开发启动命令；
- `.env.example` 的复制与配置方法；
- Docker 模式 1、模式 2 的命令和差异；
- `/api/v1/health` 的用途与状态语义；
- 已执行验证和 Docker 未验证限制。

## 11. 验收标准

完成以下条件即认为项目骨架实施完成：

- 项目目录符合开发规范，无额外根目录；
- Java 根包名为 `com.xtong.saas`；
- 后端四模块边界及依赖方向正确；
- 后端能通过 Maven 测试和打包，最终只有一个可运行应用；
- 前端能通过 lint、typecheck 和 build；
- 欢迎页通过统一 API 层请求 `/api/v1/health`；
- 页面区分 `UP`、`DOWN`、`UNREACHABLE`；
- 健康接口覆盖应用、MySQL、Redis 且不披露敏感详情；
- 单个 `docker-compose.yaml` 描述两种运行模式；
- `.env.example` 完整，真实 `.env` 被忽略；
- README 能指导开发者启动和理解项目；
- Docker 文件完成静态检查，并明确注明未做实际运行验证；
- 不包含认证、权限、菜单、业务 CRUD、业务表等超出范围的功能；
- 不初始化 Git，不创建提交，不执行远程操作。

## 12. 非目标

以下内容不属于本阶段：

- 登录、JWT、用户、角色、权限和菜单；
- 管理后台布局；
- 业务模块、业务接口和数据库表；
- Flyway/Liquibase 数据库迁移；
- 文件存储、消息、任务调度等基础设施；
- 微服务、服务注册、配置中心、消息队列；
- Docker 实机运行验证；
- Git 初始化、提交或推送。
