# M0 Foundation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the M0 engineering baseline for `west-flow-ai`: import the `shadcn-admin` frontend foundation, scaffold the single-app Spring Boot backend, freeze the shared M0 contracts in code, and deliver a minimal approval demo that can start parallel M1 work.

**Architecture:** The frontend lives in `frontend/` and is a direct secondary build on top of `shadcn-admin` with TanStack Router, TanStack Query, Zustand, React Hook Form, Zod, and shared CRUD/list-page primitives. All visible menu and page copy is Chinese-only for this phase, with no i18n abstraction. The backend lives in `backend/` as one Spring Boot application organized by bounded-context packages, with PostgreSQL, Flyway, Redis, MinIO, Sa-Token, Flowable, LiteFlow, Aviator, and a contract-first API surface.

**Tech Stack:** `pnpm`, `Vite`, `React 19`, `TanStack Router`, `TanStack Query`, `zustand`, `react-hook-form`, `zod`, `shadcn/ui`, `Spring Boot`, `MyBatis-Plus`, `PostgreSQL`, `Flyway`, `Redis`, `MinIO`, `Flowable`, `LiteFlow`, `Google Aviator`, `Spring AI`, `Sa-Token`

---

## File Structure Lock

Expected repo shape after M0:

```text
/
├─ docs/contracts/*
├─ docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md
├─ docs/superpowers/plans/2026-03-21-m0-foundation-plan.md
├─ frontend/
│  ├─ package.json
│  ├─ components.json
│  ├─ src/components/layout/*
│  ├─ src/components/data-table/*
│  ├─ src/features/shared/table/*
│  ├─ src/features/shared/auth/*
│  ├─ src/lib/api/*
│  ├─ src/routes/_authenticated/*
│  └─ src/stores/auth-store.ts
├─ backend/
│  ├─ pom.xml
│  ├─ src/main/java/com/westflow/*
│  ├─ src/main/resources/*
│  │  └─ db/migration/V1__init.sql
│  └─ src/test/java/com/westflow/*
├─ infra/
│  ├─ docker-compose.yml
│  └─ env/.env.example
├─ scripts/
│  ├─ bootstrap-frontend.sh
│  └─ validate-contracts.sh
└─ .github/workflows/ci.yml
```

## Chunk 1: Repo Bootstrap

### Task 1: Import `shadcn-admin` into `frontend/`

**Files:**
- Create: `frontend/*` from the upstream `shadcn-admin` snapshot
- Modify: `frontend/package.json`
- Modify: `frontend/components.json`
- Modify: `frontend/README.md`

- [ ] **Step 1: Clone upstream into a temporary directory**

Run: `rm -rf /tmp/west-flow-ai-frontend && git clone --depth 1 https://github.com/satnaing/shadcn-admin.git /tmp/west-flow-ai-frontend`
Expected: clone completes successfully and `/tmp/west-flow-ai-frontend/package.json` exists

- [ ] **Step 2: Copy the snapshot into `frontend/` without nested git metadata**

Run: `mkdir -p frontend && rsync -a --exclude '.git' --exclude '.github' /tmp/west-flow-ai-frontend/ frontend/`
Expected: `frontend/package.json`, `frontend/components.json`, `frontend/src/` exist

- [ ] **Step 3: Rename the frontend package and keep the upstream build scripts**

Update `frontend/package.json`:

```json
{
  "scripts": {
    "typecheck": "tsc --noEmit",
    "test": "vitest run"
  },
  "name": "west-flow-ai-frontend"
}
```

- [ ] **Step 4: Record the baseline import decision**

Update `frontend/README.md` to state that `frontend/` is a direct secondary build on top of `shadcn-admin`, but all product routes, menu data, auth shape, CRUD page patterns, data contracts, and Chinese-only UI copy are project-owned.

- [ ] **Step 5: Install frontend dependencies**

Run: `pnpm --dir frontend install --frozen-lockfile`
Expected: install succeeds without lockfile drift

- [ ] **Step 6: Verify the imported baseline still builds**

