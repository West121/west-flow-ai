# Approval Sheet Detail Closure Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Archive note (2026-03-23):** 本计划记录审批单详情页从 demo 运行态向真实审批单视图过渡时的历史方案；文中 “in-memory demo runtime” 只表示当时前提，不代表当前平台现状。

**Goal:** Turn the current task detail page into the real approval-sheet detail page, combining business form content, current approval actions, process tracking, and animated flow review in one runtime screen.

**Architecture:** Keep the existing runtime entry route at `/workbench/todos/$taskId`, but enrich the task-detail contract so the page can render one unified approval-sheet view. Backend extends the in-memory demo runtime with instance events, task timing metrics, and OA business snapshots; frontend renders those fields through a dedicated approval-sheet layout and a read-only React Flow review canvas with playback controls.

**Tech Stack:** React, Vite, TanStack Query, TanStack Router, React Hook Form, Zod, shadcn/ui, React Flow; Spring Boot, Flyway single `V1__init.sql`, Sa-Token, MyBatis-style mappers.

---

## File Structure Lock

```text
frontend/src/
├─ features/workbench/
│  ├─ pages.tsx
│  ├─ approval-sheet-graph.tsx
│  └─ approval-sheet-helpers.ts
├─ features/oa/
│  ├─ pages.tsx
│  └─ detail-sections.tsx
└─ lib/api/workbench.ts

backend/src/main/java/com/westflow/
├─ processruntime/api/*
├─ processruntime/service/legacy-runtime-service.java（历史 demo 运行态实现，占位说明）
└─ approval/service/ApprovalSheetQueryService.java
```

## Task 1: Freeze the approval-sheet detail contract

**Files:**
- Create: `docs/superpowers/plans/2026-03-22-approval-sheet-detail-closure-plan.md`
- Modify: `docs/contracts/task-actions.md`
- Modify: `docs/superpowers/specs/2026-03-22-workflow-management-replan.md`

- [x] **Step 1: Document the runtime detail payload**

Freeze the required fields:

- business snapshot
- process DSL / graph payload
- task timing fields
- instance event list
- node execution trace list

- [x] **Step 2: Verify contracts**

Run:

```bash
./scripts/validate-contracts.sh
```

Expected: PASS

## Task 2: Add failing backend tests for approval-sheet detail data

**Files:**
- Modify: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`
- Create or modify: `backend/src/test/java/com/westflow/processruntime/service/ProcessDemoFormRuntimeTest.java`

- [x] **Step 1: Write failing controller assertions**

Assert task detail now returns:

- `businessType`
- `businessData`
- `flowNodes`
- `flowEdges`
- `instanceEvents`
- `taskTrace`
- `receiveTime/readTime/handleStartTime/handleEndTime/handleDurationSeconds`

- [x] **Step 2: Run focused backend tests to verify red**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest,ProcessDemoFormRuntimeTest test
```

Expected: FAIL

## Task 3: Implement backend approval-sheet detail contract

**Files:**
- Create: `backend/src/main/java/com/westflow/approval/service/ApprovalSheetQueryService.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/ProcessInstanceEventResponse.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskTraceItemResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskDetailResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/legacy-runtime-service.java`（历史 demo 运行态实现，占位说明）
- Modify: `backend/src/main/java/com/westflow/oa/mapper/OALeaveBillMapper.java`
- Modify: `backend/src/main/java/com/westflow/oa/mapper/OAExpenseBillMapper.java`
- Modify: `backend/src/main/java/com/westflow/oa/mapper/OACommonRequestBillMapper.java`

- [x] **Step 1: Extend runtime state**

Add:

- per-task receive/read/handle timing
- per-instance event log
- per-node task trace projection

- [x] **Step 2: Add business snapshot query service**

Resolve `OA_LEAVE / OA_EXPENSE / OA_COMMON` into normalized business detail maps.

- [x] **Step 3: Return approval-sheet detail payload**

Enrich task detail response with:

- business snapshot
- process graph payload
- event playback list
- node execution trace

- [x] **Step 4: Run focused backend tests to verify green**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest,ProcessDemoFormRuntimeTest test
```

Expected: PASS

## Task 4: Add failing frontend tests for unified approval-sheet detail page

**Files:**
- Modify: `frontend/src/features/workbench/pages.test.tsx`

- [x] **Step 1: Write failing UI assertions**

Assert the task detail page shows:

- OA business正文
- 流程图回顾 controls
- 节点时序/办理人/接收时间/阅读时间/办理时间/办理时长

- [x] **Step 2: Run focused frontend test to verify red**

Run:

```bash
pnpm --dir frontend test --run src/features/workbench/pages.test.tsx
```

Expected: FAIL

## Task 5: Implement frontend approval-sheet detail page

**Files:**
- Create: `frontend/src/features/workbench/approval-sheet-graph.tsx`
- Create: `frontend/src/features/workbench/approval-sheet-helpers.ts`
- Create: `frontend/src/features/oa/detail-sections.tsx`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/lib/api/workbench.ts`

- [x] **Step 1: Add typed approval-sheet detail models**

Mirror the new backend detail payload in `workbench.ts`.

- [x] **Step 2: Add OA business detail sections**

Render leave / expense / common request正文 as read-only code sections.

- [x] **Step 3: Add React Flow review canvas**

Support:

- current-node highlighting
- played event highlighting
- play / pause / reset
- event-step browsing

- [x] **Step 4: Rebuild the task detail page layout**

Unify:

- business summary
- business正文
- current task actions
- process trace timeline
- animated graph review

- [x] **Step 5: Run focused frontend tests to verify green**

Run:

```bash
pnpm --dir frontend test --run src/features/workbench/pages.test.tsx
pnpm --dir frontend typecheck
```

Expected: PASS

## Task 6: Full verification and commit

**Files:**
- Modify: all files above

- [x] **Step 1: Run full verification**

Run:

```bash
./scripts/validate-contracts.sh
mvn -f backend/pom.xml test
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
pnpm --dir frontend lint
pnpm --dir frontend build
```

Expected: PASS, with only existing known lint warnings if unchanged

- [ ] **Step 2: Commit**

```bash
git add docs backend frontend
git commit -m "feat: 打通审批单详情与流程回顾闭环"
```
