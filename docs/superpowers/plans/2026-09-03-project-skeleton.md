# saas-portal Project Skeleton Implementation Plan

> **For agentic workers:** Project-level instructions override the default Superpowers execution handoff. Implement this
> plan directly with Codex in the current workspace. Do not
> invoke `superpowers:subagent-driven-development`, `superpowers:executing-plans`, automatic subagents, or multi-round
> code review. Track progress with the checkboxes below.

**Goal:** Build a runnable Java 21/Spring Boot modular-monolith backend and a minimal Vue 3 frontend that displays
application, MySQL, and Redis health, with one Docker Compose file supporting built-in or external infrastructure.

**Architecture:** The backend is a four-module Maven reactor with one executable JAR in `platform-boot`; the frontend is
a Vite SPA that accesses Actuator through a single Axios client. One Compose file always defines frontend/backend and
gates MySQL/Redis behind the `local-infra` profile, while `.env` selects container or external connection addresses.

**Tech Stack:** Java 21.0.12, Maven 3.9.1, Spring Boot 4.1.1, MyBatis-Plus 3.5.17, MySQL, Redis, Vue 3.5.42, TypeScript
5.9.3, Vite 8.2.2, Vue Router 5.0.7, Pinia 3.0.4, Axios, Element Plus, npm.

**Spec:** `docs/superpowers/specs/2026-09-03-project-skeleton-design.md`

## Global Constraints

- Java source package is exactly `com.xtong.saas`; the sole application class is `com.xtong.saas.SaasPortalApplication`.
- Backend modules are exactly `platform-common`, `platform-system`, `platform-business`, and `platform-boot`; only boot
  produces an executable Spring Boot JAR.
- Use the local JDK at `D:\develop\environment\Java\jdk-21.0.12` and Maven
  at `D:\develop\environment\apache\maven\apache-maven-3.9.1`.
- Use the currently active Node 24.19.0 and npm 11.17.0; use npm only and generate `package-lock.json`.
- Do not add authentication, permissions, admin layout, business CRUD, business tables, Flyway, Liquibase,
  microservices, or additional root directories.
- Do not initialize Git, commit, push, or invoke any history-changing Git operation.
- Do not run Docker commands because Docker is unavailable locally. Docker files receive text-level static review only.
- Never commit a real `.env` or any real password, token, connection string, or secret.
- Health endpoint is `GET /api/v1/health`, reports application/database/Redis component statuses, and hides sensitive
  details.
- The frontend must distinguish `UP`, `DOWN`, and backend `UNREACHABLE` states.

## File Map

### Root

- `.gitignore`: excludes environment files, IDE state, Maven/Node outputs, logs, and OS files.
- `.env.example`: documents all Compose/runtime variables with safe local examples.
- `docker-compose.yaml`: defines frontend/backend plus profiled MySQL/Redis services.
- `README.md`: documents local development, both Compose modes, health semantics, and verification limits.

### Backend

- `backend/pom.xml`: parent, module order, Java/Spring Boot/MyBatis-Plus version management.
- `backend/platform-common/pom.xml`: common module descriptor.
- `backend/platform-common/src/main/java/com/xtong/saas/common/result/Result.java`: generic future business response.
- `backend/platform-common/src/test/java/com/xtong/saas/common/result/ResultTest.java`: response factory contract.
- `backend/platform-system/pom.xml`: system module descriptor and dependency on common.
- `backend/platform-business/pom.xml`: business module descriptor and dependency on common.
- `backend/platform-boot/pom.xml`: runtime dependencies and executable-JAR plugin.
- `backend/platform-boot/src/main/java/com/xtong/saas/SaasPortalApplication.java`: sole Spring Boot entry point.
- `backend/platform-boot/src/main/resources/application.yml`: environment-backed database, Redis, server, and Actuator
  configuration.
- `backend/platform-boot/src/test/java/com/xtong/saas/SaasPortalApplicationTest.java`: context-start contract without
  live infrastructure.
- `backend/Dockerfile`: multi-stage build producing one runtime JAR image.

### Frontend

