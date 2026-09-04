# saas-portal 项目开发规范

> 本文件是 `saas-portal` 项目的统一开发规范，同时约束后端、前端、数据库、接口、Docker、Git 以及 AI Agent 的开发行为。
>
> 所有开发人员和 AI 编程助手在新增、修改、重构代码前，都必须优先遵守本文件。
>
> 项目已有实现与本规范存在细节差异时：
>
> **优先保持已有项目一致性，不允许为了套规范而进行无关的大规模重构。**

---

# 1. 项目总体结构

项目根目录固定为：

```text
saas-portal/
├── docs/
├── AGENT.md
├── backend/
├── frontend/
└── docker-compose.yaml
```

各目录职责：

```text
docs/
    项目文档、架构设计、数据库设计、接口说明、
    部署说明、业务方案、开发文档等

AGENT.md
    整个项目统一开发规范和 AI Agent 行为约束

backend/
    Java / Spring Boot 后端项目

frontend/
    Vue 3 前端项目

docker-compose.yaml
    项目统一 Docker Compose 编排入口
```

未经明确需求，不允许随意增加新的根目录，例如：

```text
server/
client/
web/
api/
services/
deploy/
database/
temp/
```

新增内容应优先归入已有目录。

---

# 2. 项目整体技术架构

整体采用：

```text
Vue 3 Frontend
        │
        │ HTTP / REST API
        ▼
Spring Boot Backend
        │
        ├── MySQL
        ├── Redis
        └── 其他基础设施
```

架构原则：

```text
前后端分离
+
后端模块化单体
+
单 JAR 部署
+
前端单独构建
+
Docker Compose 编排
```

当前项目默认不采用微服务架构。

---

# 3. 核心开发原则

所有开发必须优先遵循：

```text
业务边界 > 技术分层

清晰 > 炫技

简单 > 过度设计

项目已有规范 > 个人偏好

复用已有能力 > 重复造轮子

最小修改 > 无关重构

业务语义 > 单纯 CRUD

显式依赖 > 隐式依赖

可维护性 > 短期减少代码量
```

禁止为了所谓“最佳实践”擅自改变项目整体架构。

---

# 4. AI Agent 开发基本规则

AI 收到任务后，不允许直接开始创建大量代码。

必须先判断：

```text
1. 当前需求属于 frontend、backend、docs 还是部署？

2. 属于哪个业务模块？

3. 项目中是否已经存在类似实现？

4. 是否存在可复用 API、Service、组件、Util、Composable？

5. 是否会影响现有接口？

6. 是否涉及数据库修改？

7. 是否涉及权限？

8. 是否涉及事务？

9. 是否涉及跨模块调用？

10. 是否会产生兼容性问题？
```

然后再进行开发。

---

# 5. 最小修改原则

默认只修改完成当前需求所必须修改的代码。

禁止：

```text
顺便重构无关代码

顺便格式化整个项目

顺便升级依赖

顺便调整目录

顺便改技术栈

顺便修改其他业务模块
```

如果发现无关问题：

```text
完成当前任务
+
指出问题
+
给出修改建议
```

未经明确要求，不进行大规模重构。

---

# 6. 后端总体架构

后端位于：

```text
saas-portal/backend/
```

后端采用：

```text
Java
+
Spring Boot
+
Maven 多模块
+
模块化单体
+
MVC 分层架构
+
MyBatis-Plus
```

最终统一打包成一个：

```text
Spring Boot JAR
```

不因为 Maven 存在多个 Module 就拆成多个服务。

---

# 7. 后端模块结构

推荐：

```text
backend/
├── pom.xml
├── platform-boot/
├── platform-common/
├── platform-system/
└── platform-business/
```

具体模块以：

```text
backend/pom.xml
```

中现有模块为准。

---

# 8. platform-boot

`platform-boot` 是应用启动模块。

主要负责：

```text
Spring Boot Application
模块装配
全局配置
应用启动入口
```

原则上整个项目只有一个：

```java
@SpringBootApplication
```

例如：

```java

@SpringBootApplication
public class SaasPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaasPortalApplication.class, args);
    }
}
```

其他业务 Module 不允许自行创建独立 Spring Boot Application。

---

# 9. platform-common

`platform-common` 只允许包含业务无关的公共技术能力。

例如：

```text
Result
PageResult

BusinessException
ErrorCode

BaseEntity

GlobalExceptionHandler

RedisUtils
JsonUtils
DateUtils

通用注解
日志能力
Web 配置
MyBatis 配置
Redis 配置
通用 Converter
```

禁止放入：

```text
UserService

DatasourceService

TaskService

UserEntity

DatasourceEntity

具体业务状态

具体业务规则
```

