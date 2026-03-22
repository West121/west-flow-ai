# Reject & Return Runtime Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement one coherent runtime slice covering reject routing strategies, jump, take-back, wake-up, and richer approval-sheet trajectory data.

**Architecture:** Continue extending the single in-memory runtime backbone in `processruntime`. Model each new behavior as explicit task end states plus instance events, so the same approval-sheet detail page, process-center lists, and flow replay can render the new actions without forking the runtime. Reject routing must be treated as a dedicated action with target strategy and re-approval strategy, not as a generic `complete(action=REJECT)` payload.

**Tech Stack:** React, TanStack Query, TanStack Router, React Hook Form, Zod, shadcn/ui, React Flow; Spring Boot, Sa-Token, existing runtime controller tests and workbench page tests.

---

## File Structure Lock

This batch stays inside the current runtime seams:

```text
backend/src/main/java/com/westflow/processruntime/
├─ api/*
└─ service/ProcessDemoService.java

backend/src/test/java/com/westflow/processruntime/
└─ api/ProcessRuntimeControllerTest.java

frontend/src/features/workbench/
├─ approval-sheet-graph.tsx
├─ approval-sheet-helpers.ts
├─ pages.tsx
└─ pages.test.tsx

frontend/src/lib/api/workbench.ts
docs/contracts/task-actions.md
docs/superpowers/specs/2026-03-22-reject-return-runtime-design.md
```

## Chunk 1: Freeze Reject/Return Contracts

### Task 1: Update specs and plan docs

**Files:**
- Modify: `docs/contracts/task-actions.md`
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`
- Create: `docs/superpowers/specs/2026-03-22-reject-return-runtime-design.md`
- Create: `docs/superpowers/plans/2026-03-22-reject-return-runtime-plan.md`

- [ ] **Step 1: Update the frozen scope**

Document this batch as:

- `驳回到上一步 / 发起人 / 任意节点`
- `重新审批策略`
- `跳转`
- `拿回`
- `唤醒`

- [ ] **Step 2: Freeze runtime contract fields**

Define:

- new task end states
- new action names
- reject target strategy
- reapprove strategy
- wake-up source task semantics

- [ ] **Step 3: Validate docs**

Run:

```bash
./scripts/validate-contracts.sh
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add docs/contracts docs/superpowers/specs docs/superpowers/plans
git commit -m "docs: freeze reject return runtime scope"
```

## Chunk 2: Backend Reject Routing and Jump

### Task 2: Add reject and jump runtime APIs

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/api/RejectTaskRequest.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/JumpTaskRequest.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/TaskActionAvailabilityResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskDetailResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskTraceItemResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessInstanceEventResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/ProcessDemoService.java`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write failing controller tests for reject routing**

Assert:

- current assignee can reject to previous user task
- current assignee can reject to initiator
- current assignee can reject to any configured user task node
- reapproval strategy is included in detail trajectory

- [ ] **Step 2: Run the focused test to verify red**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: FAIL with missing endpoint or assertion mismatch

- [ ] **Step 3: Write failing controller tests for jump**

Assert:

- jump closes the current task as `JUMPED`
- jump to approver node creates a new pending task
- jump to end node completes the instance

- [ ] **Step 4: Implement minimal backend behavior**

Wire:

- request records
- controller endpoints
- availability flags
- reject target resolution
- reapprove routing metadata
- jump event payloads

- [ ] **Step 5: Verify green**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime backend/src/test/java/com/westflow/processruntime
git commit -m "feat: add reject routing and jump runtime actions"
```

## Chunk 3: Backend Take-Back and Wake-Up

### Task 3: Add take-back and wake-up semantics

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/api/TakeBackTaskRequest.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/WakeUpInstanceRequest.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/TaskActionAvailabilityResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/ProcessDemoService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskTraceItemResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessInstanceEventResponse.java`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write failing controller tests for take-back**

Assert:

- previous submitter can take back only before current task is read or handled
- target task becomes `TAKEN_BACK`
- previous node gets a new pending task

- [ ] **Step 2: Write failing controller tests for wake-up**

Assert:

- completed, rejected, or revoked instance can be woken up from a historical user task
- instance status returns to `RUNNING`
- detail trace shows the wake-up action and new pending task

- [ ] **Step 3: Run the focused test to verify red**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: FAIL

- [ ] **Step 4: Implement minimal backend behavior**

Add:

- take-back guard rules
- wake-up source task validation
- new terminal task statuses
- timeline/event data for take-back and wake-up

- [ ] **Step 5: Verify green**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime backend/src/test/java/com/westflow/processruntime
git commit -m "feat: add take back and wake up runtime actions"
```

## Chunk 4: Frontend Approval-Sheet Detail and Action Dialogs

### Task 4: Surface reject routing and return actions in one shared UI

**Files:**
- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `frontend/src/features/workbench/approval-sheet-helpers.ts`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-graph.tsx`
- Test: `frontend/src/features/workbench/pages.test.tsx`

- [ ] **Step 1: Write failing UI tests for reject dialog**

Assert:

- detail page shows `驳回` dialog with target strategy choices
- choosing “任意节点” reveals a node selector
- submit calls the dedicated reject API payload

- [ ] **Step 2: Write failing UI tests for jump, take-back, and wake-up**

Assert:

- detail page shows `跳转 / 拿回 / 唤醒` when allowed
- submit calls the dedicated APIs
- wake-up detail page refreshes to the new active task state

- [ ] **Step 3: Run focused tests to verify red**

Run:

```bash
pnpm --dir frontend test --run src/features/workbench/pages.test.tsx
```

Expected: FAIL

- [ ] **Step 4: Implement minimal frontend support**

Wire:

- new API types and clients
- action availability flags
- dialog forms and validations
- trajectory labels and detail badges
- flow replay labels for reject/jump/take-back/wake-up

- [ ] **Step 5: Verify green**

Run:

```bash
pnpm --dir frontend test --run src/features/workbench/pages.test.tsx
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/api/workbench.ts frontend/src/features/workbench
git commit -m "feat: add reject return runtime actions UI"
```

## Chunk 5: Full Verification

### Task 5: Run the full regression set

**Files:**
- Modify: any touched files from previous tasks only if regressions require it

- [ ] **Step 1: Validate contracts**

Run:

```bash
./scripts/validate-contracts.sh
```

Expected: PASS

- [ ] **Step 2: Run backend tests**

Run:

```bash
mvn -f backend/pom.xml test
```

Expected: PASS

- [ ] **Step 3: Run frontend tests**

Run:

```bash
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
pnpm --dir frontend lint
pnpm --dir frontend build
```

Expected: PASS, allowing only existing historical lint warnings if unchanged

- [ ] **Step 4: Commit final integration**

```bash
git add backend frontend docs
git commit -m "feat: 打通驳回与回退策略运行态闭环"
```
