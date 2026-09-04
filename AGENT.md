# saas-portal Agent 开发指南

本文件约束所有在 `saas-portal` 项目中工作的 AI 编程 Agent。开始新增、修改、重构或删除代码前，必须先阅读并遵守本文件。

## 1. 规则优先级

1. 用户当前明确要求。
2. 本文件及项目现有约定。
3. 同模块已有实现和代码风格。
4. 框架、语言与工具的通用最佳实践。

当现有实现与本文细节不一致时，优先保持局部一致性。除非任务明确要求，不得为了套用规范进行全局整改或无关重构。发现冲突时应明确说明，不得暗中改变架构。

## 2. 工作方式

接到任务后，先完成以下检查，再修改代码：

- 判断任务属于 `frontend`、`backend`、`docs` 还是部署配置。
- 确认所属业务模块及模块边界。
- 搜索相似实现，确认是否已有可复用的 API、Service、组件、Composable、Util、Converter、异常、响应结构、权限或分页能力。
- 评估接口兼容性、数据库变更、权限、事务、跨模块调用及前后端联动影响。
- 读取实际配置确认技术栈、版本、脚本和包管理器，不凭经验猜测。

默认实施完成需求所需的最小改动。禁止顺便：

- 重构或格式化无关代码；
- 升级依赖、调整目录或替换技术栈；
- 修改其他业务模块；
- 新增无实际用途的层、类或空目录。

发现无关问题时，先完成当前任务，再在交付说明中指出问题并给出建议；未经要求不要直接处理。

## 3. 项目结构与总体架构

根目录约定：

```text
saas-portal/
├── docs/
├── AGENT.md
├── backend/
├── frontend/
└── docker-compose.yaml
```

新增内容优先放入已有目录。未经明确要求，不新增 `server`、`client`、`web`、`api`、`services`、`deploy`、`database`、`temp` 等根目录。

项目采用前后端分离架构：

- 前端：Vue 3 单独构建。
- 后端：Spring Boot Maven 多模块的模块化单体，最终打包为单个 JAR。
- 通信：HTTP / REST API。
- 基础设施：MySQL、Redis 及项目已有设施。
- 编排入口：根目录 `docker-compose.yaml`。

默认不采用微服务。禁止把 Maven Module 拆成独立服务或 Docker Service。

核心取舍：业务边界优先于技术分层；清晰、简单、可维护优先于炫技和过度设计；复用优先于重复实现；显式依赖优先于隐式依赖。

## 4. 后端规范

### 4.1 技术栈与模块

后端位于 `backend/`，默认使用 Java、Spring Boot、Spring MVC、Maven、MyBatis-Plus、MySQL、Redis、Lombok、Jackson、Jakarta
Validation、SLF4J/Logback、JUnit 5 和 Mockito。实际版本必须读取 `backend/pom.xml`、父 POM 和 `dependencyManagement`。

实际模块以 `backend/pom.xml` 为准，典型职责如下：

- `platform-boot`：唯一应用启动与装配模块；原则上项目只有一个 `@SpringBootApplication`。
- `platform-common`：仅放与具体业务无关的通用技术能力，如统一响应、异常、基础实体、配置和通用工具。
- `platform-system`：认证、用户、角色、权限、菜单、组织、字典、文件、通知等平台能力。
- `platform-business`：按 datasource、metadata、develop、integration、schedule、execution 等业务领域组织核心业务。

禁止把具体业务 Service、Entity、状态或规则放进 `platform-common`。判断标准是：删除 SaaS Portal 的具体业务后，该能力是否仍有通用价值。

### 4.2 依赖边界

允许的依赖方向：

```text
platform-boot -> platform-system
platform-boot -> platform-business
platform-business -> platform-system
platform-business -> platform-common
platform-system -> platform-common
```

禁止：

```text
platform-common -> platform-system
platform-common -> platform-business
platform-system -> platform-business
```

