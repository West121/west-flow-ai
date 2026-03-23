# OA Launch Closure Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Archive note (2026-03-23):** 本计划形成于业务发起闭环接入真实 `Flowable` 之前；若后文引用 `ProcessDemoService` 或 demo 运行态文件路径，只表示当时历史上下文。

**Goal:** 打通 `OA` 业务发起闭环，让用户可以从 `OA` 菜单或 `流程中心 > 发起流程` 进入业务表单，保存业务单据、匹配流程绑定、自动发起流程实例，并在流程中心里看到对应待办。

**Architecture:** 后端新增 `processbinding` 与 `oa` 业务域，业务单据与流程实例通过 `wf_business_process_link` 关联。前端新增 `OA` 菜单与独立业务发起页，`流程中心` 发起页改为业务入口选择页，所有实际发起仍走同一套业务表单和后端业务发起接口。

**Tech Stack:** React, TanStack Router, TanStack Query, React Hook Form, Zod, shadcn/ui; Spring Boot, Flyway, MyBatis 注解 Mapper, Sa-Token, H2 test profile.

---

## Chunk 1: Backend Binding and OA Launch APIs

### Task 1: Add business binding schema and OA bill launch endpoints

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `backend/src/main/java/com/westflow/processbinding/model/BusinessProcessBindingRecord.java`
- Create: `backend/src/main/java/com/westflow/processbinding/model/BusinessProcessLinkRecord.java`
- Create: `backend/src/main/java/com/westflow/processbinding/mapper/BusinessProcessBindingMapper.java`
- Create: `backend/src/main/java/com/westflow/processbinding/mapper/BusinessProcessLinkMapper.java`
- Create: `backend/src/main/java/com/westflow/processbinding/service/BusinessProcessBindingService.java`
- Create: `backend/src/main/java/com/westflow/oa/model/OALeaveBillRecord.java`
- Create: `backend/src/main/java/com/westflow/oa/model/OAExpenseBillRecord.java`
- Create: `backend/src/main/java/com/westflow/oa/model/OACommonRequestBillRecord.java`
- Create: `backend/src/main/java/com/westflow/oa/mapper/OALeaveBillMapper.java`
- Create: `backend/src/main/java/com/westflow/oa/mapper/OAExpenseBillMapper.java`
- Create: `backend/src/main/java/com/westflow/oa/mapper/OACommonRequestBillMapper.java`
- Create: `backend/src/main/java/com/westflow/oa/api/OALaunchResponse.java`
- Create: `backend/src/main/java/com/westflow/oa/api/CreateOALeaveBillRequest.java`
- Create: `backend/src/main/java/com/westflow/oa/api/CreateOAExpenseBillRequest.java`
- Create: `backend/src/main/java/com/westflow/oa/api/CreateOACommonRequestBillRequest.java`
- Create: `backend/src/main/java/com/westflow/oa/api/OALeaveBillDetailResponse.java`
- Create: `backend/src/main/java/com/westflow/oa/api/OAExpenseBillDetailResponse.java`
- Create: `backend/src/main/java/com/westflow/oa/api/OACommonRequestBillDetailResponse.java`
- Create: `backend/src/main/java/com/westflow/oa/service/OALaunchService.java`
- Create: `backend/src/main/java/com/westflow/oa/api/OAController.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/StartProcessRequest.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskListItemResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskDetailResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/ProcessDemoService.java`
- Test: `backend/src/test/java/com/westflow/oa/api/OAControllerTest.java`
- Test: `backend/src/test/java/com/westflow/processbinding/service/BusinessProcessBindingServiceTest.java`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] **Step 1: Write failing tests for binding resolution**

Assert:

- `businessType + sceneCode` 能解析到启用的流程绑定
- 未配置绑定时返回明确业务错误

- [ ] **Step 2: Run tests to verify red**

Run:

```bash
mvn -f backend/pom.xml -Dtest=BusinessProcessBindingServiceTest,OAControllerTest,ProcessRuntimeControllerTest test
```

Expected: FAIL because binding schema and OA launch APIs do not exist yet

- [ ] **Step 3: Add Flyway schema and seed rows**

Add:

- `wf_business_process_binding`
- `wf_business_process_link`
- `oa_leave_bill`
- `oa_expense_bill`
- `oa_common_request_bill`

