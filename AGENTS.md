# saas-portal Agent 工作指南

本文件根据根目录 `AGENT.md` 整理，适用于整个仓库。开始分析或修改前先阅读本文件；涉及具体实现时，结合 `AGENT.md`、同模块代码和实际配置确认约定。

## 1. 工作原则与规则优先级

- 在系统和执行环境约束内，优先遵循用户当前明确要求，其次是项目规范、同模块已有风格及通用最佳实践。
- 用户要求“只分析”时，只进行读取和查询，不修改文件、不安装依赖、不构建、不启动服务，也不执行有副作用的命令。
- 默认只实施完成当前需求所需的最小改动。不得顺便格式化、重构、升级依赖、调整目录、修改其他业务或创建无用途的抽象和空目录。
- 规范与现有实现不一致时，保持局部一致性并说明差异，不得暗中改变架构。发现无关问题时记录并反馈，不直接整改。
- 本文件是整理后的工作入口，`AGENT.md` 保留详细说明；两者有实质冲突时应明确指出，不据此扩大任务范围。维护项目规范时检查两份文档的一致性。

开始工作前：

1. 判断范围属于 `backend`、`frontend`、`docs` 或部署配置，确认业务领域和模块边界。
2. 查看 Git 状态和已有改动，保护用户尚未提交的工作。
3. 搜索相似实现及可复用的 API、Service、组件、Composable、工具、响应、权限和分页能力。
4. 读取实际依赖与运行配置，不凭经验猜测技术栈或版本。
5. 评估接口兼容、数据库迁移、权限、租户隔离、事务和前后端联动影响。

## 2. 项目结构与架构边界

项目采用 Vue 前端与 Spring Boot 后端分离的 HTTP / REST 架构。后端是 Maven 多模块的模块化单体，最终仅产出一个可执行应用 JAR。

```text
saas-portal/
├── AGENTS.md                 # Agent 工作入口
├── AGENT.md                  # 原始详细开发指南
├── README.md                 # 开发、运行与部署说明
├── backend/
│   ├── pom.xml               # 父 POM、模块与版本管理
│   ├── platform-boot/        # 唯一启动与装配模块
│   ├── platform-common/      # 通用技术能力
│   ├── platform-system/      # 平台 IAM 能力
│   └── platform-business/    # 核心业务扩展位置
├── frontend/                 # Vue 单页应用
├── docs/                     # 设计与实施文档
├── docker-compose.yaml       # 四服务编排入口
└── .env.example              # 环境变量占位示例
```

- 不将 Maven 模块拆成独立应用或 Docker 服务，不擅自改为微服务。
- 新内容优先放入已有目录。未经要求，不增加 `server`、`client`、`web`、`api`、`services`、`deploy`、`database`、`temp` 等根目录。
- 业务边界优先于技术分层，显式依赖与现有能力复用优先于复杂抽象。
- 当前 `platform-system` 已有认证、租户、用户、角色、菜单权限和首次初始化；`platform-business` 目前只有 POM。组织、字典、文件、通知及核心业务领域属于可扩展职责，不代表已经实现。

## 3. 技术栈与配置来源

| 范围 | 技术与事实来源 |
| --- | --- |
| 后端 | Java、Spring Boot / MVC、Maven、MyBatis-Plus、Spring Security、Flyway、MySQL、Redis、Lombok、Jackson、Jakarta Validation、SLF4J/Logback |
| 后端版本 | `backend/pom.xml`、Spring Boot 父 POM、`dependencyManagement` 和各模块 POM |
| 后端配置 | `backend/platform-boot/src/main/resources/application.yml` |
| 前端 | Vue、TypeScript、Vite、Vue Router、Pinia、Axios、Element Plus |
| 前端版本与脚本 | `frontend/package.json`、`frontend/package-lock.json` |
| 部署 | `docker-compose.yaml`、两端 Dockerfile、`frontend/nginx.conf`、`.env.example` |

当前后端声明 Java 21，前端使用 npm。执行任务时仍需重新核对配置；Node 版本需同时满足项目及锁定依赖的要求。

- 使用现有包管理器和锁文件，不混用 npm、pnpm、yarn。当前可复现安装方式为在 `frontend/` 执行 `npm ci`。
- 新增依赖前依次检查：现有业务实现、公共能力、框架能力、语言标准库，只有确有价值才增加依赖。
- 不为简单功能引入大型库或重复 UI、JSON、日期、图表、编辑器、存储封装。
- 未经要求，不替换既有技术路线，不引入 DDD、CQRS、Event Sourcing、领域事件、六边形架构、复杂 Repository 或大量 Factory/Strategy。

## 4. 后端开发规范

### 4.1 模块职责与依赖

- `platform-boot`：唯一 `@SpringBootApplication`、模块装配、运行配置与可执行 JAR。
- `platform-common`：统一响应、异常、基础实体、审计、MyBatis 等与业务无关的技术能力，不放业务 Service、Entity、状态或规则。
- `platform-system`：平台能力；认证、用户、角色、权限复用现有实现。
- `platform-business`：按实际需求增加 datasource、metadata、develop、integration、schedule、execution 等业务领域，不预建空目录。