不得产生 Maven 循环依赖。跨模块调用必须通过目标模块公开的 `api` 接口；不得直接访问其他模块的 Mapper、Entity、`service.impl`
或数据表。每个业务模块负责管理自己的数据。

### 4.3 业务模块组织

业务模块按需采用以下结构，不为目录完整性创建空目录：

```text
<domain>/
├── api/
│   └── dto/
├── controller/
├── service/
│   └── impl/
├── mapper/
├── entity/
├── dto/
└── vo/
```

可按需增加 `enums`、`constant`、`converter`、`config`、`handler`。禁止按 Controller、Service、Mapper、Entity 等技术类型拆成独立
Maven 模块。

### 4.4 分层职责

- Controller：仅处理 HTTP 请求、格式校验、Service 调用和统一响应。不得直接调用 Mapper 或数据库，不承载复杂业务流程。
- Service：承载业务规则、业务流程、事务、Mapper 调用、跨模块 API 调用和对象转换协调。优先构造器注入与 `final` 字段。
- Mapper：仅负责数据访问。简单 CRUD 用 MyBatis-Plus，简单动态条件优先 Lambda API，复杂 JOIN、聚合或报表使用 Mapper
  XML；不得实现业务规则。
- API：模块对外的稳定能力边界。

Service 方法名应表达业务语义，如 `createDatasource`、`testConnection`、`enableDatasource`
，避免只是机械复制 `save`、`update`、`select`。ServiceImpl 过大且混合不同能力时按业务能力拆分，但不得“一方法一 Service”。

### 4.5 数据对象与转换

- Entity：数据库对象，仅在 Service、Mapper 和数据库链路中使用，不直接暴露给前端。
- DTO：请求参数或跨模块数据，名称应体现用途，如 `CreateDTO`、`UpdateDTO`、`QueryDTO`。
- VO：HTTP 响应对象，Controller 优先返回 VO。

简单转换可放在 Service 的私有方法中；项目已有 MapStruct 或 Converter 体系时必须沿用。禁止在 Controller 中堆积对象转换代码。

### 4.6 接口、事务、校验和异常

- REST 路径优先使用 `/api/v1/<resources>`；业务动作可使用 `POST /api/v1/<resources>/{id}/<action>`。
- 统一复用项目已有 `Result<T>`、`PageResult<T>`，不得新增重复响应体系。
- 格式校验使用 DTO + Jakarta Validation，业务规则校验放在 Service。
- 业务事务放在 Service，按需使用 `@Transactional(rollbackFor = Exception.class)`；不得在 Controller、Mapper 或 Util
  中控制业务事务。
- 统一复用 `BusinessException`、`ErrorCode`、`GlobalExceptionHandler`。Controller 不重复捕获和包装通用异常。

### 4.7 数据、缓存、日志与安全

- 新表需明确主键、唯一约束、索引、字段类型和长度、NULL、默认值及审计字段。
- SQL 需考虑索引、分页、范围、排序、JOIN、批量处理、N+1 和大数据量；避免 `SELECT *` 及循环内逐条查询。
- Redis Key 必须带 namespace，如 `saas:portal:<domain>:<id>`，并考虑 TTL、失效、穿透、击穿、一致性和序列化。
- JSON 统一复用 Spring 管理的 Jackson `ObjectMapper`，不得因个人偏好增加其他 JSON 库或在业务代码中反复实例化。
- 日志统一使用 SLF4J + Logback，禁止 `System.out.println` 和 `printStackTrace`。
- 日志和代码不得泄露密码、JWT、Token、AccessKey、SecretKey、数据库密码或完整敏感连接信息。
- 文件能力通过 `platform-system/file` 使用，不得让业务代码直接依赖 MinIO、S3 或本地文件系统实现。
- 认证、用户、角色和权限统一复用 `platform-system/auth` 等系统能力，不得在业务模块重复实现。

未经明确要求，禁止引入 DDD、CQRS、Event Sourcing、领域事件、六边形架构、复杂 Repository、大量 Factory/Strategy，或替换
MyBatis-Plus、Spring MVC、Maven、Jackson、MySQL、模块化单体等既有技术路线。