判断标准：

> 如果删除整个 SaaS Portal 的具体业务，这段代码是否仍然具有通用价值？

如果没有，就不应该放进 `common`。

---

# 10. platform-system

`platform-system` 负责平台级公共业务能力。

例如：

```text
auth
user
role
permission
menu
organization
dict
file
notify
```

可以理解为：

```text
platform-system
├── auth
├── user
├── role
├── permission
├── menu
├── organization
├── dict
├── file
└── notify
```

这些属于平台能力，而不是核心业务。

---

# 11. platform-business

`platform-business` 负责项目核心业务。

按业务领域划分，例如：

```text
platform-business/
├── datasource/
├── metadata/
├── develop/
├── integration/
├── schedule/
└── execution/
```

具体业务领域以当前项目为准。

禁止按照技术类型拆成：

```text
controller-module

service-module

mapper-module

entity-module
```

必须按照业务领域组织代码。

---

# 12. 后端依赖方向

允许：

```text
boot → system

boot → business

system → common

business → system

business → common
```

禁止：

```text
common → system

common → business

system → business
```

禁止产生 Maven 循环依赖。

依赖关系原则：

```text
                 platform-boot
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
    platform-system       platform-business
            │                     │
            └──────────┬──────────┘
                       ▼
               platform-common
```

---

# 13. 后端业务模块目录结构

单个业务模块默认采用：

```text
datasource/
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

根据需要允许增加：

```text
enums/
constant/
converter/
config/
handler/
```

不要为了目录完整创建没有代码的空目录。

禁止无理由创建新的架构层。

---

# 14. API 层

`api` 是当前业务模块向其他模块提供能力的稳定边界。

例如：

```java
public interface DatasourceApi {

    DatasourceDTO getById(Long id);

}
```

跨模块调用：

```text
develop
   ↓
DatasourceApi
   ↓
datasource
```

禁止：

```text
develop
   ↓
DatasourceMapper
```

其他模块不得直接访问：

```text
mapper

entity

service.impl
```

等内部实现。

---

# 15. Controller

Controller 只负责 HTTP 层。

职责：

```text
接收请求

参数格式校验

调用 Service

返回结果
```

推荐：

```java

@PostMapping
public Result<Long> create(
        @Valid @RequestBody DatasourceCreateDTO dto) {

    return Result.success(
            datasourceService.create(dto)
    );
}
```

禁止：

```text
Controller → Mapper

Controller → Database
```

禁止把复杂业务流程写进 Controller。

---

# 16. Service

Service 是主要业务逻辑层。

负责：

```text
业务规则

业务流程

事务

Mapper 调用

跨模块 API 调用

对象转换协调
```

推荐构造器注入：

```java

@Service
@RequiredArgsConstructor
public class DatasourceServiceImpl
        implements DatasourceService {

    private final DatasourceMapper datasourceMapper;

}
```

优先：

```text
final
+
@RequiredArgsConstructor
```

不推荐：

```java

@Autowired
private DatasourceMapper datasourceMapper;
```

---

# 17. Service 方法设计

方法应体现业务语义。

推荐：

```text
createDatasource()

updateDatasource()

testConnection()

enableDatasource()

disableDatasource()

getDatasourceDetail()

pageDatasources()
```

避免只是机械复制 Mapper：

```text
save()

update()

delete()

select()
```

---

# 18. ServiceImpl 复杂度

避免产生超大型 ServiceImpl。

出现以下情况时，应考虑拆分：

```text
数千行代码

大量 private 方法

多个完全不同的业务能力

复杂流程互相混杂
```

可以：

```text
DatasourceService

DatasourceConnectionService

DatasourceMetadataService
```

但禁止：

> 一个方法拆一个 Service。

---

# 19. Mapper

Mapper 只负责数据库访问。

例如：

```java

@Mapper
public interface DatasourceMapper
        extends BaseMapper<DatasourceEntity> {
}
```

原则：

```text
简单 CRUD
    → MyBatis-Plus

简单动态条件
    → LambdaQueryWrapper

复杂 JOIN / 聚合 / 报表
    → Mapper XML
```

禁止在 Mapper 中实现业务规则。

---

# 20. Entity / DTO / VO

必须合理区分：

```text
Entity
DTO
VO
```

原则：

```text
数据库对象
    → Entity

请求参数
    → DTO

跨模块数据
    → DTO

HTTP 响应
    → VO
```

禁止 Entity 同时承担：

```text
数据库对象
+
请求参数
+
响应对象
+
跨模块 DTO
```

---

# 21. Entity

例如：

```java

@Data
@TableName("data_source")
public class DatasourceEntity {

    @TableId
    private Long id;

