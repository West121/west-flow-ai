# M1 Runtime Actions and Node Form Plan

> **Scope:** Freeze the next executable batch after the current M0 baseline. This plan covers node form configuration expansion in the process designer and the first collaboration-oriented runtime actions: `claim`, `transfer`, and `return-to-previous`.
>
> **Archive note (2026-03-23):** 本计划中的 `/api/v1/process-runtime/demo/*` 仅表示当时冻结时的历史路径草案，当前正式接口已统一为 `/api/v1/process-runtime/*`。

**Goal:** Extend the current process definition and runtime demo into a more realistic OA collaboration loop without introducing full rejection strategy, revoke semantics, or visual form design.

**Out of Scope:** `reject to initiator`, `reject to any node`, `withdraw`, `delegate`, `agent`, `add sign`, `remove sign`, visual form designer, Flowable engine-level rollback semantics.

---

## Chunk 1: Freeze DSL and Runtime Contracts

### Task 1: Extend the process DSL contract for form references

**Files:**
- Modify: `docs/contracts/process-dsl.md`
- Modify: `docs/contracts/dsl-bpmn-mapping.md`
- Modify: `docs/contracts/task-actions.md`
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`

- [ ] **Step 1: Freeze process-level form metadata**

Document the DSL fields:

- `formKey`
- `formVersion`
- `formFields[]`

- [ ] **Step 2: Freeze node-level form metadata**

Document the node config fields:

- `nodeFormKey`
- `nodeFormVersion`
- `fieldBindings[]`

`fieldBindings[]` sources in this batch:

- `PROCESS_FORM`
- `NODE_FORM`

- [ ] **Step 3: Freeze runtime task states and action enums**

Document:

- task states: `PENDING_CLAIM`, `PENDING`, `COMPLETED`, `TRANSFERRED`, `RETURNED`
- action enums: `CLAIM`, `TRANSFER`, `RETURN`, `APPROVE`, `REJECT`

- [ ] **Step 4: Freeze runtime action endpoints**

Document:

- `POST /api/v1/process-runtime/demo/tasks/{taskId}/claim`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/transfer`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/return`
- `GET /api/v1/process-runtime/demo/tasks/{taskId}/actions`

- [ ] **Step 5: Commit**

```bash
git add docs/contracts docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md
git commit -m "docs: freeze m1 runtime action contracts"
```

## Chunk 2: Designer Node Form Configuration

### Task 2: Add failing frontend tests for node form configuration

**Files:**
- Modify: `frontend/src/features/workflow/designer/dsl.test.ts`
- Create or modify: `frontend/src/features/workflow/designer/config.test.ts`

- [ ] **Step 1: Write a failing serialization test**

Assert that process-level form metadata and node-level `fieldBindings[]` survive:

- workflow snapshot -> DSL
- DSL detail -> workflow snapshot

- [ ] **Step 2: Write a failing condition-node field reference test**

Assert that condition nodes can store field-based expression fragments instead of raw handwritten strings only.

- [ ] **Step 3: Verify red**

Run:

```bash
pnpm --dir frontend test --run src/features/workflow/designer/dsl.test.ts
```

Expected: FAIL

### Task 3: Implement designer form metadata configuration

**Files:**
- Modify: `frontend/src/features/workflow/designer/types.ts`
- Modify: `frontend/src/features/workflow/designer/config.ts`
- Modify: `frontend/src/features/workflow/designer/node-config-panel.tsx`
- Modify: `frontend/src/features/workflow/pages.tsx`
- Modify: `frontend/src/lib/api/workflow.ts` if required by the updated payload shape

- [ ] **Step 1: Extend typed config models**

Add:

- process form metadata
- node form metadata
- field binding source enum

- [ ] **Step 2: Extend node config panel**

Add UI for:

- `nodeFormKey`
- `nodeFormVersion`
- `fieldBindings[]`

- [ ] **Step 3: Add process-level form metadata editing**

Expose process-level form identity in the designer page instead of leaving only static defaults.

- [ ] **Step 4: Keep the current designer stable**

Do not break:

- drag and drop
- undo/redo
- auto layout
- save draft
- publish

- [ ] **Step 5: Verify green**

Run:

```bash
pnpm --dir frontend test --run src/features/workflow/designer/dsl.test.ts
pnpm --dir frontend typecheck
```

Expected: PASS

## Chunk 3: Runtime Claim / Transfer / Return

### Task 4: Write failing backend tests for runtime actions

**Files:**
- Modify: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`
- Create: `backend/src/test/java/com/westflow/processruntime/service/ProcessDemoServiceTest.java`