Run: `pnpm --dir frontend lint && pnpm --dir frontend build`
Expected: both commands pass

- [ ] **Step 6.1: Add the frontend typecheck gate**

Run: `pnpm --dir frontend typecheck`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend
git commit -m "chore: import frontend admin baseline"
```

### Task 2: Establish root repo scaffolding

**Files:**
- Create: `.gitignore`
- Create: `README.md`
- Create: `.github/workflows/ci.yml`
- Create: `infra/env/.env.example`
- Create: `scripts/bootstrap-frontend.sh`
- Create: `scripts/validate-contracts.sh`

- [ ] **Step 1: Create a root `.gitignore`**

Include at minimum:

```gitignore
node_modules
frontend/node_modules
frontend/dist
backend/target
.idea
.DS_Store
.env
```

- [ ] **Step 2: Create the root `README.md`**

Document:

- monorepo structure
- frontend and backend startup commands
- docs entrypoints
- the M0 contract files in `docs/contracts`
- the phase rule that frontend visible copy is Chinese-only and does not introduce i18n

- [ ] **Step 3: Add a minimal CI workflow**

Create `.github/workflows/ci.yml` with these jobs:

- `frontend-lint-build`
- `frontend-typecheck`
- `backend-test`

- [ ] **Step 4: Add environment template**

Create `infra/env/.env.example` with placeholder values for:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`
- `MINIO_ROOT_USER`
- `MINIO_ROOT_PASSWORD`

- [ ] **Step 5: Verify**

Create lightweight helper scripts:

- `scripts/bootstrap-frontend.sh` for importing the `shadcn-admin` snapshot into `frontend/`
- `scripts/validate-contracts.sh` for checking that the M0 contract docs exist before parallel implementation starts

- [ ] **Step 6: Verify**

Run: `git status --short`
Expected: only the intended repo bootstrap files are staged or visible

- [ ] **Step 7: Commit**

```bash
git add .gitignore README.md .github/workflows/ci.yml infra/env/.env.example scripts/bootstrap-frontend.sh scripts/validate-contracts.sh
git commit -m "chore: add root repo scaffolding"
```

## Chunk 2: Frontend Foundation

### Task 3: Add the frontend test harness required for M0 TDD

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/vitest.config.ts`
- Create: `frontend/src/test/setup.ts`

- [ ] **Step 1: Add test dependencies**

Run: `pnpm --dir frontend add -D vitest jsdom @testing-library/react @testing-library/jest-dom`
Expected: dependencies are added to `frontend/package.json`

- [ ] **Step 2: Add the frontend test script**

Update `frontend/package.json`:

```json
{
  "scripts": {
    "test": "vitest run"
  }
}
```

- [ ] **Step 3: Create the Vitest config**

Create `frontend/vitest.config.ts` with a jsdom test environment and alias support for `@/`.

- [ ] **Step 4: Create the shared test setup file**

Create `frontend/src/test/setup.ts` to load `@testing-library/jest-dom`.

- [ ] **Step 5: Verify**

Run: `pnpm --dir frontend test`
Expected: PASS with zero or placeholder tests discovered

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/vitest.config.ts frontend/src/test/setup.ts
git commit -m "test: add frontend vitest harness"
```

### Task 4: Replace demo auth shape with the M0 auth contract

**Files:**
- Modify: `frontend/src/stores/auth-store.ts`
- Create: `frontend/src/features/shared/auth/types.ts`
- Create: `frontend/src/lib/api/client.ts`
- Create: `frontend/src/lib/api/auth.ts`
- Test: `frontend/src/stores/auth-store.test.ts`

- [ ] **Step 1: Write a failing auth-store contract test**

Create `frontend/src/stores/auth-store.test.ts` to verify:

- token is read from cookie
- `currentUser` matches the `docs/contracts/auth.md` shape
- `currentUser.mobile`, `email`, `avatar`, `dataScopes`, `partTimePosts`, `delegations` are preserved
- `aiCapabilities` and `menus` are retained
- switch-context performs a successful `POST /api/v1/auth/switch-context` and then refreshes via `GET /api/v1/auth/current-user`