    private String name;

}
```

Entity 主要存在于：

```text
Service
  ↓
Mapper
  ↓
Database
```

禁止直接将 Entity 暴露给前端。

---

# 22. DTO

推荐命名：

```text
DatasourceCreateDTO

DatasourceUpdateDTO

DatasourceQueryDTO

DatasourceDTO
```

命名必须体现用途。

避免：

```text
DatasourceData

DatasourceInfo

DatasourceParam
```

除非项目已有明确约定。

---

# 23. VO

VO 用于 HTTP API 返回前端。

例如：

```java

@Data
public class DatasourceVO {

    private Long id;

    private String name;

    private String typeName;

}
```

Controller 优先返回 VO，而不是 Entity。

---

# 24. 对象转换

简单场景允许：

```java
private DatasourceVO toVO(DatasourceEntity entity) {
    ...
}
```

如果项目已经使用 MapStruct：

```text
converter/
└── DatasourceConverter.java
```

优先沿用项目现有 Converter 体系。

禁止在 Controller 中大量进行对象转换。

---

# 25. 后端标准调用链

普通请求：

```text
HTTP Request
      ↓
Controller
      ↓
Service
      ↓
Mapper
      ↓
Database
```

返回：

```text
Database
    ↓
Entity
    ↓
Service
    ↓
VO
    ↓
Controller
    ↓
HTTP Response
```

---

# 26. 跨模块调用

模块之间必须通过明确接口调用。

推荐：

```java

@Service
@RequiredArgsConstructor
public class DevelopServiceImpl implements DevelopService {

    private final DatasourceApi datasourceApi;

}
```

禁止跨业务模块：

```text
直接调用 Mapper

直接访问 Entity

直接访问 service.impl

直接操作另一个业务模块的数据表
```

原则：

> 一个业务模块负责管理自己的数据。

---

# 27. 事务

事务原则上放在 Service。

例如：

```java

@Transactional(rollbackFor = Exception.class)
public Long createDatasource(...) {
    ...
}
```

禁止在：

```text
Controller

Mapper

Util
```

中控制业务事务。

---

# 28. 异常处理

统一使用项目异常体系：

```text
BusinessException

ErrorCode

GlobalExceptionHandler
```

例如：

```java
throw new BusinessException(
        ErrorCode.DATASOURCE_NOT_FOUND
        );
```

Controller 不要大量：

```java
try{
        ...
        }catch(Exception e){
        ...
        }
```

通用异常统一由全局异常处理器处理。

---

# 29. 参数校验

使用 Jakarta Validation。

例如：

```java

@Data
public class DatasourceCreateDTO {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    @NotNull(message = "数据源类型不能为空")
    private DatasourceType type;

}
```

格式校验：

```text
DTO + Validation
```

业务规则：

```text
Service
```

---

# 30. 后端技术栈

默认后端技术栈：

```text
Java

Spring Boot

Spring MVC

Maven

MyBatis-Plus

MySQL

Redis

Lombok

Jackson

Jakarta Validation

SLF4J

Logback

JUnit 5

Mockito

Docker
```

具体版本必须读取：

```text
backend/pom.xml

parent pom

dependencyManagement
```

禁止根据经验猜版本。

---

# 31. MyBatis-Plus

简单条件查询优先使用 Lambda API：

```java
Wrappers.lambdaQuery()
        .

eq(
        DatasourceEntity::getStatus,
        status
        );
```

优先：

```java
DatasourceEntity::getStatus
```

避免：

```java
wrapper.eq("status",status);
```

复杂 SQL 不要强行使用 Wrapper。

---

# 32. Redis

Redis 可以用于：

```text
缓存

验证码

临时状态

Token 辅助信息

限流

分布式锁
```

Key 必须具有 namespace。

推荐：

```text
saas:portal:user:10001

saas:portal:captcha:xxxx

saas:portal:datasource:10001
```

禁止：

```text
user

data

cache

test
```

必须考虑：

```text
TTL

缓存失效

缓存穿透

缓存击穿

一致性

序列化
```

---

# 33. JSON

默认统一使用 Jackson。

如果项目已经使用 Jackson，不允许因为个人偏好增加：

```text
Fastjson

Fastjson2

Gson
```

禁止在业务代码中反复：

```java
new ObjectMapper();
```

优先使用 Spring 管理的 ObjectMapper。

---

# 34. 日志

后端统一：

```text
SLF4J
+
Logback
```

推荐：

```java
@Slf4j
```

禁止：

```java
System.out.println(...)

e.

printStackTrace()
```

日志不得输出：

```text
密码

JWT

Token

AccessKey

SecretKey

数据库密码