Seed:

- `OA_LEAVE/default -> oa_leave`
- `OA_EXPENSE/default -> oa_expense`
- `OA_COMMON/default -> oa_common`

- [ ] **Step 4: Implement minimal binding service**

Behavior:

- query enabled binding by `businessType + sceneCode`
- prefer higher `priority`
- return `processKey` for runtime launch

- [ ] **Step 5: Implement OA launch service and controller**

Behavior:

- save bill record
- resolve binding
- call runtime start with `processKey + businessKey + businessType + formData`
- save process link
- return bill id, bill no, process instance id, first active task

- [ ] **Step 6: Extend runtime instance metadata**

Persist `businessType` into demo runtime instance so later process-center filtering can use it.

- [ ] **Step 7: Run tests to verify green**

Run:

```bash
mvn -f backend/pom.xml -Dtest=BusinessProcessBindingServiceTest,OAControllerTest,ProcessRuntimeControllerTest test
```

Expected: PASS

## Chunk 2: Frontend OA Launch Pages and Process-Center Entry

### Task 2: Add OA create pages and convert process-center launch into business entry selection

**Files:**
- Create: `frontend/src/features/oa/pages.tsx`
- Create: `frontend/src/features/oa/pages.test.tsx`
- Create: `frontend/src/lib/api/oa.ts`
- Create: `frontend/src/lib/api/oa.test.ts`
- Create: `frontend/src/routes/_authenticated/oa/leave/create.tsx`
- Create: `frontend/src/routes/_authenticated/oa/expense/create.tsx`
- Create: `frontend/src/routes/_authenticated/oa/common/create.tsx`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workbench/pages.test.tsx`
- Modify: `frontend/src/components/layout/data/sidebar-data.ts`
- Modify: `frontend/src/routeTree.gen.ts`

- [ ] **Step 1: Write failing frontend tests**

Assert:

- `流程中心 > 发起流程` 展示业务入口选择，不再直接输 `processKey`
- `OA > 请假申请` 进入独立业务发起页
- 业务发起成功后跳到首个待办或任务处理页

- [ ] **Step 2: Run tests to verify red**

Run:

```bash
pnpm --dir frontend test --run src/features/oa/pages.test.tsx src/features/workbench/pages.test.tsx src/lib/api/oa.test.ts
```

Expected: FAIL because OA pages and APIs do not exist yet

- [ ] **Step 3: Add OA frontend API client**

Endpoints:

- `POST /oa/leaves`
- `POST /oa/expenses`
- `POST /oa/common-requests`
- corresponding detail endpoints

- [ ] **Step 4: Add OA create pages**

Pages:

- 请假申请
- 报销申请
- 通用申请

Requirements:

- 中文文案
- 独立页面
- `react-hook-form + zod`
- 成功后跳任务处理页

- [ ] **Step 5: Convert process-center launch page**

Replace the old direct runtime launch form with business entry cards linking to the OA pages.

- [ ] **Step 6: Update sidebar**

Add:

- `OA > 请假申请`
- `OA > 报销申请`
- `OA > 通用申请`
- `OA > OA 流程查询`
- `流程管理 > 流程中心 > 发起流程`
- `流程管理 > 流程中心 > 待办列表`

- [ ] **Step 7: Run tests to verify green**

Run:

```bash
pnpm --dir frontend test --run src/features/oa/pages.test.tsx src/features/workbench/pages.test.tsx src/lib/api/oa.test.ts
pnpm --dir frontend typecheck
pnpm --dir frontend lint
```

Expected: PASS, except existing historical route fast-refresh warnings

## Chunk 3: Full Verification and Commit

### Task 3: Verify end-to-end closure and commit

**Files:**
- Modify as needed from previous tasks only

- [ ] **Step 1: Run backend full test suite**

```bash
mvn -f backend/pom.xml test
```

Expected: PASS

- [ ] **Step 2: Run frontend full verification**

```bash
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
pnpm --dir frontend lint
pnpm --dir frontend build
```

Expected: PASS, with only pre-existing route fast-refresh warnings if still present

- [ ] **Step 3: Verify contracts**

```bash
./scripts/validate-contracts.sh
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add backend frontend docs/superpowers/plans/2026-03-22-oa-launch-closure-plan.md
git commit -m "feat: 打通oa业务发起闭环"
```