- `frontend/package.json`: exact runtime and development dependencies plus dev/lint/typecheck/build scripts.
- `frontend/package-lock.json`: npm-resolved exact dependency graph.
- `frontend/index.html`: Vite HTML entry.
- `frontend/tsconfig.json`: strict application TypeScript configuration.
- `frontend/tsconfig.node.json`: Vite configuration TypeScript settings.
- `frontend/vite.config.ts`: `@` alias and `/api` development proxy.
- `frontend/eslint.config.js`: flat ESLint config for Vue and TypeScript.
- `frontend/src/env.d.ts`: Vite client types.
- `frontend/src/main.ts`: application, Router, Pinia, and Element Plus bootstrap.
- `frontend/src/App.vue`: root RouterView.
- `frontend/src/router/index.ts`: home and catch-all routes.
- `frontend/src/utils/request.ts`: only Axios instance.
- `frontend/src/types/health.ts`: Actuator health response types.
- `frontend/src/api/health.ts`: 200/503 health-response normalization.
- `frontend/src/views/HomeView.vue`: minimal health dashboard.
- `frontend/src/views/NotFoundView.vue`: minimal 404 route.
- `frontend/src/styles/index.css`: small global reset and page background.
- `frontend/Dockerfile`: Node build and Nginx runtime stages.
- `frontend/nginx.conf`: SPA fallback and backend API proxy.

---

### Task 1: Establish the Maven reactor and module boundaries

**Files:**

- Create: `backend/pom.xml`
- Create: `backend/platform-common/pom.xml`
- Create: `backend/platform-system/pom.xml`
- Create: `backend/platform-business/pom.xml`
- Create: `backend/platform-boot/pom.xml`

**Interfaces:**

- Consumes: Java 21 and Maven 3.9.1 from the global constraints.
- Produces: Maven coordinates `com.xtong.saas:saas-portal:1.0.0-SNAPSHOT`; four child modules; Boot 4.1.1 and
  MyBatis-Plus 3.5.17 dependency management.

- [ ] **Step 1: Create the root reactor POM**

Use `org.springframework.boot:spring-boot-starter-parent:4.1.1`, packaging `pom`, and this exact module order:

```xml

<modules>
    <module>platform-common</module>
    <module>platform-system</module>
    <module>platform-business</module>
    <module>platform-boot</module>
</modules>
```

Set these properties:

```xml

<java.version>21</java.version>
<maven.compiler.release>21</maven.compiler.release>
<mybatis-plus.version>3.5.17</mybatis-plus.version>
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
```

Import `com.baomidou:mybatis-plus-bom:${mybatis-plus.version}` in `dependencyManagement`.

- [ ] **Step 2: Create focused child POMs**

All children inherit `com.xtong.saas:saas-portal:1.0.0-SNAPSHOT`.

`platform-common` has no runtime dependency. `platform-system` and `platform-business` each depend on:

```xml

<dependency>
    <groupId>com.xtong.saas</groupId>
    <artifactId>platform-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

`platform-boot` depends on both internal modules and declares:

```xml

<dependency>
    <groupId>com.xtong.saas</groupId>
    <artifactId>platform-system</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
<groupId>com.xtong.saas</groupId>
<artifactId>platform-business</artifactId>
<version>${project.version}</version>
</dependency>
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
<groupId>com.baomidou</groupId>
<artifactId>mybatis-plus-spring-boot4-starter</artifactId>
</dependency>
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
<groupId>com.mysql</groupId>
<artifactId>mysql-connector-j</artifactId>
<scope>runtime</scope>
</dependency>
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-test</artifactId>
<scope>test</scope>
</dependency>
```

Configure `spring-boot-maven-plugin` only in `platform-boot` so only that module is repackaged.

- [ ] **Step 3: Validate the reactor before adding Java sources**

Run:

```powershell
$env:JAVA_HOME = 'D:\develop\environment\Java\jdk-21.0.12'
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml validate
```

Expected: `BUILD SUCCESS`, reactor order lists common, system, business, boot, then parent summary; no cyclic-dependency
error.

- [ ] **Step 4: Inspect the effective dependency direction**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml dependency:tree
```

Expected: common has no system/business dependency; system and business depend on common; boot depends on system and
business.

### Task 2: Add the common response contract with a unit test

**Files:**

- Modify: `backend/platform-common/pom.xml`
- Create: `backend/platform-common/src/main/java/com/xtong/saas/common/result/Result.java`
- Create: `backend/platform-common/src/test/java/com/xtong/saas/common/result/ResultTest.java`