- [ ] **Step 1: Add a failing claim test**

Assert:

- public task starts as `PENDING_CLAIM`
- candidate user can claim
- non-candidate user cannot claim

- [ ] **Step 2: Add a failing transfer test**

Assert:

- assignee can transfer
- original task becomes `TRANSFERRED`
- new task becomes `PENDING`

- [ ] **Step 3: Add a failing return test**

Assert:

- assignee can return to previous user task
- original task becomes `RETURNED`
- previous node gets a fresh pending task

- [ ] **Step 4: Verify red**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest,ProcessDemoServiceTest test
```

Expected: FAIL

### Task 5: Implement runtime action endpoints and service logic

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/ProcessDemoService.java`
- Create or modify: `backend/src/main/java/com/westflow/processruntime/api/*Request.java`
- Create or modify: `backend/src/main/java/com/westflow/processruntime/api/*Response.java`

- [ ] **Step 1: Add action capability response**

Implement `GET /tasks/{taskId}/actions`.

- [ ] **Step 2: Add claim**

Implement `POST /tasks/{taskId}/claim`.

- [ ] **Step 3: Add transfer**

Implement `POST /tasks/{taskId}/transfer`.

- [ ] **Step 4: Add return**

Implement `POST /tasks/{taskId}/return` with `PREVIOUS_USER_TASK` only.

- [ ] **Step 5: Add action log model**

Use an in-memory runtime structure for this batch if needed, but keep the model explicit and separable from comments.

- [ ] **Step 6: Verify green**

Run:

```bash
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest,ProcessDemoServiceTest test
```

Expected: PASS

## Chunk 4: Workbench UI Upgrade

### Task 6: Write failing frontend workbench tests

**Files:**
- Modify: `frontend/src/lib/api/workbench.test.ts`
- Create: `frontend/src/features/workbench/pages.test.tsx`

- [ ] **Step 1: Add failing API tests**

Assert API clients exist for:

- claim
- transfer
- return
- action capability query

- [ ] **Step 2: Add failing page tests**

Assert:

- public tasks render as “待认领”
- claim action is conditionally visible
- transfer dialog submits target user
- return action is conditionally visible

- [ ] **Step 3: Verify red**

Run:

```bash
pnpm --dir frontend test --run src/lib/api/workbench.test.ts
```

Expected: FAIL

### Task 7: Implement workbench runtime action UX

**Files:**
- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/routes/_authenticated/workbench/todos/$taskId.tsx`

- [ ] **Step 1: Add API clients**

Add:

- `claimWorkbenchTask`
- `transferWorkbenchTask`
- `returnWorkbenchTask`
- `getWorkbenchTaskActions`

- [ ] **Step 2: Upgrade list page**

Show:

- task ownership
- public task badge
- inline claim action when allowed

- [ ] **Step 3: Upgrade detail page**

Show:

- action capability driven buttons
- transfer dialog
- return action

- [ ] **Step 4: Preserve Chinese-only UI**

All new page titles, labels, buttons, and status copy remain Chinese.

- [ ] **Step 5: Verify green**

Run:

```bash
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
pnpm --dir frontend build
```

Expected: PASS

## Chunk 5: Full Verification and Handoff

### Task 8: Final batch verification

**Files:**
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md` only if contract drift is discovered

- [ ] **Step 1: Run frontend verification**

```bash
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test --run
pnpm --dir frontend build
```

- [ ] **Step 2: Run backend verification**

```bash
mvn -f backend/pom.xml test
```

- [ ] **Step 3: Record residual risks**

At minimum confirm whether these remain out of scope:

- reject strategy
- revoke
- delegate / agent
- persistent runtime action log storage

- [ ] **Step 4: Commit**

```bash
git add frontend backend docs
git commit -m "feat: add m1 claim transfer return batch"
```