完整敏感连接信息
```

---

# 35. 后端 REST API

默认采用清晰 REST 风格。

例如：

```text
GET    /api/v1/datasources

GET    /api/v1/datasources/{id}

POST   /api/v1/datasources

PUT    /api/v1/datasources/{id}

DELETE /api/v1/datasources/{id}
```

业务动作允许：

```text
POST /api/v1/datasources/{id}/test

POST /api/v1/datasources/{id}/enable

POST /api/v1/datasources/{id}/disable
```

不要为了 REST 形式牺牲业务表达。

---

# 36. 后端统一响应

优先复用项目已有：

```text
Result<T>

PageResult<T>
```

禁止再创建重复体系：

```text
AjaxResult

ApiResponse

CommonResult

ResponseData
```

---

# 37. 禁止后端过度设计

未经明确需求，不主动引入：

```text
DDD

CQRS

Event Sourcing

领域事件

六边形架构

复杂 Repository

大量 Factory

大量 Strategy

大量 AbstractXXX
```

当前默认：

```text
Controller
    ↓
Service
    ↓
Mapper
```

复杂业务未来可以逐步演进。

---

# 38. 前端总体架构

前端位于：

```text
saas-portal/frontend/
```

默认采用：

```text
Vue 3
+
TypeScript
+
Vite
+
Vue Router
+
Pinia
+
Axios
+
Element Plus
```

如果现有项目技术栈不同，以：

```text
frontend/package.json
```

为准。

禁止 AI 擅自切换技术框架。

---

# 39. 前端目录

推荐：

```text
frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── public/
└── src/
    ├── api/
    ├── assets/
    ├── components/
    ├── composables/
    ├── directives/
    ├── layouts/
    ├── router/
    ├── stores/
    ├── styles/
    ├── types/
    ├── utils/
    ├── views/
    ├── App.vue
    └── main.ts
```

实际目录优先遵循项目已有结构。

---

# 40. 前端业务目录

`views` 按业务领域划分。

例如：

```text
views/
├── system/
├── datasource/
├── metadata/
├── develop/
├── integration/
└── schedule/
```

业务页面和业务组件尽量放在同一业务领域附近。

例如：

```text
views/
└── datasource/
    ├── index.vue
    ├── detail.vue
    └── components/
        ├── DatasourceForm.vue
        └── DatasourceTable.vue
```

---

# 41. 前端组件分类

组件分成：

```text
页面组件

业务组件

全局通用组件
```

页面：

```text
views/
```

当前业务复用：

```text
views/xxx/components/
```

真正跨业务通用：

```text
components/
```

不要看到少量重复就立即抽全局组件。

---

# 42. Vue 3 规范

新增代码默认采用：

```vue

<script setup lang="ts">
```

优先 Composition API。

例如：

```vue

<script setup lang="ts">
  import {ref} from 'vue'

  const loading = ref(false)
</script>
```

如果旧模块已有明确编码风格：

> 修改旧代码时优先保持一致，不要为了统一而大规模重构。

---

# 43. TypeScript

新增代码必须尽量保证完整类型。

禁止大量：

```ts
any
```

推荐：

```ts
export interface DatasourceVO {
    id: number
    name: string
    type: string
}
```

无法确认类型时优先考虑：

```ts
unknown
```

再进行类型收窄。

---

# 44. 前端 API

HTTP 请求必须统一放入项目 API 层。

例如：

```text
src/api/datasource.ts
```

推荐：

```ts
export function getDatasourceList(
    params: DatasourceQuery
) {
    return request.get('/api/v1/datasources', {
        params
    })
}
```

禁止页面中随处：

```ts
axios.get(...)
```

Vue 页面应调用统一 API 方法。

---

# 45. Axios

必须复用项目统一 Request 实例。

例如：

```text
src/utils/request.ts
```

统一处理：

```text
baseURL

Token

请求 Header

超时

401

403

HTTP 错误

通用业务异常
```

禁止每个业务模块自行：

```ts
axios.create(...)
```

---

# 46. 前端错误处理

通用错误：

```text
网络异常

401

403

500

通用业务异常
```

应由统一 HTTP 层处理。

页面只处理具有业务意义的异常。

禁止每个页面重复：

```ts
try {
...
} catch {
    ElMessage.error('请求失败')
}
```

如果统一层已经处理相同错误。

---

# 47. Pinia

Pinia 只用于真正的全局状态。

适合：

```text
当前用户

Token

权限

菜单

主题

全局应用状态
```

不适合：

```text
Dialog 是否显示

当前页面查询条件

某个表单临时值