## 5. 前端规范

### 5.1 技术栈与目录

前端位于 `frontend/`，默认使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios 和 Element
Plus。实际情况以 `frontend/package.json` 和锁文件为准。

根据锁文件选择包管理器：

```text
pnpm-lock.yaml   -> pnpm
package-lock.json -> npm
yarn.lock        -> yarn
```

不得混用包管理器。未经要求，不得将 Vue、Pinia、Element Plus、Vite 或 TypeScript 替换为其他技术。

遵循现有目录结构。`views` 按业务领域组织；业务专用组件放在对应 `views/<domain>/components`
附近；只有真正跨业务复用的组件才放入全局 `components`。

### 5.2 Vue 与 TypeScript

- 新增 Vue 代码默认使用 `<script setup lang="ts">` 和 Composition API；修改旧模块时优先保持该模块现有风格。
- Props 和 Emits 必须显式定义类型。
- 尽量提供完整类型，避免 `any`；无法确认时使用 `unknown` 并进行类型收窄。
- 派生状态优先使用 `computed`；`watch` 仅用于副作用、异步请求或外部状态同步，避免多个 watch 形成隐式流程。
- 异步请求优先 `async/await`，必须通过 `finally` 等方式保证 loading 正确恢复。

### 5.3 API、状态与权限

- 所有 HTTP 请求统一放在 `src/api` 层，并复用项目统一 Request/Axios 实例。
- 页面不得直接散落 `axios.get` 或自行创建 Axios 实例。
- baseURL、Token、Header、超时、401、403、HTTP 错误和通用业务异常由统一请求层处理。
- 页面仅处理有业务意义的异常；统一层已处理的错误不得重复弹出。
- Pinia 仅保存真正全局的用户、Token、权限、菜单、主题等状态。弹窗、表单、查询条件和局部 loading 保持在组件内。
- 路由统一在 `router` 管理，页面优先懒加载，并检查 path、name、title、权限、菜单、缓存等现有约定。
- 权限必须复用项目已有路由、菜单、按钮和角色机制，如 `v-permission`、`v-auth` 或 `usePermission`。前端隐藏按钮不能代替后端权限校验。

### 5.4 UI 与交互

- 优先复用 Element Plus 和项目已有组件，不引入第二套完整 UI 框架。
- 表单需处理 model、rules、初始化、重置、取消、loading、错误提示和重复提交。
- 列表需处理 loading、空数据、分页、查询、重置、刷新、操作权限；大数据量场景不得一次请求全部记录。
- Dialog/Drawer 需处理打开初始化、编辑回显、关闭清理、表单重置、提交 loading 和失败状态，不能残留上次数据。
- 新增、编辑、删除、发布、执行、提交、测试连接等操作必须用 loading/disabled 防止重复点击。
- 删除操作需包含二次确认、loading、成功提示、列表刷新和错误处理。

### 5.5 组件、工具与样式

- 组件名使用明确的 PascalCase；变量、事件和布尔值使用可读的业务命名，禁止 `data1`、`obj`、`tmp`、`flag1` 等含糊命名。
- 仅把真正可复用逻辑放入 `composables`，不要为几个简单变量创建 Composable。
- `utils` 仅放日期、字符串、存储、请求、下载、校验等通用能力，不放具体业务规则。
- 样式沿用项目现有 SCSS、UnoCSS 或 Tailwind 体系；局部页面和业务组件默认使用 scoped 样式，不污染全局 CSS。
- 日期库、图表库、Monaco Editor、Storage 封装均优先复用项目已有能力，不重复初始化或引入同类依赖。

### 5.6 前端安全

禁止在前端代码或 Vite 环境变量中保存数据库密码、SecretKey、服务端 Token 等真正 Secret；禁止打印认证 Token
或执行服务端返回的 `eval` 内容。认证信息必须遵循项目统一机制。

