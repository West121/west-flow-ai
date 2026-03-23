# Advanced Runtime Actions Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Archive note (2026-03-23):** 本计划保留 demo 运行态时期的动作扩展思路；文中“旧内存运行态服务”“demo runtime service”仅代表历史实现背景，不代表当前正式运行态口径。

**Goal:** Implement one coherent advanced runtime slice covering add-sign, remove-sign, revoke, urge, real CC delivery/read, process-center filter expansion, and richer approval-sheet action timelines.

**Architecture:** Continue extending the single in-memory runtime backbone in `processruntime` instead of creating a second action engine. Model all new behaviors as explicit task/instance events plus small state extensions, so the same approval-sheet detail page and process-center lists can render richer runtime semantics without forking UI flows.

**Tech Stack:** React, TanStack Query, TanStack Router, React Hook Form, Zod, shadcn/ui, React Flow; Spring Boot, Sa-Token, PostgreSQL/Flyway baseline, existing demo runtime service and controller tests.

---

## File Structure Lock

This batch should stay inside the current runtime seams:

```text
backend/src/main/java/com/westflow/processruntime/
├─ api/*
└─ service/legacy-runtime-service.java（历史 demo 运行态实现，占位说明）

backend/src/test/java/com/westflow/processruntime/
└─ api/ProcessRuntimeControllerTest.java

frontend/src/features/workbench/
├─ approval-sheet-graph.tsx
├─ approval-sheet-helpers.ts
├─ approval-sheet-list.tsx
├─ pages.tsx
└─ pages.test.tsx

frontend/src/lib/api/workbench.ts
frontend/src/routes/_authenticated/workbench/*
```

## Chunk 1: Freeze Runtime Contracts Before Code

### Task 1: Align docs with the new advanced-runtime batch

**Files:**
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`
- Modify: `docs/contracts/task-actions.md`
- Create: `docs/superpowers/plans/2026-03-22-advanced-runtime-actions-plan.md`

- [ ] **Step 1: Update the spec**

Document this batch as:

- `加签`
- `减签`
- `撤销`
- `催办`
- `真实抄送`
- `抄送已阅`
- `流程中心筛选器增强`
- `审批单详情动作轨迹增强`

- [ ] **Step 2: Freeze the task-action contract**

Define:

- new task states
- new action names
- new timeline fields
- new filtering dimensions
- `抄送我` real-task semantics

- [ ] **Step 3: Validate docs**

Run:

```bash
./scripts/validate-contracts.sh
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add docs/contracts docs/superpowers/specs docs/superpowers/plans
git commit -m "docs: freeze advanced runtime actions scope"
```

## Chunk 2: Backend Runtime Actions

### Task 2: Add add-sign, remove-sign, revoke, and urge APIs

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/service/legacy-runtime-service.java`（历史 demo 运行态实现，占位说明）
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/AddSignTaskRequest.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/RemoveSignTaskRequest.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/RevokeTaskRequest.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/UrgeTaskRequest.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/TaskActionAvailabilityResponse.java`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write failing controller tests for add-sign**

Assert:

- current assignee can add-sign one extra user
- new add-sign task becomes visible in task detail trace
- original task is blocked from completion until add-sign task resolves

- [ ] **Step 2: Run the focused tests to verify red**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: FAIL with missing endpoints or assertions

- [ ] **Step 3: Write failing controller tests for remove-sign, revoke, and urge**

Assert:

- only pending add-sign tasks can be removed
- initiator can revoke an in-flight instance
- urge records an instance event without changing task status

- [ ] **Step 4: Implement minimal backend behavior**

Wire:

- request records
- controller endpoints
- availability flags
- status transitions
- instance events
- task trace updates

- [ ] **Step 5: Verify green**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime backend/src/test/java/com/westflow/processruntime
git commit -m "feat: add advanced runtime task actions"
```

## Chunk 3: Backend Real CC Model and Filter Expansion

### Task 3: Turn CC into real runtime data

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/service/legacy-runtime-service.java`（历史 demo 运行态实现，占位说明）
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ApprovalSheetPageRequest.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ApprovalSheetListItemResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskDetailResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskTraceItemResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessInstanceEventResponse.java`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write failing tests for CC delivery**

Assert:

- reaching a `cc` node creates real CC tasks
- `CC` approval-sheet view returns records instead of placeholder-only behavior
- CC tasks can be marked read

- [ ] **Step 2: Write failing tests for list filtering**

Assert support for:

- `businessType`
- `instanceStatus`
- `currentNodeName`
- `latestAction`
- `readStatus`
- `dateRange`

- [ ] **Step 3: Run focused tests to verify red**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: FAIL

- [ ] **Step 4: Implement the minimal model changes**

Add:

- CC task status/state
- read endpoint behavior
- richer trace/event payloads
- filter parsing and application

- [ ] **Step 5: Verify green**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime backend/src/test/java/com/westflow/processruntime
git commit -m "feat: add real cc runtime model and filters"
```

## Chunk 4: Frontend Process Center and Approval-Sheet Detail

### Task 4: Surface the new runtime capabilities in one shared UI

**Files:**
- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `frontend/src/features/workbench/approval-sheet-list.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-helpers.ts`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-graph.tsx`
- Test: `frontend/src/features/workbench/pages.test.tsx`

- [ ] **Step 1: Write failing UI tests for new actions**

Assert:

- detail page shows `加签 / 减签 / 撤销 / 催办 / 标记已阅` when allowed
- action dialogs submit the expected payloads
- revoked instances become read-only

- [ ] **Step 2: Write failing UI tests for filter expansion**

Assert:

- process-center lists support the new filters
- CC list uses real records and read-state badges
- approval-sheet detail shows richer timeline rows

- [ ] **Step 3: Run focused tests to verify red**

Run:

```bash
pnpm --dir frontend test --run src/features/workbench/pages.test.tsx
```

Expected: FAIL

- [ ] **Step 4: Implement minimal frontend support**

Wire:

- API types and calls
- list filters
- action dialogs
- CC read action
- detail timeline rendering
- helper labels/status mapping

- [ ] **Step 5: Verify green**

Run:

```bash
pnpm --dir frontend test --run src/features/workbench/pages.test.tsx
pnpm --dir frontend typecheck
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/api/workbench.ts frontend/src/features/workbench
git commit -m "feat: add advanced runtime workbench ui"
```

## Chunk 5: Integration Verification

### Task 5: Run full verification and fix regressions

**Files:**
- Modify as needed: affected runtime/workbench files only

- [ ] **Step 1: Run backend tests**

Run:

```bash
mvn -f backend/pom.xml test
```

Expected: PASS

- [ ] **Step 2: Run frontend tests**

Run:

```bash
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
pnpm --dir frontend lint
pnpm --dir frontend build
```

Expected: PASS, with only pre-existing warnings if any

- [ ] **Step 3: Run contract validation**

Run:

```bash
./scripts/validate-contracts.sh
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add backend frontend docs
git commit -m "feat: close advanced runtime actions batch"
```