- [ ] **Step 2: Run the test to confirm the current demo store is incompatible**

Run: `pnpm --dir frontend test -- auth-store`
Expected: FAIL because the imported store only contains demo fields like `accountNo` and `role`

- [ ] **Step 3: Introduce shared auth types**

Create `frontend/src/features/shared/auth/types.ts` with:

```ts
export type CurrentUser = {
  userId: string
  username: string
  displayName: string
  mobile: string
  email: string
  avatar: string
  companyId: string
  activePostId: string
  activeDepartmentId: string
  roles: string[]
  permissions: string[]
  dataScopes: Array<{ scopeType: string; scopeValue: string }>
  partTimePosts: Array<{ postId: string; departmentId: string; postName: string }>
  delegations: Array<{ principalUserId: string; delegateUserId: string; status: string }>
  aiCapabilities: string[]
  menus: Array<{ id: string; title: string; path: string }>
}
```

- [ ] **Step 4: Rewrite the auth store against the contract**

Modify `frontend/src/stores/auth-store.ts` to store:

- `accessToken`
- `currentUser`
- `setCurrentUser`
- `setAccessToken`
- `reset`

- [ ] **Step 5: Add API client and auth fetcher**

Create:

- `frontend/src/lib/api/client.ts`
- `frontend/src/lib/api/auth.ts`

The client must attach `Authorization: Bearer <token>` and centralize error handling via the M0 error contract.

- [ ] **Step 5.1: Add context-switch support**

Extend `frontend/src/lib/api/auth.ts` with a `switchContext` API bound to `POST /api/v1/auth/switch-context`, then immediately re-fetch `GET /api/v1/auth/current-user` before updating the auth store.

- [ ] **Step 6: Run verification**

Run: `pnpm --dir frontend lint && pnpm --dir frontend typecheck && pnpm --dir frontend build`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/stores/auth-store.ts frontend/src/features/shared/auth/types.ts frontend/src/lib/api/client.ts frontend/src/lib/api/auth.ts frontend/src/stores/auth-store.test.ts
git commit -m "feat: align frontend auth store with m0 contract"
```

### Task 5: Create the shared list-page and table query foundation

**Files:**
- Create: `frontend/src/features/shared/table/query-state.ts`
- Create: `frontend/src/features/shared/table/use-list-search.ts`
- Create: `frontend/src/features/shared/table/list-page.tsx`
- Modify: `frontend/src/components/data-table/toolbar.tsx`
- Modify: `frontend/src/components/data-table/pagination.tsx`
- Test: `frontend/src/features/shared/table/query-state.test.ts`

- [ ] **Step 1: Write a failing query-state mapping test**

Create `frontend/src/features/shared/table/query-state.test.ts` to verify:

- TanStack Router search state maps to the `docs/contracts/table-query.md` shape
- page resets to `1` when filters or page size change
- keyword, sorts, groups round-trip correctly

- [ ] **Step 2: Run the test to confirm the shared query layer does not exist yet**

Run: `pnpm --dir frontend test -- query-state`
Expected: FAIL because `query-state.ts` is missing

- [ ] **Step 3: Implement the query-state adapter**

Create `frontend/src/features/shared/table/query-state.ts` with helpers:

- `parseListSearch`
- `serializeListSearch`
- `toPageRequest`

- [ ] **Step 4: Implement route-search persistence helpers**

Create `frontend/src/features/shared/table/use-list-search.ts` to:

- read search params from TanStack Router
- update search state
- preserve state when navigating back from create/edit/detail pages

- [ ] **Step 5: Introduce a reusable list-page shell**

Create `frontend/src/features/shared/table/list-page.tsx` that composes:

- top query bar
- table content
- pagination footer
- column visibility controls
- batch selection support
- permission-state control wrappers
- empty state
- loading state

- [ ] **Step 6: Update the imported table toolbar and pagination components to use the shared query contract**

Modify:

- `frontend/src/components/data-table/toolbar.tsx`
- `frontend/src/components/data-table/pagination.tsx`

- [ ] **Step 7: Verify**

Run: `pnpm --dir frontend lint && pnpm --dir frontend typecheck && pnpm --dir frontend build`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add frontend/src/features/shared/table frontend/src/components/data-table/toolbar.tsx frontend/src/components/data-table/pagination.tsx
git commit -m "feat: add shared list page and query state foundation"
```

