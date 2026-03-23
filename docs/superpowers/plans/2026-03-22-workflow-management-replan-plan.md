# Workflow Management Replan Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Archive note (2026-03-23):** 本重排计划形成于真实 `Flowable` 切换之前；若下文仍出现“旧内存运行态服务”等历史文件占位描述，只表示历史执行上下文，不代表当前正式实现。

**Goal:** Replan and implement workflow management as one coherent product slice: dual entry (`OA` + `流程管理`), one shared process center, business-direct launch, approval-sheet detail pages, property-panel form selection, and business-process binding without introducing standalone form management.

**Architecture:** `OA` stays the business-facing entry layer, while `流程管理` stays the platform-facing entry layer. Runtime pages converge into one process center and one approval-sheet detail page; design-time pages stay in workflow definition/designer/version/release areas. Forms remain frontend code components referenced by `processFormKey/processFormVersion` at process level and `nodeFormKey/nodeFormVersion` at node level, with node override taking precedence.

**Tech Stack:** React, Vite, TanStack Router, TanStack Query, Zustand, React Hook Form, Zod, shadcn/ui, React Flow; Spring Boot, PostgreSQL, Flyway single `V1__init.sql`, MyBatis-style mappers, Sa-Token, Flowable, LiteFlow, Aviator.

---

## File Structure Lock

Workflow-related implementation after this replan should converge around these areas:

```text
frontend/src/
├─ components/layout/data/sidebar-data.ts
├─ features/workflow/
│  ├─ pages.tsx
│  └─ designer/*
├─ features/workbench/
│  └─ pages.tsx
├─ features/forms/runtime/*
├─ features/forms/components/process/*
├─ features/forms/components/node/*
├─ features/oa/*
├─ routes/_authenticated/workflow/*
└─ routes/_authenticated/oa/*

backend/src/main/java/com/westflow/
├─ processdef/*
├─ processruntime/*
├─ processaction/*            # if later extracted from runtime
├─ processbinding/*
├─ approval/*
└─ oa/*
```

## Chunk 1: Correct Docs and Remove the Wrong Form-Management Direction

### Task 1: Freeze the corrected workflow-management spec