## 6. 前后端协同

接口变更必须同时检查：

```text
Backend DTO / VO
Controller API
Frontend Type
Frontend API
Frontend Page
```

确保字段名称、类型、NULL 语义、状态枚举、分页参数和响应结构一致。分页字段必须沿用现有约定，不得在前端擅自改名。相同业务概念在前后端不得使用含义不同的名称。

权限必须同时实现前端展示控制和后端真实校验，以后端校验为准。

后端删除操作必须检查数据是否存在、是否允许删除、关联数据、权限和事务，不能只机械执行数据库 DELETE。

## 7. 依赖与配置

新增依赖前按以下顺序判断：

1. 当前业务或项目是否已有实现。
2. 是否有已有公共组件、Composable、Util 或第三方库可复用。
3. Spring Boot、Vue、Element Plus 等框架能力是否可解决。
4. JDK 或 JavaScript 标准能力是否可解决。
5. 只有确有价值时才新增依赖。

禁止为了简单功能引入大型依赖。

敏感配置必须来自环境变量、环境配置文件或配置系统，不得硬编码进源码、前端包或 Docker 镜像。

## 8. 测试与验证

修改后必须按风险和影响范围执行实际可用的验证。

后端：

- 先读取 Maven 结构和配置，再选择 `mvn compile`、`mvn test` 或项目已有命令。
- 检查编译、Import、依赖与循环依赖、空指针、事务、SQL、参数校验、异常处理和 API 兼容性。
- Service 单元测试优先 JUnit 5 + Mockito；只有需要 Spring Context 时使用 `@SpringBootTest`。

前端：

- 先读取 `package.json` 的实际 scripts，再选择 lint、typecheck、test、build。
- 检查 TypeScript、Import、未使用变量、组件引用、API 地址、请求参数、响应类型、loading、错误处理、权限和路由。
- 已有 Vitest、Vue Test Utils 或 Playwright 时沿用；项目没有测试体系时，不因普通需求擅自引入整套测试框架。

不得伪造验证结果。未实际执行的 compile、test、lint 或 build，必须明确写明未执行及原因，不能声称通过。

## 9. Git 与变更安全

可按需执行只读检查，如 `git status`、`git diff`、`git log`。完成修改后应检查 diff，确认没有无关改动。

未经用户明确要求，禁止：

- `git push`、force push；
- merge、rebase；
- `git reset --hard`；
- 删除分支或改写 Git 历史。

删除代码前必须确认直接与间接引用、公共 API、配置引用、反射使用、数据兼容和前后端影响。不得仅凭 IDE 未发现引用就大规模删除。

## 10. 实施与交付清单

开发前确认：

- [ ] 所属目录、业务模块和模块边界已明确。
- [ ] 已搜索相似实现和可复用能力。
- [ ] 已读取实际技术栈、版本、脚本和包管理器。
- [ ] 已评估接口、数据库、权限、事务和跨模块影响。
- [ ] 方案符合最小修改原则，未引入不必要复杂度。

开发后确认：

- [ ] 未修改无关代码，未擅自改变架构或技术栈。
- [ ] 前后端字段、分页、状态和 NULL 语义一致。
- [ ] 权限、异常、事务、SQL、loading 和防重复操作处理合理。
- [ ] 未泄露或硬编码敏感信息。
- [ ] 已执行适用的编译、测试、lint、typecheck 或 build，并如实记录结果。
- [ ] 已检查最终 diff。
- [ ] 未留下可在当前任务中解决的 `TODO` 或 `FIXME`。

交付时简要说明：改了什么、影响范围、执行了哪些验证、验证结果，以及任何未完成项或已知风险。

## 11. 最终原则

在“复杂但高级”和“简单、清晰、满足需求”的方案之间，默认选择后者。项目目标是保持结构清晰、规范统一、业务边界明确、前后端职责清楚、代码低耦合、易维护、易测试、易部署，并为未来演进保留空间。