### Task 6: Replace demo sidebar data with project-owned menu groups and standalone CRUD routes

**Files:**
- Modify: `frontend/src/components/layout/data/sidebar-data.ts`
- Modify: `frontend/src/components/layout/app-sidebar.tsx`
- Create: `frontend/src/routes/_authenticated/system/users/list.tsx`
- Create: `frontend/src/routes/_authenticated/system/users/create.tsx`
- Create: `frontend/src/routes/_authenticated/system/users/$userId.tsx`
- Create: `frontend/src/routes/_authenticated/system/users/$userId/edit.tsx`
- Create: `frontend/src/routes/_authenticated/system/roles/list.tsx`
- Create: `frontend/src/routes/_authenticated/system/departments/list.tsx`
- Create: `frontend/src/routes/_authenticated/system/posts/list.tsx`
- Create: `frontend/src/routes/_authenticated/workflow/definitions/list.tsx`
- Create: `frontend/src/routes/_authenticated/workflow/designer/index.tsx`
- Create: `frontend/src/routes/_authenticated/workbench/todo/list.tsx`

- [ ] **Step 1: Write one failing route smoke test**

Create a route smoke test that checks:

- `/system/users/list` renders
- `/system/users/create` renders
- `/system/users/$userId/edit` is a separate page

- [ ] **Step 2: Run the smoke test**

Run: `pnpm --dir frontend test -- routes`
Expected: FAIL because the project-owned routes do not exist yet

- [ ] **Step 3: Replace demo sidebar groups with project groups**

Modify `frontend/src/components/layout/data/sidebar-data.ts` so the sidebar contains:

- 工作台
- 组织管理
- 系统管理
- 流程管理
- OA
- AI Copilot

- [ ] **Step 4: Update the sidebar rendering to support the new menu tree**

Modify `frontend/src/components/layout/app-sidebar.tsx` only as needed; keep the imported layout composition intact.

- [ ] **Step 5: Create standalone CRUD route files**

Create the exact route files listed above and render placeholder pages that use the new `ListPage` shell instead of tabs or drawer-based editing.

- [ ] **Step 6: Run route generation and build verification**

Run: `pnpm --dir frontend build`
Expected: PASS and TanStack route generation completes

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/layout/data/sidebar-data.ts frontend/src/components/layout/app-sidebar.tsx frontend/src/routes/_authenticated
git commit -m "feat: add project menu tree and standalone crud route skeletons"
```

## Chunk 3: Backend Foundation

### Task 7: Scaffold the Spring Boot single-app backend

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/westflow/WestFlowApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `backend/src/test/java/com/westflow/WestFlowApplicationTests.java`

- [ ] **Step 1: Write a failing backend bootstrap test**

Create `backend/src/test/java/com/westflow/WestFlowApplicationTests.java`:

```java
@SpringBootTest
class WestFlowApplicationTests {
  @Test
  void contextLoads() {}
}
```

- [ ] **Step 2: Run the test to confirm the backend project does not exist yet**

Run: `mvn -f backend/pom.xml test`
Expected: FAIL because `backend/pom.xml` is missing

- [ ] **Step 3: Create `backend/pom.xml`**

Include dependencies for:

- Spring Web
- Validation
- MyBatis-Plus
- PostgreSQL
- Flyway
- Redis
- Sa-Token
- MinIO
- Flowable
- LiteFlow
- Aviator
- Spring AI
- Spring Boot Test

- [ ] **Step 4: Add the application entrypoint and config files**

Create:

- `backend/src/main/java/com/westflow/WestFlowApplication.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 4.1: Lock the Flyway migration rule for this phase**