允许的依赖方向：

```text
platform-boot     -> platform-system、platform-business
platform-system   -> platform-common
platform-business -> platform-common、platform-system（按需）
```

当前 `platform-business` 仅依赖 `platform-common`。禁止 Common 反向依赖业务模块、System 依赖 Business，以及 Maven 循环依赖。

跨 Maven 业务模块调用使用目标模块公开的 `api` 接口；不得直接访问其他业务模块的 Mapper、Entity、`service.impl` 或数据表。通用技术类型通过 `platform-common` 复用。

### 4.2 领域组织与分层

按业务领域组织 `controller`、`service/impl`、`mapper`、`entity`、`dto`、`vo`，按需增加 `api/dto`、`enums`、`constant`、`converter`、`config`、`handler`。

- Controller：HTTP 参数、格式校验、Service 调用、统一响应；不直接访问 Mapper，不编排复杂业务，不堆积对象转换或重复捕获通用异常。
- Service：业务规则、流程、事务、数据访问和转换协调；优先构造器注入和 `final` 字段，方法表达业务语义。
- Mapper：仅负责数据访问；简单 CRUD 使用 MyBatis-Plus，简单动态条件沿用 Lambda API，复杂 JOIN、聚合及报表使用 Mapper XML。
- Entity：仅用于持久化链路，不直接返回前端；DTO 表达请求或跨模块数据，VO 表达响应。
- 简单转换可放在 Service 私有方法中；已有 Converter 等体系时沿用。混合多种职责的 Service 按业务能力拆分，不做“一方法一 Service”。

### 4.3 接口、事务与数据

- 路径优先使用 `/api/v1/<resources>`；业务动作可使用 `POST /api/v1/<resources>/{id}/<action>`，修改时保持已有契约。
- 复用 `Result<T>`、`PageResult<T>`、`BusinessException`、`ErrorCode`、`GlobalExceptionHandler`。
- DTO 使用 Jakarta Validation 进行格式校验，业务规则放在 Service。
- 事务放在 Service，按需使用 `@Transactional(rollbackFor = Exception.class)`，不在 Controller、Mapper、Util 控制业务事务。
- 新表明确主键、唯一约束、索引、类型、长度、NULL、默认值和审计字段，沿用基础实体、逻辑删除和审计填充能力。
- 删除前检查存在性、业务限制、关联数据、权限和事务，不直接机械删除。
- 查询考虑分页上限、排序、索引、批量操作和 N+1，避免 `SELECT *`、循环逐条查询及一次拉取大数据量。
- 数据库变更使用 `platform-system/src/main/resources/db/migration` 中既有 Flyway 机制；不修改已发布迁移，新增后续版本，并检查数据兼容性。

### 4.4 认证、租户与缓存

- 复用 Spring Security 方法授权及现有认证链路，不在业务中另建认证体系。
- 租户来源使用受信任认证上下文和 `TenantScope`，保持上下文清理与 MyBatis 租户隔离，不信任请求自行指定的租户身份，不随意绕过拦截器。
- 菜单是全局资源，用户、角色及授权关联具有租户边界。菜单变更需评估跨租户影响，不将全局菜单误作租户私有数据。
- 用户状态、密码、角色或权限变更需沿用 `auth_version`、事务提交后会话撤销及相关补偿机制。
- Redis Key 使用 `saas:portal:` namespace，检查 TTL、失效、并发、一致性、序列化及缓存穿透/击穿。
- 当前会话 Lua 协议只支持单节点 Redis，不假定支持 Redis Cluster。
- JSON 复用 Spring 管理的 Jackson `ObjectMapper`；日志使用 SLF4J/Logback，禁止 `System.out.println` 和 `printStackTrace`。
- 若新增文件能力，应放在 `platform-system/file` 并提供统一接口，不让业务直接耦合 MinIO、S3 或本地文件系统实现。

## 5. 前端开发规范

### 5.1 目录、组件与类型

- 页面按业务放入 `src/views`；业务专用组件就近放在页面领域下，真正跨业务复用的组件才放入 `src/components`。
- 新 Vue 代码使用 `<script setup lang="ts">` 和 Composition API，修改现有代码保持局部风格。
- Props、Emits 显式声明类型，避免 `any`；不确定的数据使用 `unknown` 并收窄。
- 派生状态使用 `computed`，`watch` 用于副作用、异步请求或外部状态同步，避免隐式联动流程。
- 优先 `async/await`，通过 `finally` 恢复 loading。
- 组件名使用明确的 PascalCase，变量、事件和布尔值表达业务含义，不使用 `data1`、`obj`、`tmp`、`flag1` 等含糊名称。
- `composables` 提取真正可复用逻辑，`utils` 只放通用能力，不放业务规则。
- 当前样式为 CSS 与 Element Plus 主题；沿用实际样式体系，局部样式默认 scoped，不擅自引入 SCSS、UnoCSS 或 Tailwind。

### 5.2 请求、状态与权限