表格局部 loading
```

原则：

> 能局部，就不要全局。

---

# 48. Vue Router

路由统一在：

```text
router/
```

管理。

页面优先懒加载：

```ts
component: () =>
    import('@/views/datasource/index.vue')
```

路由需要考虑：

```text
path

name

title

权限

菜单

页面缓存

懒加载
```

---

# 49. 前端权限

优先使用现有统一机制。

例如：

```text
路由权限

菜单权限

按钮权限

角色权限
```

如果已有：

```text
v-permission

v-auth

usePermission
```

必须优先复用。

禁止各页面自行写一套角色判断逻辑。

---

# 50. Element Plus

UI 优先使用项目已有 Element Plus。

例如：

```text
ElTable

ElForm

ElDialog

ElDrawer

ElSelect

ElTree

ElPagination

ElMessage

ElMessageBox
```

禁止在 Element Plus 能正常满足需求时再引入：

```text
Ant Design Vue

Naive UI

Vuetify
```

等另一套完整 UI 框架。

---

# 51. 前端表单

表单必须考虑：

```text
model

rules

loading

重复提交

初始化

重置

取消

错误提示
```

例如：

```ts
const form = reactive<DatasourceCreateRequest>({
    name: '',
    type: ''
})
```

提交过程中必须避免重复提交。

---

# 52. 前端表格

列表页面必须考虑：

```text
loading

空数据

分页

查询

重置

刷新

操作列

权限
```

禁止明显可能存在大量数据时一次性请求全部记录。

---

# 53. Dialog / Drawer

弹窗必须考虑：

```text
打开初始化

编辑数据回显

关闭清理

Form Reset

提交 loading

请求失败
```

禁止关闭后残留上一次编辑数据。

---

# 54. Composable

真正具有复用价值的逻辑可以放：

```text
composables/
```

例如：

```text
usePagination

usePermission

useTableSelection
```

不要为了几个简单变量创建 Composable。

---

# 55. 前端 Utils

`utils` 只允许真正通用的工具。

例如：

```text
date

string

storage

request

download

validation
```

禁止把具体业务逻辑放：

```text
datasourceUtils

taskUtils

metadataBusinessUtils
```

具体业务逻辑应留在业务领域。

---

# 56. 前端样式

优先使用项目现有样式技术。

如果当前是：

```text
SCSS
```

继续 SCSS。

如果项目已经使用：

```text
UnoCSS
```

继续 UnoCSS。

如果项目已经使用：

```text
Tailwind CSS
```

继续 Tailwind。

禁止因为个人偏好新增另一套样式体系。

---

# 57. scoped 样式

Vue 页面和业务组件默认优先：

```vue

<style scoped>
</style>
```

全局样式放：

```text
styles/
```

禁止为了修改局部组件随意污染全局 CSS。

---

# 58. 前端命名规范

组件：

```text
DatasourceForm.vue

DatasourceTable.vue

DatasourceDetail.vue
```

变量：

```text
datasourceList

selectedDatasource

submitLoading
```

事件：

```text
handleCreate

handleEdit

handleDelete

handleSubmit
```

Boolean：

```text
isLoading

isVisible

hasPermission
```

禁止：

```text
data1

obj

tmp

flag1

info2
```

---

# 59. Props

Props 必须有明确类型。

例如：

```ts
interface Props {
    datasourceId: number
    readonly?: boolean
}

const props = defineProps<Props>()
```

禁止：

```ts
defineProps<any>()
```

作为默认做法。

---

# 60. Emits

事件应显式定义。

例如：

```ts
const emit = defineEmits<{
    success: []
    cancel: []
}>()
```

保持组件调用关系明确。

---

# 61. computed / watch

派生状态优先：

```ts
computed
```

不要使用 watch 手工同步本可计算得到的数据。

`watch` 主要用于：

```text
副作用

异步请求

与外部状态同步
```

避免大量 watch 互相触发形成隐式业务流程。

---

# 62. 异步请求

推荐：

```text
async / await
```

例如：

```ts
const loadData = async () => {
    loading.value = true

    try {
        const result = await getDatasourceList(query)
        datasourceList.value = result.records
    } finally {
        loading.value = false
    }
}
```

必须保证 loading 能正确恢复。

---

# 63. 防止重复操作

以下操作必须考虑重复点击：

```text
新增

编辑

删除

发布

执行

提交

测试连接
```

至少使用：

```text
loading

disabled
```

避免重复提交。

---

# 64. 日期处理

如果项目已经使用：

```text
Day.js
```

继续使用 Day.js。

不要因为一个日期格式化新增：

```text
Moment.js

date-fns
```

等另一套日期库。

---

# 65. 图表

如果项目已有：

```text
ECharts
```

业务图表继续使用 ECharts。

必须考虑：

```text
空数据