Populate `V1__init.sql` as the only migration file for M0/M1. If schema changes are needed during this phase, update this file in place instead of creating `V2+` migrations.

- [ ] **Step 5: Run backend bootstrap verification**

Run: `mvn -f backend/pom.xml test`
Expected: PASS for `WestFlowApplicationTests`

- [ ] **Step 6: Commit**

```bash
git add backend
git commit -m "feat: scaffold backend spring boot foundation"
```

### Task 8: Implement the M0 common contracts in backend code

**Files:**
- Create: `backend/src/main/java/com/westflow/common/api/ApiResponse.java`
- Create: `backend/src/main/java/com/westflow/common/api/ApiErrorResponse.java`
- Create: `backend/src/main/java/com/westflow/common/error/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/westflow/common/query/PageRequest.java`
- Create: `backend/src/main/java/com/westflow/common/query/PageResponse.java`
- Create: `backend/src/main/java/com/westflow/common/query/FilterItem.java`
- Create: `backend/src/main/java/com/westflow/common/query/SortItem.java`
- Create: `backend/src/main/java/com/westflow/common/query/GroupItem.java`
- Create: `backend/src/main/java/com/westflow/identity/api/AuthController.java`
- Create: `backend/src/main/java/com/westflow/identity/api/PermissionProbeController.java`
- Create: `backend/src/main/java/com/westflow/identity/dto/CurrentUserResponse.java`
- Create: `backend/src/main/java/com/westflow/identity/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/westflow/identity/dto/LoginResponse.java`
- Create: `backend/src/main/java/com/westflow/identity/dto/SwitchContextRequest.java`
- Create: `backend/src/main/java/com/westflow/identity/security/SaTokenConfiguration.java`
- Create: `backend/src/main/java/com/westflow/system/audit/AuditLogInterceptor.java`
- Create: `backend/src/test/java/com/westflow/common/query/PageRequestTest.java`
- Create: `backend/src/test/java/com/westflow/common/error/GlobalExceptionHandlerTest.java`
- Create: `backend/src/test/java/com/westflow/identity/api/AuthControllerTest.java`

- [ ] **Step 1: Write a failing query contract test**

Create `backend/src/test/java/com/westflow/common/query/PageRequestTest.java` to assert:

- `page >= 1`
- `pageSize` only accepts allowed values
- `keyword` is optional
- `between` filters require exactly two values

- [ ] **Step 2: Write a failing auth contract test**

Create `backend/src/test/java/com/westflow/identity/api/AuthControllerTest.java` to assert `GET /api/v1/auth/current-user` returns:

- `POST /api/v1/auth/login` returns a bearer token
- `userId`
- `mobile`
- `email`
- `avatar`
- `permissions`
- `dataScopes`
- `partTimePosts`
- `delegations`
- `menus`
- `aiCapabilities`
- and `POST /api/v1/auth/switch-context` updates `activePostId`
- and a protected probe endpoint returns `403` when the permission is missing

- [ ] **Step 2.1: Write a failing global error-mapping test**

Create `backend/src/test/java/com/westflow/common/error/GlobalExceptionHandlerTest.java` to assert validation failures and permission failures map to `docs/contracts/errors.md`.

- [ ] **Step 3: Implement the common API envelopes and query DTOs**

Create the exact files listed above under `common/api` and `common/query`.

- [ ] **Step 4: Implement the auth contract surface**

Create:

- `AuthController`
- `CurrentUserResponse`
- `LoginRequest`
- `LoginResponse`
- `SwitchContextRequest`