**Files:**
- Create: `docs/superpowers/specs/2026-03-22-workflow-management-replan.md`
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`
- Modify: `docs/contracts/process-dsl.md`
- Modify: `docs/contracts/task-actions.md`
- Modify: `docs/contracts/form-registry.md`
- Modify: `docs/superpowers/plans/2026-03-22-m2-code-form-runtime-plan.md`
- Modify: `docs/superpowers/plans/2026-03-21-m0-foundation-plan.md`

- [ ] **Step 1: Write the corrected design doc**

Document:

- dual entry (`OA` + `流程管理`)
- one shared process center
- approval-sheet detail page as the only runtime detail container
- process default form + node override form
- business-process binding model
- instance event playback requirements

- [ ] **Step 2: Update shared contracts**

Freeze:

- `processFormKey/processFormVersion`
- `nodeFormKey/nodeFormVersion`
- `节点表单 > 流程默认表单`
- task detail returns effective form reference and timeline fields
- standalone form CRUD is deprecated

- [ ] **Step 3: Mark the old M2 plan as superseded**

The old “standalone form registry” plan must explicitly say it is no longer executable.

- [ ] **Step 4: Verify contract docs**

Run:

```bash
./scripts/validate-contracts.sh
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add docs/contracts docs/superpowers/specs docs/superpowers/plans
git commit -m "docs: replan workflow management architecture"
```

## Chunk 2: Clean Wrong-Scope Code and Keep Only Reusable Runtime Form Pieces

### Task 2: Remove standalone form-management code and preserve reusable form renderers

**Files:**
- Delete: `backend/src/main/java/com/westflow/form/*`
- Delete: `backend/src/test/java/com/westflow/form/*`
- Delete or modify: `frontend/src/lib/api/form-definitions.ts`
- Delete or modify: `frontend/src/lib/api/form-definitions.test.ts`
- Modify: `frontend/src/features/forms/runtime/form-component-registry.ts`
- Modify: `frontend/src/features/forms/runtime/process-form-renderer.tsx`
- Modify: `frontend/src/features/forms/runtime/node-form-renderer.tsx`
- Modify: `frontend/src/features/forms/runtime/types.ts`
- Modify: `frontend/src/features/workflow/designer/form-selection.tsx`
- Modify: `frontend/src/features/workflow/designer/form-selection.test.tsx`

- [ ] **Step 1: Write failing cleanup tests**

Assert:

- no API client remains for standalone process-form/node-form CRUD
- designer form selection reads from a frontend static registry

- [ ] **Step 2: Run focused tests to verify red**

Run:

```bash
pnpm --dir frontend test -- form-selection
```

Expected: FAIL until the static-registry direction is wired in

- [ ] **Step 3: Remove backend standalone form-management artifacts**

Delete the `com.westflow.form` package and related tests. Do not leave dead endpoints in the backend.

- [ ] **Step 4: Keep only reusable runtime form code**

Retain:

- code-component registry
- process form renderer
- node form renderer
- sample OA forms

Refactor them so they no longer depend on backend form CRUD APIs.

- [ ] **Step 5: Verify**

Run:

```bash
pnpm --dir frontend test -- form-renderer
mvn -f backend/pom.xml test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend backend
git commit -m "refactor: remove standalone form management direction"
```

## Chunk 3: Workflow Definition, Designer, and Binding Core

### Task 3: Add process default form and node override selection in the designer

**Files:**
- Modify: `frontend/src/features/workflow/pages.tsx`
- Modify: `frontend/src/features/workflow/designer/dsl.ts`
- Modify: `frontend/src/features/workflow/designer/config.ts`
- Modify: `frontend/src/features/workflow/designer/types.ts`
- Modify: `frontend/src/features/workflow/designer/node-config-panel.tsx`
- Modify: `frontend/src/features/workflow/designer/form-selection.tsx`
- Modify: `frontend/src/lib/api/workflow.ts`
- Modify: `backend/src/main/java/com/westflow/processdef/model/ProcessDslPayload.java`
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDefinitionService.java`
- Modify: `backend/src/main/java/com/westflow/processdef/mapper/ProcessDefinitionMapper.java`
- Test: `frontend/src/features/workflow/designer/dsl.test.ts`
- Test: `frontend/src/features/workflow/designer/form-selection.test.tsx`
- Test: `backend/src/test/java/com/westflow/processdef/service/ProcessDslValidatorTest.java`

- [ ] **Step 1: Write failing tests for process-level form selection**

Assert:

- DSL root stores `processFormKey/processFormVersion`
- publish/save round-trip keeps those values

- [ ] **Step 2: Write failing tests for node override precedence metadata**

Assert:

- approver node can optionally store `nodeFormKey/nodeFormVersion`
- node override metadata survives round-trip

- [ ] **Step 3: Run the targeted tests to verify red**

Run:

```bash
pnpm --dir frontend test -- dsl form-selection
mvn -f backend/pom.xml -Dtest=ProcessDslValidatorTest test
```

Expected: FAIL

- [ ] **Step 4: Implement the minimal DSL contract changes**

Wire:

- process-level default form reference
- node-level optional override form reference
- field bindings for node forms

- [ ] **Step 5: Verify green**

Run:

```bash
pnpm --dir frontend test -- dsl form-selection
pnpm --dir frontend typecheck
mvn -f backend/pom.xml test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/workflow frontend/src/lib/api/workflow.ts backend/src/main/java/com/westflow/processdef backend/src/test/java/com/westflow/processdef
git commit -m "feat: support process default forms and node overrides"
```

### Task 4: Add business-process binding and OA launch routing

**Files:**
- Create: `backend/src/main/java/com/westflow/processbinding/*`
- Create: `backend/src/test/java/com/westflow/processbinding/*`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `frontend/src/features/oa/*`
- Create: `frontend/src/routes/_authenticated/oa/*`
- Modify: `frontend/src/components/layout/data/sidebar-data.ts`
- Test: `frontend/src/lib/api/workbench.test.ts`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write failing backend tests for binding resolution**

Assert:

- `businessType + sceneCode` can resolve the active process definition
- launching via business entry writes the business-process link

- [ ] **Step 2: Write failing frontend tests for OA direct launch routing**

Assert:

- OA entry goes directly to business form pages
- process center launch goes to business selection first, then the same business form page

- [ ] **Step 3: Run focused tests to verify red**

Run:

```bash
pnpm --dir frontend test -- workbench
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: FAIL

- [ ] **Step 4: Implement business binding tables and APIs**

Add:

- `wf_business_process_binding`
- `wf_business_process_link`

And the service/controller flow needed to resolve active bindings.

- [ ] **Step 5: Implement OA launch pages**

Add:

- leave start page
- expense start page
- common request start page

Each page must directly open the business form, not a template chooser.

- [ ] **Step 6: Verify green**

Run:

```bash
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
mvn -f backend/pom.xml test
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/features/oa frontend/src/routes/_authenticated/oa frontend/src/components/layout/data/sidebar-data.ts backend/src/main/java/com/westflow/processbinding backend/src/main/resources/db/migration/V1__init.sql backend/src/test/java/com/westflow/processbinding
git commit -m "feat: add business process binding and oa launch flow"
```

## Chunk 4: Process Center and Approval-Sheet Detail Closure

### Task 5: Build one shared process center

**Files:**
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/routes/_authenticated/workbench/start.tsx`
- Modify: `frontend/src/routes/_authenticated/workbench/todos/list.tsx`
- Create: `frontend/src/routes/_authenticated/workbench/done/list.tsx`
- Create: `frontend/src/routes/_authenticated/workbench/mine/list.tsx`
- Create: `frontend/src/routes/_authenticated/workbench/cc/list.tsx`
- Modify: `frontend/src/components/layout/data/sidebar-data.ts`
- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/legacy-runtime-service.java`（历史 demo 运行态实现，占位说明）
- Test: `frontend/src/features/workbench/pages.test.tsx`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write failing tests for unified center lists**

Assert:

- `待办 / 已办 / 我发起 / 抄送我 / 发起流程` exist as separate routes
- OA and workflow-management entries reuse the same pages with different default filters

- [ ] **Step 2: Run tests to verify red**

Run:

```bash
pnpm --dir frontend test -- pages
```

Expected: FAIL

- [ ] **Step 3: Implement the shared center routes and default-filter behavior**

The runtime lists must be single implementation, not duplicates.

- [ ] **Step 4: Verify**

Run:

```bash
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/workbench frontend/src/routes/_authenticated/workbench frontend/src/components/layout/data/sidebar-data.ts frontend/src/lib/api/workbench.ts backend/src/main/java/com/westflow/processruntime backend/src/test/java/com/westflow/processruntime
git commit -m "feat: build shared process center"
```

### Task 6: Build the approval-sheet detail page with process playback

**Files:**
- Create: `frontend/src/features/workbench/detail/*`
- Create: `frontend/src/routes/_authenticated/workbench/sheets/$businessType/$businessId.tsx`
- Modify: `frontend/src/features/forms/runtime/process-form-renderer.tsx`
- Modify: `frontend/src/features/forms/runtime/node-form-renderer.tsx`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskDetailResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/legacy-runtime-service.java`（历史 demo 运行态实现，占位说明）
- Create: `backend/src/main/java/com/westflow/approval/*`
- Create: `backend/src/test/java/com/westflow/approval/*`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Test: `frontend/src/features/workbench/pages.test.tsx`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write failing tests for detail layout and timeline fields**

Assert:

- detail page renders business form, current task area, opinion list, process graph, playback controls, attachments, operation log
- backend returns `receiveTime`, `readTime`, `handleStartTime`, `handleEndTime`, `handleDuration`

- [ ] **Step 2: Write failing tests for effective form resolution**

Assert:

- node form is used when configured
- process default form is used when node form is absent

- [ ] **Step 3: Run focused tests to verify red**

Run:

```bash
pnpm --dir frontend test -- pages form-renderer
mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test
```

Expected: FAIL

- [ ] **Step 4: Implement instance detail payload and event timeline**

Add backend support for:

- approval opinions
- instance events
- playback-friendly node execution timeline
- effective form reference

- [ ] **Step 5: Implement the approval-sheet detail page**

The page must embed:

- business form area
- current action area
- process graph preview
- playback controls
- node execution metadata
- opinion list
- attachments
- operation log

- [ ] **Step 6: Verify**

Run:

```bash
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
pnpm --dir frontend build
mvn -f backend/pom.xml test
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/features/workbench frontend/src/features/forms/runtime frontend/src/routes/_authenticated/workbench backend/src/main/java/com/westflow/processruntime backend/src/main/java/com/westflow/approval backend/src/main/resources/db/migration/V1__init.sql backend/src/test/java/com/westflow/processruntime backend/src/test/java/com/westflow/approval
git commit -m "feat: add approval sheet detail and process playback"
```

## Chunk 5: Platform Monitoring and Configuration

### Task 7: Add monitoring, action log, and opinion configuration pages

**Files:**
- Create: `frontend/src/features/workflow/monitoring/*`
- Create: `frontend/src/features/workflow/config/*`
- Create: `frontend/src/routes/_authenticated/workflow/instances/*`
- Create: `frontend/src/routes/_authenticated/workflow/logs/*`
- Create: `frontend/src/routes/_authenticated/workflow/opinions/*`
- Create: `frontend/src/routes/_authenticated/workflow/bindings/*`
- Create: `backend/src/main/java/com/westflow/analytics/*`
- Modify: `backend/src/main/java/com/westflow/approval/*`
- Modify: `backend/src/main/java/com/westflow/processbinding/*`
- Test: `frontend/src/lib/api/workflow.test.ts`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write failing tests for monitoring/config APIs and pages**

Assert:

- workflow instance monitoring page exists
- operation log page exists
- opinion configuration page exists
- business-process binding page exists

- [ ] **Step 2: Run tests to verify red**

Run:

```bash
pnpm --dir frontend test -- workflow
mvn -f backend/pom.xml test
```

Expected: FAIL

- [ ] **Step 3: Implement the minimal monitoring/config slice**

Ship:

- full-instance list and filters
- action log list
- opinion template/config page
- business-process binding CRUD page

- [ ] **Step 4: Verify**

Run:

```bash
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
pnpm --dir frontend build
mvn -f backend/pom.xml test
./scripts/validate-contracts.sh
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/workflow frontend/src/routes/_authenticated/workflow backend/src/main/java/com/westflow/analytics backend/src/main/java/com/westflow/approval backend/src/main/java/com/westflow/processbinding backend/src/test/java/com/westflow
git commit -m "feat: add workflow monitoring and configuration"
```

Plan complete and saved to `docs/superpowers/plans/2026-03-22-workflow-management-replan-plan.md`. Ready to execute?