- HTTP 接口封装在 `src/api`，复用 `src/utils/request.ts`；页面不直接创建 Axios 实例或散落 HTTP 调用。
- baseURL、Token、Header、超时、401/403、HTTP 和通用业务错误集中处理；页面只处理业务相关异常，避免重复提示。
- 当前认证状态使用 `stores/auth.ts`，令牌通过 `utils/authSession.ts` 保存在 `sessionStorage`，刷新请求沿用统一的并发合并机制。
- Pinia 只放真正全局状态，弹窗、表单、查询条件和局部 loading 保留在组件内。
- 路由统一放在 `src/router`，页面优先懒加载；检查路径、名称、标题和权限。
- 当前路由和侧栏为静态声明，新增数据库菜单不会自动生成页面或导航；增加页面时检查路由、布局、菜单与权限的联动。
- 复用 `usePermission` 和路由守卫；前端隐藏按钮不能代替后端授权。操作权限还需覆盖打开弹窗、查询选项和读取详情所调用的接口。

### 5.3 交互完整性

- 复用 Element Plus，不引入第二套完整 UI 框架。
- 表单处理规则、初始化、重置、取消、loading、错误提示和重复提交。
- 列表处理 loading、空数据、分页、查询、重置、刷新和权限；大量候选项使用合理的分页或搜索，不依赖固定截断结果。
- Dialog/Drawer 处理回显、关闭清理、表单重置和失败保留，避免残留上次数据。
- 新增、编辑、删除、启停、发布、执行、提交、测试连接等写操作使用 loading/disabled 防止重复触发。
- 删除包含二次确认、执行状态、成功提示、列表刷新与错误处理。

## 6. 前后端协同与敏感配置

接口变更同时核对 Backend DTO/VO、Controller、Frontend Type、Frontend API 和页面。

- 字段名称、类型、NULL 语义、状态枚举、分页和响应结构一致。沿用 `records`、`total`、`pageNum`、`pageSize` 等现有分页字段。
- ID 类型沿用现有字符串响应约定，避免前端数值精度丢失。
- 权限同时实现前端展示和后端真实校验，以后端为准。
- 密码、JWT、Token、AccessKey、SecretKey、数据库密码和完整敏感连接信息不得写入源码、日志或交付输出。
- 敏感配置从环境变量或环境配置读取，不写入前端 Vite 变量、前端产物或镜像。
- `.env.example` 只保留占位示例，不提交真实 `.env` 或运行数据。禁止执行服务端返回的 `eval` 内容。

## 7. Docker 与运行配置

- 根目录 Compose 编排 frontend、backend、MySQL、Redis；frontend 使用 Nginx 提供静态资源并代理 `/api/`。
- 保持健康检查启动依赖：MySQL/Redis → backend → frontend。
- 端口、代理、变量变更需联查 `.env.example`、Compose、`application.yml`、Vite 和 Nginx，不能假定根目录 `.env` 会自动注入所有运行方式。
- 当前 Compose 未透传 `BACKEND_PORT`，Nginx 和后端健康检查固定使用 8080；任务涉及端口时需核对完整链路，不顺便修改。
- 不将 Maven/H2 测试等同于真实 MySQL、Redis 或容器验证，不将镜像构建成功等同于服务运行正常。

## 8. 验证与交付

根据改动风险选择实际可用的验证，先检查当前环境和脚本。以下是可选命令，不要求每次全部执行；只读分析任务不执行安装、测试、构建或启动命令。

| 范围 | 工作目录 | 可选验证 |
| --- | --- | --- |
| 后端 | 仓库根目录 | `mvn -f backend/pom.xml compile`、`mvn -f backend/pom.xml test`、`mvn -f backend/pom.xml package` |
| 前端 | `frontend/` | `npm run lint`、`npm run typecheck`、`npm run build` |
| 文档 | 仓库根目录 | 检查内容、路径、命令和最终差异；纯文档修改通常无需应用构建 |

- Service 单元测试优先 JUnit 与 Mockito，需要 Spring Context 时再使用 `@SpringBootTest`；检查事务、租户、权限、SQL、边界条件和接口兼容。
- 前端检查类型、Import、未使用变量、组件引用、请求契约、loading、错误处理、权限和路由。
- 当前前端没有 `test` 脚本，不臆造可用测试命令，也不因普通需求擅自引入整套测试框架；以后已有测试体系时沿用。
- `npm run build` 包含类型检查但不包含 lint；后端 Docker 构建跳过测试，不能将其报告为测试通过。
- 未执行或失败的验证如实说明原因，不能声称通过。构建、测试失败时区分代码问题和环境限制。

Git 与变更安全：

- 开始与结束时检查相关差异，避免覆盖用户改动，确认无无关修改。
- 未经用户明确要求，不执行 push、force push、merge、rebase、`git reset --hard`、删除分支或改写历史。
- 删除代码前核对直接/间接引用、公共 API、配置、反射、数据兼容及前后端影响，不仅凭 IDE 未引用判断删除。
- 不留下当前任务内可以解决的 TODO/FIXME；任务外问题单独说明。

交付时简要说明：完成内容、影响范围、实际验证及结果、未验证部分和已知风险。以清晰、简单、可维护且满足需求为目标，不扩大任务范围。