Resize

销毁

容器尺寸变化
```

禁止每个业务引入不同图表库。

---

# 66. Monaco Editor

如果项目已经用于：

```text
SQL

JSON

Script
```

等编辑场景，应优先复用现有 Monaco Editor 封装。

禁止每个页面重复初始化完整 Monaco。

---

# 67. 浏览器缓存

`localStorage`、`sessionStorage` 应优先通过项目已有统一封装。

例如：

```text
storage.ts
```

禁止业务代码到处直接：

```ts
localStorage.setItem(...)
```

认证 Token 必须遵守项目统一认证机制。

---

# 68. 前端安全

禁止：

```text
在前端代码写数据库密码

写 SecretKey

打印 Token

把真正 Secret 放 Vite 环境变量

执行服务端返回的 eval 内容
```

必须认识到：

> Vite 环境变量最终会进入浏览器代码，不能存真正 Secret。

---

# 69. 前端环境变量

优先使用：

```text
.env

.env.development

.env.production
```

例如：

```text
VITE_API_BASE_URL
```

实际命名以项目现有规范为准。

---

# 70. 前后端字段一致性

开发接口时必须同时检查：

```text
Backend DTO

Backend VO

Controller API

Frontend Type

Frontend API

Frontend Page
```

保证：

```text
字段名称一致

字段类型一致

NULL 语义一致

状态一致

分页一致
```

---

# 71. 前后端命名对应

例如后端：

```text
DatasourceCreateDTO

DatasourceUpdateDTO

DatasourceVO
```

前端可以：

```text
DatasourceCreateRequest

DatasourceUpdateRequest

DatasourceVO
```

或者遵循当前项目已有规范。

同一个概念不能在前后端出现完全不同的业务名称。

---

# 72. 分页

分页参数和响应必须以项目现有规范为准。

例如如果后端采用：

```text
pageNum
pageSize
```

前端必须保持：

```text
pageNum
pageSize
```

禁止前端自行改成：

```text
current
limit
```

除非统一转换层已有明确设计。

---

# 73. 状态枚举

前后端状态语义必须统一。

例如：

```text
ENABLED

DISABLED

RUNNING

SUCCESS

FAILED
```

禁止后端：

```text
SUCCESS
```

前端却使用：

```text
FINISHED
```

表示同一个状态。

---

# 74. 删除操作

前端删除需要考虑：

```text
二次确认

loading

成功提示

列表刷新

错误处理
```

后端删除需要考虑：

```text
数据是否存在

是否允许删除

关联数据

权限

事务
```

禁止只机械执行数据库 DELETE。

---

# 75. 权限安全

权限必须同时考虑：

```text
前端展示控制
+
后端真实权限校验
```

前端隐藏按钮不是安全机制。

最终权限必须以后端为准。

---

# 76. MySQL

数据库默认使用：

```text
MySQL
```

新增表必须考虑：

```text
主键

唯一约束

索引

字段类型

长度

NULL

默认值

审计字段
```

避免：

```text
所有字段 varchar(255)

所有字段允许 NULL

没有索引

SELECT *
```

---

# 77. SQL 性能

SQL 必须考虑：

```text
索引

分页

查询范围

排序

JOIN

批量操作

N+1

大数据量
```

禁止明显：

```java
for(...){
mapper.select...
        }
```

导致大量数据库查询。

---

# 78. System File 能力

文件管理统一属于：

```text
platform-system/file
```

业务模块应通过统一接口使用文件服务。

业务代码禁止直接依赖具体：

```text
MinIO

S3

本地文件系统
```

实现。

---

# 79. Auth 能力

认证统一属于：

```text
platform-system/auth
```

业务模块禁止重新实现：

```text
JWT

登录

用户

角色

权限
```

应复用 System 提供的统一能力。

---

# 80. 后端依赖选择

技术选择优先级：

```text
项目已有实现
    ↓
Spring Boot 已有能力
    ↓
项目已有第三方库
    ↓
JDK 标准库
    ↓
新增第三方依赖
```

---

# 81. 前端依赖选择

优先级：

```text
当前业务已有实现
    ↓
已有公共组件
    ↓
已有 Composable
    ↓
已有 Utils
    ↓
Element Plus / Vue 官方能力
    ↓
项目已经安装的库
    ↓
新增依赖
```

---

# 82. 禁止擅自替换后端技术栈

未经用户明确要求，禁止：

```text
MyBatis-Plus → JPA

Spring MVC → WebFlux

Maven → Gradle

Jackson → Fastjson

MySQL → PostgreSQL

MVC → DDD

模块化单体 → 微服务
```

---

# 83. 禁止擅自替换前端技术栈

未经明确要求，禁止：

```text
Vue → React

