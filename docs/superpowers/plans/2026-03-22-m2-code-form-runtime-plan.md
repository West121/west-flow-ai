# M2 Code Form Runtime Closure Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first closed-loop “code component form” module so a published process can select registered forms, render a real process form on start, render a real node form on task handling, and persist submitted runtime form data without introducing any visual form designer.

**Architecture:** Backend owns form metadata registry and runtime form payload persistence; frontend owns the code component registry and form rendering. Process definitions only store `formKey/formVersion` and node-level `nodeFormKey/nodeFormVersion/fieldBindings`, while concrete React form components are resolved from a frontend registry by `componentKey`. Runtime pages stop using raw JSON textareas and instead render typed code components driven by registry metadata.

**Tech Stack:** React, shadcn/ui, react-hook-form, zod, tanstack-query, axios, TanStack Router, Zustand; Spring Boot, MyBatis-Plus style mappers, PostgreSQL, Flyway single `V1__init.sql`, Redis, Sa-Token.

---

## Current Baseline

- 已完成系统管理/组织管理/菜单/角色/数据权限真实 CRUD。
- 已完成流程定义 DSL 持久化、发布、BPMN 转换与设计器画布基础能力。
- 已完成运行态最小闭环：发起、待办、详情、完成、认领、转办、退回上一步。
- 当前最大断点：
  - 发起页仍以原始 JSON 文本作为表单输入。
  - 任务处理页没有真正渲染节点表单代码组件。
  - 设计器虽然已有 `formFields`、`nodeFormKey`、`fieldBindings` 元数据，但没有接到“可选的代码组件表单注册中心”。

## Module Boundary

**This module closes one business slice only:** `代码组件表单运行闭环`

**In scope:**

- 流程表单管理页
- 节点表单管理页
- 代码组件表单注册中心
- 设计器选择流程表单/节点表单
- 发起页渲染流程表单代码组件
- 任务处理页渲染节点表单代码组件
- 运行态保存流程表单数据、节点表单数据
- 一个 OA 请假发起表单 + 一个审批节点表单的样例闭环

**Out of scope:**

- 可视化表单设计器
- 拖拽式字段设计
- 表单版本可视化 diff
- PLM / OA 多案例同时落地
- Flowable 真正接管节点表单状态
- AI 自动填报执行链路

## Parallel Ownership

**Agent A: Backend Form Registry and Runtime Form APIs**

- Owns backend form metadata tables, CRUD APIs, launch/task metadata APIs, runtime form payload persistence.

**Agent B: Frontend Form Registry CRUD and Designer Integration**

- Owns frontend process-form/node-form management pages and workflow designer form selection UX.

**Agent C: Runtime Form Renderer and OA Sample Forms**

- Owns frontend code-component form registry, process start renderer, task node-form renderer, sample OA forms.

**Agent D: Integration and Regression**

- Owns contract checks, frontend integration tests, backend controller/service tests, full verification and template residue triage.

---

## Chunk 1: Freeze Form Registry and Runtime Contract

### Task 1: Freeze code-form registry contract

**Files:**
- Create: `docs/contracts/form-registry.md`
- Modify: `docs/contracts/process-dsl.md`
- Modify: `docs/contracts/task-actions.md`
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`

- [ ] **Step 1: Document the registry model**

Document two resource types:

- `PROCESS_FORM`
- `NODE_FORM`

Required metadata:

- `formKey`
- `formVersion`
- `formName`
- `formType`
- `componentKey`
- `status`
- `fieldDefinitions[]`
- `remark`

- [ ] **Step 2: Freeze runtime payload boundaries**

Document:

- start request uses `formData`
- task completion request adds `taskFormData`
- task detail response returns current node form metadata and existing `taskFormData`

- [ ] **Step 3: Freeze explicit non-goal**

Write the constraint plainly:

- “This phase does not include a visual form designer. Forms are maintained as frontend code components.”

- [ ] **Step 4: Review and commit**

Run: `./scripts/validate-contracts.sh`
Expected: PASS

```bash
git add docs/contracts docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md
git commit -m "docs: freeze m2 code form runtime contracts"
```

## Chunk 2: Backend Form Registry and Runtime Payload Persistence

### Task 2: Add failing backend tests for form registry and runtime form payloads

**Files:**
- Create: `backend/src/test/java/com/westflow/form/api/FormDefinitionControllerTest.java`
- Modify: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`
- Create: `backend/src/test/java/com/westflow/processruntime/service/ProcessDemoFormRuntimeTest.java`

- [ ] **Step 1: Write failing process-form CRUD tests**

Assert:

- process form page/detail/create/update work
- node form page/detail/create/update work
- list filters distinguish `PROCESS_FORM` and `NODE_FORM`

- [ ] **Step 2: Write failing launch metadata test**

Assert:

- backend can return published process launch options with selected process form metadata

- [ ] **Step 3: Write failing runtime task-form test**

Assert:

- task detail returns node form metadata from current node config
- `complete` can persist `taskFormData`
- persisted `taskFormData` can be read back in task detail

- [ ] **Step 4: Verify red**

Run:

```bash
mvn -f backend/pom.xml -Dtest=FormDefinitionControllerTest,ProcessRuntimeControllerTest,ProcessDemoFormRuntimeTest test
```

Expected: FAIL

### Task 3: Implement backend form registry and runtime form payload support

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `backend/src/main/java/com/westflow/form/model/FormDefinitionRecord.java`
- Create: `backend/src/main/java/com/westflow/form/mapper/FormDefinitionMapper.java`
- Create: `backend/src/main/java/com/westflow/form/service/FormDefinitionService.java`
- Create: `backend/src/main/java/com/westflow/form/api/FormDefinitionController.java`
- Create: `backend/src/main/java/com/westflow/form/api/*Request.java`
- Create: `backend/src/main/java/com/westflow/form/api/*Response.java`
- Modify: `backend/src/main/java/com/westflow/processdef/model/ProcessDslPayload.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/CompleteTaskRequest.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskDetailResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/ProcessDemoService.java`
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDefinitionService.java`

- [ ] **Step 1: Extend single Flyway init script**

Add:

- `wf_form_definition`
- seed rows for `oa_leave_start_form` and `oa_leave_approve_form`

- [ ] **Step 2: Build form metadata CRUD API**

Endpoints:

- `POST /api/v1/process-forms/page`
- `GET /api/v1/process-forms/{formKey}:{formVersion}`
- `POST /api/v1/process-forms`
- `PUT /api/v1/process-forms/{formKey}:{formVersion}`
- `POST /api/v1/node-forms/page`
- `GET /api/v1/node-forms/{formKey}:{formVersion}`
- `POST /api/v1/node-forms`
- `PUT /api/v1/node-forms/{formKey}:{formVersion}`

- [ ] **Step 3: Add process launch metadata API**

Add one of:

- `GET /api/v1/process-runtime/launch-options`

Response must include:

- `processKey`
- `processName`
- `formKey`
- `formVersion`
- `formFields`

- [ ] **Step 4: Persist runtime node form payload**

Add `taskFormData` to runtime task model and read model.

- [ ] **Step 5: Expose node form metadata on task detail**

Task detail must include:

- `nodeFormKey`
- `nodeFormVersion`
- `fieldBindings`
- `taskFormData`

- [ ] **Step 6: Verify green**

Run:

```bash
mvn -f backend/pom.xml test
```

Expected: PASS

```bash
git add backend
git commit -m "feat: 支持表单注册中心与运行态表单载荷"
```

## Chunk 3: Frontend Form Registry CRUD and Designer Integration

### Task 4: Add failing frontend tests for form registry APIs and designer selection

**Files:**
- Create: `frontend/src/lib/api/form-definitions.test.ts`
- Modify: `frontend/src/lib/api/workflow.test.ts`
- Create: `frontend/src/features/workflow/designer/form-selection.test.tsx`

- [ ] **Step 1: Write failing API tests**

Assert:

- process form list/detail/create/update client exists
- node form list/detail/create/update client exists

- [ ] **Step 2: Write failing designer selection tests**

Assert:

- designer can load process form options
- approver node can load node form options
- selected form metadata survives save/publish round trip

- [ ] **Step 3: Verify red**

Run:

```bash
pnpm --dir frontend test --run src/lib/api/form-definitions.test.ts src/features/workflow/designer/form-selection.test.tsx
```

Expected: FAIL

### Task 5: Implement form registry pages and designer integration

**Files:**
- Create: `frontend/src/lib/api/form-definitions.ts`
- Create: `frontend/src/features/forms/process-form-pages.tsx`
- Create: `frontend/src/features/forms/node-form-pages.tsx`
- Create: `frontend/src/routes/_authenticated/process/forms/list.tsx`
- Create: `frontend/src/routes/_authenticated/process/forms/create.tsx`
- Create: `frontend/src/routes/_authenticated/process/forms/$formId/index.tsx`
- Create: `frontend/src/routes/_authenticated/process/forms/$formId/edit.tsx`
- Create: `frontend/src/routes/_authenticated/process/node-forms/list.tsx`
- Create: `frontend/src/routes/_authenticated/process/node-forms/create.tsx`
- Create: `frontend/src/routes/_authenticated/process/node-forms/$formId/index.tsx`
- Create: `frontend/src/routes/_authenticated/process/node-forms/$formId/edit.tsx`
- Modify: `frontend/src/components/layout/data/sidebar-data.ts`
- Modify: `frontend/src/features/workflow/pages.tsx`
- Modify: `frontend/src/features/workflow/designer/node-config-panel.tsx`

- [ ] **Step 1: Add two independent Chinese CRUD modules**

Separate sidebar entries:

- `流程表单管理`
- `节点表单管理`

Each module must have:

- 独立列表页
- 独立新建页
- 独立详情页
- 独立编辑页

- [ ] **Step 2: Keep forms as metadata management only**

These pages manage:

- `componentKey`
- `fieldDefinitions`
- remarks and status

They do **not** visually design the form layout.

- [ ] **Step 3: Integrate designer process-form selection**

Designer header must stop relying on hardcoded defaults only and allow selecting:

- process form key/version

- [ ] **Step 4: Integrate node-form selection**

Approver node config must allow selecting registered node forms instead of freehand metadata only.

- [ ] **Step 5: Verify green**

Run:

```bash
pnpm --dir frontend test --run src/lib/api/form-definitions.test.ts src/features/workflow/designer/form-selection.test.tsx
pnpm --dir frontend typecheck
```

Expected: PASS

```bash
git add frontend/src/lib/api frontend/src/features/forms frontend/src/routes/_authenticated/process frontend/src/components/layout/data/sidebar-data.ts frontend/src/features/workflow
git commit -m "feat: 打通表单注册中心与设计器选表单链路"
```

## Chunk 4: Runtime Form Renderer and OA Sample Closure

### Task 6: Add failing frontend runtime form tests

**Files:**
- Create: `frontend/src/features/forms/runtime/form-renderer.test.tsx`
- Modify: `frontend/src/features/workbench/pages.test.tsx`

- [ ] **Step 1: Write failing process-start form renderer test**

Assert:

- start page loads launch options
- selecting a process renders the registered process form component
- submit emits structured `formData`

- [ ] **Step 2: Write failing task-detail node-form renderer test**

Assert:

- task detail renders the registered node form component
- `fieldBindings` hydrate initial values from process form data
- submit sends `taskFormData` through complete API

- [ ] **Step 3: Verify red**

Run:

```bash
pnpm --dir frontend test --run src/features/forms/runtime/form-renderer.test.tsx src/features/workbench/pages.test.tsx
```

Expected: FAIL

### Task 7: Implement code-component form registry and runtime rendering

**Files:**
- Create: `frontend/src/features/forms/runtime/form-component-registry.ts`
- Create: `frontend/src/features/forms/runtime/process-form-renderer.tsx`
- Create: `frontend/src/features/forms/runtime/node-form-renderer.tsx`
- Create: `frontend/src/features/forms/components/process/oa-leave-start-form.tsx`
- Create: `frontend/src/features/forms/components/node/oa-leave-approve-form.tsx`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `frontend/src/lib/api/workbench.test.ts`

- [ ] **Step 1: Create component registry**

Map `componentKey -> React code component`.

Example keys:

- `oa.leave.start.v1`
- `oa.leave.approve.v1`

- [ ] **Step 2: Render process forms on start page**

Replace raw JSON textarea with:

- process selector
- code form renderer
- generated `formData`

- [ ] **Step 3: Render node forms on task detail**

If task detail has `nodeFormKey/nodeFormVersion`, render the registered node form and preload values via `fieldBindings`.

- [ ] **Step 4: Submit runtime node form payload**

`completeWorkbenchTask` must send:

- action
- comment
- `taskFormData`

- [ ] **Step 5: Ship one OA sample closure**

At minimum:

- 请假发起表单组件
- 审批节点处理表单组件

- [ ] **Step 6: Verify green**

Run:

```bash
pnpm --dir frontend test --run
pnpm --dir frontend build
```

Expected: PASS

```bash
git add frontend
git commit -m "feat: 打通代码组件表单运行闭环"
```

## Chunk 5: Integration, Multi-Agent Merge, and Residue Review

### Task 8: Integrate branches, verify contracts, and clean obvious template blockers

**Files:**
- Modify as needed after merge conflict review:
  - `frontend/src/routeTree.gen.ts`
  - `frontend/src/components/layout/data/sidebar-data.ts`
  - `frontend/package.json`
  - `frontend/src/routes/_authenticated/tasks/index.tsx`
  - `frontend/src/routes/_authenticated/chats/index.tsx`
  - `frontend/src/routes/_authenticated/apps/index.tsx`

- [ ] **Step 1: Merge agent outputs**

Integration owner checks:

- route collisions
- sidebar collisions
- API type collisions
- registry key consistency

- [ ] **Step 2: Remove only blockers from template residue**

If the old `tasks/chats/apps` routes or `clerk` residue confuse the new module or menus, remove them in this chunk.

- [ ] **Step 3: Run full regression**

Run:

```bash
./scripts/validate-contracts.sh
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
pnpm --dir frontend lint
pnpm --dir frontend build
mvn -f backend/pom.xml test
```

Expected:

- tests/build pass
- lint may retain only known historical route warnings

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "chore: 收口代码组件表单模块联调"
```

## Recommended Execution Order

1. Agent A and Agent B start in parallel after Chunk 1 freezes.
2. Agent C starts once form metadata API shape is frozen, but does not wait for all CRUD pages to finish.
3. Agent D starts once the first green backend API and first green frontend renderer tests exist.
4. Integration owner merges all outputs only after each agent reports exact changed files and verification results.

## Success Criteria

- 流程定义可选择已注册的流程表单和节点表单。
- 流程发起页不再要求人工输入 JSON 文本。
- 任务处理页可渲染节点表单代码组件并提交 `taskFormData`。
- 至少一个 OA 场景从“设计 -> 发布 -> 发起 -> 认领/审批 -> 完成”走通真实代码组件表单闭环。
- 本期仍然不引入任何可视化表单设计器。