**Interfaces:**

- Consumes: Java 21 records and JUnit Jupiter managed by Spring Boot.
- Produces: `Result<T>(int code, String message, T data)` and `Result.success(T data)`.

- [ ] **Step 1: Add the test dependency to common**

Add only:

```xml

<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write the failing response factory test**

```java
package com.xtong.saas.common.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultTest {

    @Test
    void successCreatesStandardSuccessResponse() {
        Result<String> result = Result.success("ready");

        assertEquals(0, result.code());
        assertEquals("success", result.message());
        assertEquals("ready", result.data());
    }
}
```

- [ ] **Step 3: Run the test and confirm the contract is missing**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-common test
```

Expected: compilation fails because `Result` does not exist.

- [ ] **Step 4: Implement the minimal response record**

```java
package com.xtong.saas.common.result;

public record Result<T>(int code, String message, T data) {

    private static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MESSAGE = "success";

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }
}
```

- [ ] **Step 5: Run the common module test**

Run the Step 3 command again.

Expected: one test passes and Maven reports `BUILD SUCCESS`.

### Task 3: Add the Spring Boot application and health configuration

**Files:**

- Create: `backend/platform-boot/src/main/java/com/xtong/saas/SaasPortalApplication.java`
- Create: `backend/platform-boot/src/main/resources/application.yml`
- Create: `backend/platform-boot/src/test/java/com/xtong/saas/SaasPortalApplicationTest.java`

**Interfaces:**

- Consumes: platform-system, platform-business, MySQL/Redis environment variables.
- Produces: executable `platform-boot` JAR and `GET /api/v1/health` Actuator endpoint.

- [ ] **Step 1: Write a context-start test that does not require live infrastructure**

```java
package com.xtong.saas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.data.redis.connect-timeout=100ms",
        "spring.data.redis.timeout=100ms"
})
class SaasPortalApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run the boot test and confirm the entry point is missing**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml -pl platform-boot -am test
```

Expected: test setup fails because no `@SpringBootConfiguration` is present.

- [ ] **Step 3: Add the sole application entry point**

```java
package com.xtong.saas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SaasPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaasPortalApplication.class, args);
    }
}
```

- [ ] **Step 4: Add environment-backed application configuration**

Use this structure in `application.yml`:

```yaml
server:
  port: ${BACKEND_PORT:8080}

spring:
  application:
    name: saas-portal
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:saas_portal}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USERNAME:saas_portal}
    password: ${MYSQL_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      initialization-fail-timeout: -1
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      connect-timeout: 2s
      timeout: 2s

management:
  endpoints:
    web:
      base-path: /api/v1
      exposure:
        include: health
  endpoint:
    health:
      show-components: always
      show-details: never
```

- [ ] **Step 5: Run tests and package the backend**

Run:

```powershell
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml test
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml package
```

Expected: both commands return `BUILD SUCCESS`; `backend/platform-boot/target/platform-boot-1.0.0-SNAPSHOT.jar` is
executable, while other module JARs are not Spring Boot fat JARs.

- [ ] **Step 6: Verify the health endpoint without claiming infrastructure is healthy**

Start the JAR in one terminal:

```powershell
$env:JAVA_HOME = 'D:\develop\environment\Java\jdk-21.0.12'
& 'D:\develop\environment\Java\jdk-21.0.12\bin\java.exe' -jar 'backend\platform-boot\target\platform-boot-1.0.0-SNAPSHOT.jar'
```

In another terminal:

```powershell
try {
    Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/health'
} catch {
    $_.ErrorDetails.Message
}
```

Expected with no local MySQL/Redis: a health JSON body with overall `DOWN` and component entries for database and Redis,
without connection URL, username, password, or stack trace. Stop the Java process after inspection.

### Task 4: Create the minimal Vue application shell

**Files:**

- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/index.html`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/eslint.config.js`
- Create: `frontend/src/env.d.ts`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/views/HomeView.vue`
- Create: `frontend/src/views/NotFoundView.vue`
- Create: `frontend/src/styles/index.css`

**Interfaces:**

- Consumes: Node 24.19.0 and npm 11.17.0.
- Produces: SPA routes `/` and `/:pathMatch(.*)*`; scripts `dev`, `lint`, `typecheck`, `build`, `preview`; Vite `/api`
  proxy.

- [ ] **Step 1: Create package metadata with explicit versions**

Use:

```json
{
  "name": "saas-portal-frontend",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "engines": {
    "node": ">=24.0.0"
  },
  "scripts": {
    "dev": "vite",
    "lint": "eslint .",
    "typecheck": "vue-tsc --noEmit",
    "build": "npm run typecheck && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "axios": "1.20.0",
    "element-plus": "2.14.5",
    "pinia": "3.0.4",
    "vue": "3.5.42",
    "vue-router": "5.0.7"
  },
  "devDependencies": {
    "@eslint/js": "10.0.1",
    "@types/node": "24.3.0",
    "@vitejs/plugin-vue": "6.0.8",
    "eslint": "10.6.0",
    "eslint-plugin-vue": "10.10.0",
    "globals": "17.11.0",
    "typescript": "5.9.3",
    "typescript-eslint": "8.69.0",
    "vite": "8.2.2",
    "vue-tsc": "3.3.11"
  }
}
```

- [ ] **Step 2: Install dependencies and generate the npm lock file**

Run from `frontend`:

```powershell
& 'D:\develop\environment\nodejs\npm.cmd' install
```

Expected: `package-lock.json` is generated. If the sandbox blocks registry access, request permission for this exact npm
install; do not switch package managers or silently change versions.

- [ ] **Step 3: Add TypeScript, Vite, and ESLint configuration**

`vite.config.ts` must define:

```ts
import {fileURLToPath, URL} from 'node:url'
import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
        },
    },
    server: {
        port: 5173,
        proxy: {
            '/api': 'http://localhost:8080',
        },
    },
})
```

`tsconfig.json` must
enable `strict`, `noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch`, `moduleResolution: "Bundler"`,
DOM/ES2022 libs, Vite client types, and the `@/* -> src/*` path alias. `tsconfig.node.json` includes `vite.config.ts`
and `eslint.config.js` with Node types.

`eslint.config.js` uses flat configs from `@eslint/js`, `eslint-plugin-vue`, and `typescript-eslint`, ignores `dist`,
and applies the Vue parser to `.vue` files. It must not introduce Prettier or another formatter.

- [ ] **Step 4: Add the application shell and two routes**

`main.ts` registers the router, Pinia, Element Plus, Element Plus CSS, and `src/styles/index.css`. `App.vue` renders
only `<RouterView />`.

`router/index.ts` exports a history router with:

```ts
{
    path: '/',
        name
:
    'home',
        component
:
    () => import('@/views/HomeView.vue'),
}
,
{
    path: '/:pathMatch(.*)*',
        name
:
    'not-found',
        component
:
    () => import('@/views/NotFoundView.vue'),
}
```

The initial `HomeView.vue` renders the title `SaaS Portal` and text `项目骨架已启动`; `NotFoundView.vue` renders `404`
and a RouterLink back to `/`.

- [ ] **Step 5: Validate the initial frontend shell**

Run from `frontend`:

```powershell
& 'D:\develop\environment\nodejs\npm.cmd' run lint
& 'D:\develop\environment\nodejs\npm.cmd' run typecheck
& 'D:\develop\environment\nodejs\npm.cmd' run build
```

Expected: all commands exit successfully and `frontend/dist/index.html` exists.

### Task 5: Implement the typed health client and status page

**Files:**

- Create: `frontend/src/utils/request.ts`
- Create: `frontend/src/types/health.ts`
- Create: `frontend/src/api/health.ts`
- Modify: `frontend/src/views/HomeView.vue`

**Interfaces:**

- Consumes: `GET /api/v1/health` Actuator response, including valid JSON on HTTP 503.
- Produces: `getHealth(): Promise<HealthResponse>` and visible application/database/Redis statuses.

- [ ] **Step 1: Define health response types**

```ts
export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN' | string

export interface HealthComponent {
    status: HealthStatus
    components?: Record<string, HealthComponent>
}

export interface HealthResponse extends HealthComponent {
    components?: Record<string, HealthComponent>
}
```

- [ ] **Step 2: Create the only Axios instance**

```ts
import axios from 'axios'

const request = axios.create({
    baseURL: '/',
    timeout: 5000,
})

export default request
```

- [ ] **Step 3: Normalize successful and degraded health responses**

```ts
import axios from 'axios'
import type {HealthResponse} from '@/types/health'
import request from '@/utils/request'

const isHealthResponse = (value: unknown): value is HealthResponse => {
    return typeof value === 'object'
        && value !== null
        && 'status' in value
        && typeof value.status === 'string'
}

export const getHealth = async (): Promise<HealthResponse> => {
    try {
        const response = await request.get<HealthResponse>('/api/v1/health')
        return response.data
    } catch (error: unknown) {
        if (axios.isAxiosError(error) && isHealthResponse(error.response?.data)) {
            return error.response.data
        }
        throw error
    }
}
```

- [ ] **Step 4: Build the minimal health page state**

`HomeView.vue` must use `onMounted`, `ref`, and `computed`; keep state local:

```ts
const loading = ref(false)
const health = ref<HealthResponse | null>(null)
const isReachable = ref(true)

const applicationStatus = computed(() => {
    if (!isReachable.value) return 'UNREACHABLE'
    return health.value?.status ?? 'UNKNOWN'
})

const componentStatus = (name: string): HealthStatus => {
    return health.value?.components?.[name]?.status ?? 'UNKNOWN'
}

const loadHealth = async () => {
    loading.value = true
    try {
        health.value = await getHealth()
        isReachable.value = true
    } catch {
        health.value = null
        isReachable.value = false
    } finally {
        loading.value = false
    }
}

onMounted(loadHealth)
```

Render three Element Plus status rows for application, MySQL (`db` component), and Redis (`redis` component), plus a
refresh button bound to `loading`. Map `UP` to success, `DOWN` to danger, and unknown/unreachable states to
warning/info. Do not show raw exception objects or response details.

- [ ] **Step 5: Re-run frontend quality gates**

Run the three commands from Task 4 Step 5.

Expected: lint, typecheck, and build all succeed; generated assets exist under `frontend/dist`.

- [ ] **Step 6: Exercise the unreachable state locally**

With no backend running, start the frontend:

```powershell
& 'D:\develop\environment\nodejs\npm.cmd' run dev
```

Open `http://localhost:5173`. Expected: page loads, application shows `UNREACHABLE`, database/Redis show `UNKNOWN`,
refresh is usable, and the page has no uncaught runtime error. Stop the dev server after inspection.

### Task 6: Add environment, Compose, Dockerfile, and Nginx definitions

**Files:**

- Create: `.env.example`
- Create: `.gitignore`
- Create: `docker-compose.yaml`
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`

**Interfaces:**

- Consumes: Maven boot artifact, frontend `dist`, variables named in `.env.example`.
- Produces: Mode 1 command `docker compose --profile local-infra up --build`; Mode 2
  command `docker compose up --build`; Nginx `/api` proxy to `backend:8080`.

- [ ] **Step 1: Define safe environment examples**

`.env.example` contains:

```dotenv
BACKEND_PORT=8080
FRONTEND_PORT=80

MYSQL_HOST=mysql
MYSQL_PORT=3306
MYSQL_DATABASE=saas_portal
MYSQL_USERNAME=saas_portal
MYSQL_PASSWORD=change_me
MYSQL_ROOT_PASSWORD=change_root_me

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
```

`.gitignore`
excludes `.env`, `.env.local`, `target/`, `node_modules/`, `dist/`, `.idea/`, `.vscode/`, `*.iml`, `*.log`, `.DS_Store`,
and `Thumbs.db`, while retaining `.env.example`.

- [ ] **Step 2: Create a single-JAR backend image definition**

Use a multi-stage `backend/Dockerfile` with `maven:3.9-eclipse-temurin-21` as builder and `eclipse-temurin:21-jre` as
runtime. Build with:

```dockerfile
RUN mvn -B -ntp clean package -DskipTests
```

Copy only `/workspace/platform-boot/target/platform-boot-1.0.0-SNAPSHOT.jar` to `/app/app.jar`, create/use an
unprivileged `spring` user, expose 8080, and run `java -jar /app/app.jar`.

- [ ] **Step 3: Create the frontend image and Nginx proxy**

Use `node:24-alpine` as builder and `nginx:1.28-alpine` as runtime. Run `npm ci` followed by `npm run build`, then
copy `dist` to `/usr/share/nginx/html`.

`nginx.conf` must contain:

```nginx
location / {
    try_files $uri $uri/ /index.html;
}

location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

- [ ] **Step 4: Define both Compose modes**

Create Compose services:

- `frontend`: build `./frontend`, publish `${FRONTEND_PORT:-80}:80`, depend only on backend.
- `backend`: build `./backend`, publish `${BACKEND_PORT:-8080}:8080`, pass all MySQL/Redis variables, and do not depend
  on mysql/redis.
- `mysql`: image `mysql:8.4`, profile `local-infra`, publish `${MYSQL_PORT:-3306}:3306`, configure
  database/user/password/root password, and use a named volume.
- `redis`: image `redis:7.4-alpine`, profile `local-infra`, publish `${REDIS_PORT:-6379}:6379`, pass `REDIS_PASSWORD`,
  conditionally enable `requirepass`, and use a named volume.

All four services share one named bridge network. Define only the MySQL and Redis named volumes. Do not add a second
Compose file.

- [ ] **Step 5: Perform text-level Docker static checks only**

Run:

```powershell
rg -n "profiles:|local-infra|MYSQL_HOST|REDIS_HOST|backend:8080|platform-boot-1.0.0-SNAPSHOT.jar" docker-compose.yaml backend\Dockerfile frontend\Dockerfile frontend\nginx.conf .env.example
rg -n "latest|password=.*[^}]$|secret|token" docker-compose.yaml backend\Dockerfile frontend\Dockerfile frontend\nginx.conf
```

Expected: first command shows consistent profile, environment, upstream, and artifact references. Second command finds
no `latest` tag or hard-coded production credential. Do not run Docker or claim the YAML/images work at runtime.

### Task 7: Add documentation and run the complete non-Docker verification

**Files:**

- Create: `README.md`
- Verify: all files listed in the File Map.

**Interfaces:**

- Consumes: completed backend, frontend, and deployment definitions.
- Produces: developer onboarding instructions and an evidence-backed verification report.

- [ ] **Step 1: Write the README with exact commands**

README sections:

1. Project scope and explicit non-goals.
2. Directory/module map and backend dependency direction.
3. Required local tools and the confirmed Windows paths.
4. Backend commands using the explicit JDK/Maven paths.
5. Frontend commands using npm.
6. `.env.example` copy/configuration instructions.
7. Compose Mode 1 and Mode 2 commands and host-value differences.
8. `/api/v1/health` meanings for `UP`, `DOWN`, `UNREACHABLE`.
9. Verification status, including the exact
   statement: `Docker 配置已生成，但因本机未安装 Docker，未进行 Compose 解析、镜像构建或容器运行验证。`

- [ ] **Step 2: Run the complete backend verification**

```powershell
$env:JAVA_HOME = 'D:\develop\environment\Java\jdk-21.0.12'
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml clean test
& 'D:\develop\environment\apache\maven\apache-maven-3.9.1\bin\mvn.cmd' -f backend\pom.xml package
```

Expected: both commands report `BUILD SUCCESS`; all tests pass; the executable boot JAR exists.

- [ ] **Step 3: Run the complete frontend verification**

```powershell
Push-Location frontend
& 'D:\develop\environment\nodejs\npm.cmd' run lint
& 'D:\develop\environment\nodejs\npm.cmd' run typecheck
& 'D:\develop\environment\nodejs\npm.cmd' run build
Pop-Location
```

Expected: all three scripts exit zero and `frontend/dist/index.html` exists.

- [ ] **Step 4: Check structure, secrets, and scope**

```powershell
rg --files | Sort-Object
rg -n "password|secret|token|accesskey" -g '!package-lock.json' -g '!*.md' .
rg -n "TODO|FIXME|TBD" -g '!docs/superpowers/**' .
```

Expected: files remain inside approved roots; secret scan finds only environment-variable names/default placeholders and
no real credentials; placeholder scan returns no implementation placeholders.

- [ ] **Step 5: Record truthful completion evidence**

Final handoff must list:

- backend commands executed and their actual result;
- frontend commands executed and their actual result;
- manual health/unreachable checks actually performed;
- Docker static checks performed;
- Docker commands not executed;
- any deviations from this plan, with reasons.

Do not claim any skipped command passed. Do not initialize or use Git as a substitute for the final file review.