Vue → Angular

Pinia → Vuex

Element Plus → Ant Design Vue

Vite → Webpack

TypeScript → JavaScript
```

---

# 84. 新增依赖

新增 Maven 或 npm 依赖前必须判断：

```text
项目是否已有相同能力？

框架自身是否能解决？

标准库是否能解决？

新增依赖是否真的有价值？
```

禁止为了一个简单方法引入大型第三方库。

---

# 85. 后端版本

所有版本必须读取：

```text
backend/pom.xml

parent pom

dependencyManagement
```

禁止猜测：

```text
Java Version

Spring Boot Version

MyBatis-Plus Version

SDK Version
```

---

# 86. 前端版本

必须读取：

```text
frontend/package.json

pnpm-lock.yaml

package-lock.json

yarn.lock
```

确定：

```text
Vue

TypeScript

Vite

Element Plus

其他依赖
```

版本。

---

# 87. 前端包管理器

必须根据 lock 文件确定。

```text
pnpm-lock.yaml
    → pnpm

package-lock.json
    → npm

yarn.lock
    → yarn
```

禁止项目使用 pnpm 时 AI 自行：

```bash
npm install
```

---

# 88. Docker

项目统一使用：

```text
docker-compose.yaml
```

作为 Docker Compose 编排入口。

可以根据项目实际情况包含：

```text
frontend

backend

mysql

redis

minio

其他基础设施
```

---

# 89. 后端 Docker 部署单位

虽然 backend 内有：

```text
platform-boot

platform-common

platform-system

platform-business
```

但它们属于同一个应用。

最终只有：

```text
platform-boot.jar
```

一个后端部署单位。

禁止把 Maven Module 当成 Docker Service。

---

# 90. 配置安全

禁止在代码或 Docker 镜像中硬编码：

```text
数据库密码

Redis 密码

JWT Secret

AccessKey

SecretKey

第三方 Token
```

后端敏感配置应优先来自：

```text
环境变量

application-xxx.yml

配置系统
```

前端不得保存真正服务端 Secret。

---

# 91. 后端测试

默认：

```text
JUnit 5

Mockito

Spring Boot Test
```

Service 单元测试优先 Mockito。

只有确实需要 Spring Context 时使用：

```java
@SpringBootTest
```

禁止所有测试无脑使用 SpringBootTest。

---

# 92. 前端测试

如果项目已经使用：

```text
Vitest

Vue Test Utils

Playwright
```

等测试体系，应继续沿用。

如果当前没有前端自动化测试体系：

> 不要为了普通业务需求自行引入完整测试技术栈。

---

# 93. 后端构建验证

修改后端代码后，如果环境允许，应根据项目实际配置执行：

```bash
mvn compile
```

或者：

```bash
mvn test
```

具体命令应先确认项目结构。

---

# 94. 前端构建验证

先读取：

```text
frontend/package.json
```

确认项目实际 scripts。

根据实际存在的命令执行：

```text
lint

typecheck

test

build
```

禁止凭空假设：

```bash
pnpm typecheck
```

一定存在。

---

# 95. 修改后的后端检查

至少检查：

```text
Java 编译

Import

Maven 依赖

循环依赖

空指针风险

事务

SQL

参数校验

异常处理

API 兼容性
```

---

# 96. 修改后的前端检查

至少检查：

```text
TypeScript

Import

未使用变量

组件引用

API 地址

请求参数

响应类型

loading

错误处理

权限

路由

构建
```

---

# 97. 禁止伪造验证结果

如果没有实际执行：

```text
compile

test

lint

build
```

禁止说：

```text
编译已通过

测试全部通过

构建成功
```

应明确说明真实状态。

例如：

```text
已完成代码检查，但当前未实际执行 Maven 编译。
```

---

# 98. Git 行为

未经用户明确要求，AI 禁止：

```text
git push

git force push

git merge

git rebase

git reset --hard

删除分支

修改 Git 历史
```

允许根据任务需要使用：

```bash
git status

git diff

git log
```

修改完成后优先检查：

```bash
git diff
```

确认没有修改无关代码。

---

# 99. 禁止重复造轮子

新增以下内容前必须搜索项目：

```text
Utils

Converter

Exception

Result

HTTP Client

Table Component

Dialog Component

Composable

权限工具

分页工具
```

确认没有已有实现后才能新增。

---

# 100. 注释

注释主要用于说明：

```text
为什么这样设计

特殊业务规则

技术限制

边界情况
```

禁止：

```java
// 查询用户
User user = getUser();
```

这种重复代码语义的无意义注释。

---

# 101. TODO

不要随意留下：

```text
TODO