The M0 implementation may use fixture-backed users, but it must still expose:

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/current-user`
- `POST /api/v1/auth/switch-context`

- [ ] **Step 4.1: Implement permission enforcement and a protected probe endpoint**

Create:

- `SaTokenConfiguration.java`
- `PermissionProbeController.java`

The probe endpoint is only for M0 verification and must require an explicit permission so the test suite can prove that backend permission enforcement is active.

- [ ] **Step 4.2: Implement unified exception mapping and basic audit logging**

Create:

- `GlobalExceptionHandler.java`
- `AuditLogInterceptor.java`

M0 only requires request-level audit logging for auth and protected endpoints, but the handler must already map validation and permission errors to the frozen error contract.

- [ ] **Step 5: Run backend tests**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/common backend/src/main/java/com/westflow/identity backend/src/test/java/com/westflow/common backend/src/test/java/com/westflow/identity
git commit -m "feat: implement m0 api query and auth contracts"
```

### Task 9: Add the minimal process runtime demo

**Files:**
- Create: `backend/src/main/java/com/westflow/processdef/api/ProcessDefinitionController.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Create: `backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Create: `backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java`
- Create: `backend/src/main/java/com/westflow/processruntime/service/ProcessDemoService.java`
- Test: `backend/src/test/java/com/westflow/processdef/service/ProcessDslValidatorTest.java`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write a failing DSL validator test**

Cover:

- one and only one `start`
- at least one `end`
- approver nodes require `assignment`
- all nodes are reachable from `start`
- isolated nodes are rejected
- `condition` nodes require at least two outgoing edges
- `parallel_split` and `parallel_join` must be paired

- [ ] **Step 2: Write a failing minimal runtime API test**

Assert there are endpoints for:

- publish DSL
- start demo process
- complete demo task

- [ ] **Step 3: Implement the DSL validator**

Create `ProcessDslValidator.java` against `docs/contracts/process-dsl.md`.

- [ ] **Step 4: Implement a first-pass DSL-to-BPMN adapter and demo runtime service**

The adapter may initially support only the M0 node set defined in the contract doc.

- [ ] **Step 5: Expose demo endpoints**

Create:

- `POST /api/v1/process-definitions/publish`
- `POST /api/v1/process-runtime/demo/start`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/complete`

- [ ] **Step 6: Run backend tests**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/westflow/processdef backend/src/main/java/com/westflow/processruntime backend/src/test/java/com/westflow/processdef backend/src/test/java/com/westflow/processruntime
git commit -m "feat: add m0 process definition and runtime demo"
```

## Chunk 4: Infra and Final Verification

### Task 10: Add local infra

**Files:**
- Create: `infra/docker-compose.yml`

- [ ] **Step 1: Write down the expected services in the compose file**

Required services:

- `postgres`
- `redis`
- `minio`

- [ ] **Step 2: Create `infra/docker-compose.yml`**

Expose local ports and mount the init SQL directory.

- [ ] **Step 3: Keep schema initialization in Flyway, not Docker init SQL**

Do not add `infra/postgres/init/*.sql`. Database schema initialization for this phase must live only in `backend/src/main/resources/db/migration/V1__init.sql`.

- [ ] **Step 4: Run local infra**

Run: `docker compose -f infra/docker-compose.yml up -d`
Expected: all services healthy

- [ ] **Step 5: Commit**

```bash
git add infra/docker-compose.yml
git commit -m "feat: add local infra and seed data"
```

### Task 11: Final M0 verification and handoff

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`
- Modify: `docs/contracts/*.md` as needed after verification

- [ ] **Step 1: Run frontend verification**

Run: `pnpm --dir frontend lint && pnpm --dir frontend typecheck && pnpm --dir frontend build`
Expected: PASS

- [ ] **Step 2: Run backend verification**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

- [ ] **Step 3: Run the local demo stack**

Run:

```bash
docker compose -f infra/docker-compose.yml up -d
mvn -f backend/pom.xml spring-boot:run
pnpm --dir frontend dev
```

Expected:

- frontend starts
- backend starts
- current-user stub works
- at least one project-owned standalone CRUD list route renders
- minimal approval demo API works end-to-end

- [ ] **Step 4: Update docs with any contract drift found during implementation**

Only change the spec or contract docs if the code proved a documented contract wrong.

- [ ] **Step 5: Commit**

```bash
git add README.md docs frontend backend infra
git commit -m "chore: complete m0 verification handoff"
```