FIXME
```

当前任务可以完成时应直接完成。

只有存在真正外部依赖、当前无法处理的前置条件时才允许保留。

---

# 102. 删除代码

删除现有代码前必须确认：

```text
是否存在引用

是否是公共 API

是否由配置引用

是否通过反射使用

是否涉及数据兼容

是否影响前后端
```

禁止仅因为 IDE 看起来没有引用就进行大规模删除。

---

# 103. 重构

重构应保证：

```text
行为基本不变

API 尽量兼容

修改范围可控

不存在隐藏副作用
```

除非需求本身要求改变业务行为。

---

# 104. 发现现有代码违反规范

如果发现项目现有代码违反本规范：

不要擅自进行全项目整改。

应该：

```text
1. 优先完成当前任务。

2. 当前任务必须涉及时进行局部修复。

3. 明确说明发现的问题。

4. 给出后续优化建议。
```

---

# 105. 后端新功能开发顺序

优先按照：

```text
确定业务模块
    ↓
检查已有类似实现
    ↓
确定 DTO / VO
    ↓
Controller
    ↓
Service
    ↓
Mapper
    ↓
Entity / Database
```

涉及跨模块：

```text
当前业务模块
    ↓
目标模块 API
    ↓
目标模块 Service
```

---

# 106. 前端新功能开发顺序

优先按照：

```text
确定业务页面
    ↓
检查已有页面 / 组件
    ↓
确定 TypeScript 类型
    ↓
确定 API
    ↓
页面状态
    ↓
UI
    ↓
权限
    ↓
loading / 异常
```

---

# 107. CRUD 默认实现

普通后端 CRUD 可根据实际需求包含：

```text
XXXController

XXXService

XXXServiceImpl

XXXMapper

XXXEntity

XXXCreateDTO

XXXUpdateDTO

XXXQueryDTO

XXXVO
```

不要机械创建没有任何用途的类。

前端根据复杂度可包含：

```text
api

types

index.vue

components/XXXForm.vue
```

同样禁止为了形式创建无价值文件。

---

# 108. AI 执行要求

用户要求开发功能时：

> AI 应优先实际阅读和修改项目代码，而不是只提供理论方案。

存在多种实现时：

> 优先采用与当前项目已有代码最一致的方案。

发现规范冲突时：

> 明确指出冲突，不允许偷偷改变项目架构。

---

# 109. AI 禁止行为

未经明确要求，AI 不允许：

```text
擅自改变整体架构

擅自将 MVC 改成 DDD

擅自拆微服务

擅自新增 Maven Module

擅自替换前端框架

擅自引入大型依赖

跨模块直接调用 Mapper

Controller 直接调用 Mapper

业务代码放入 common

Entity 直接作为外部 API Response

system 依赖 business

制造循环依赖

修改无关代码

删除已有功能

修改 Git 历史

伪造测试和构建结果
```

---

# 110. 最终项目架构

```text
saas-portal/
│
├── docs/
│
├── AGENT.md
│
├── frontend/
│       │
│       │ Vue 3 + TypeScript
│       │
│       │ HTTP / REST
│       ▼
│
├── backend/
│       │
│       │ Spring Boot
│       │
│       ├── platform-boot
│       │
│       ├── platform-system
│       │
│       ├── platform-business
│       │
│       └── platform-common
│               │
│               ├── MySQL
│               └── Redis
│
└── docker-compose.yaml
```

后端：

```text
Spring Boot
+
Maven 多模块
+
模块化单体
+
MVC
+
MyBatis-Plus
+
单 JAR
```

前端：

```text
Vue 3
+
TypeScript
+
Vite
+
Vue Router
+
Pinia
+
Axios
+
Element Plus
```

部署：

```text
Docker
+
Docker Compose
```

---

# 111. 最终准则

每次修改代码前，都必须检查：

```text
这个功能属于哪个业务模块？

是否已经存在类似实现？

是否重复造轮子？

是否跨越了模块边界？

是否引入不必要复杂度？

是否擅自改变技术栈？

是否修改了无关代码？

前后端字段是否一致？

权限是否完整？

异常是否正确处理？

loading 是否正确处理？

事务是否合理？

SQL 是否合理？

是否能够正常编译 / 构建？
```

如果同时存在：

```text
复杂但高级的方案
```

和：

```text
简单、清晰、能够满足需求的方案
```

默认选择后者。

---

# 112. 项目目标

本项目不追求架构炫技。

最终目标是保持：

**结构清晰、规范统一、业务边界明确、前后端职责清晰、代码低耦合、易于维护、易于测试、易于部署，并为未来可能的业务模块拆分与系统演进保留空间。
**