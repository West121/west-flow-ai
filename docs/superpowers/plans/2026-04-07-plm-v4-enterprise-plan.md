# PLM v4 Enterprise Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade PLM from v3 enterprise change management to v4 with deep object model, revision diff, implementation task orchestration, and analytics dashboard.

**Architecture:** Extend the existing PLM aggregate around `PlmLaunchService` with dedicated object, diff, task, and dashboard services. Keep current route and API prefixes stable while expanding data structures, persistence, and UI workspaces in place.

**Tech Stack:** Spring Boot, MyBatis, PostgreSQL/Flyway, React, TanStack Query, React Hook Form, Vitest, JUnit.

---

## File Map

### Backend

- Create: `backend/src/main/resources/db/migration/V16__plm_v4_enterprise_depth.sql`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmObjectMasterMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmObjectRevisionMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmBillObjectLinkMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmRevisionDiffMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmImplementationTaskMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmObjectMasterRecord.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmObjectRevisionRecord.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmBillObjectLinkRecord.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmRevisionDiffRecord.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmImplementationTaskRecord.java`
- Create: `backend/src/main/java/com/westflow/plm/service/PlmObjectService.java`
- Create: `backend/src/main/java/com/westflow/plm/service/PlmRevisionDiffService.java`
- Create: `backend/src/main/java/com/westflow/plm/service/PlmImplementationTaskService.java`
- Create: `backend/src/main/java/com/westflow/plm/service/PlmDashboardService.java`
- Modify: `backend/src/main/java/com/westflow/plm/service/PlmLaunchService.java`
- Modify: `backend/src/main/java/com/westflow/plm/api/PLMController.java`
- Modify: `backend/src/main/java/com/westflow/plm/api/*.java`
- Modify: `backend/src/test/java/com/westflow/plm/api/PLMControllerTest.java`

### Frontend

- Modify: `frontend/src/lib/api/plm.ts`
- Modify: `frontend/src/features/plm/pages.tsx`
- Create: `frontend/src/features/plm/components/plm-dashboard-panels.tsx`
- Create: `frontend/src/features/plm/components/plm-object-link-table.tsx`
- Create: `frontend/src/features/plm/components/plm-revision-diff-panel.tsx`
- Create: `frontend/src/features/plm/components/plm-implementation-task-board.tsx`
- Modify: `frontend/src/features/plm/pages.test.tsx`

### AI

- Modify: `backend/src/main/java/com/westflow/ai/config/AiCopilotConfiguration.java`
- Modify: `backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- Modify: `backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java`

## Chunk 1: Backend Enterprise Depth

### Task 1: Add v4 schema and persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__plm_v4_enterprise_depth.sql`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmObjectMasterMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmObjectRevisionMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmBillObjectLinkMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmRevisionDiffMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/mapper/PlmImplementationTaskMapper.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmObjectMasterRecord.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmObjectRevisionRecord.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmBillObjectLinkRecord.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmRevisionDiffRecord.java`
- Create: `backend/src/main/java/com/westflow/plm/model/PlmImplementationTaskRecord.java`

- [ ] Add tables for object master, object revision, bill-object link, revision diff, and implementation task.
- [ ] Add indexes on `(business_type, bill_id)` and object code / task status access paths.
- [ ] Implement MyBatis mappers and records for CRUD + bill detail aggregation support.
- [ ] Extend controller tests with minimal schema bootstrap for the new tables.
- [ ] Run: `mvn -q -f backend/pom.xml -Dtest=PLMControllerTest test`

### Task 2: Add object/diff/task services and lifecycle rules

**Files:**
- Create: `backend/src/main/java/com/westflow/plm/service/PlmObjectService.java`
- Create: `backend/src/main/java/com/westflow/plm/service/PlmRevisionDiffService.java`
- Create: `backend/src/main/java/com/westflow/plm/service/PlmImplementationTaskService.java`
- Modify: `backend/src/main/java/com/westflow/plm/service/PlmLaunchService.java`

- [ ] Add services that translate v3 affected items into v4 object/revision/link records.
- [ ] Add revision diff upsert logic for create/update draft flows.
- [ ] Add task orchestration rules: at least one task before validating, all required tasks complete before close.
- [ ] Extend detail aggregation so all three business types return object links, revision diffs, and tasks.
- [ ] Run: `mvn -q -f backend/pom.xml -Dtest=PLMControllerTest test`

## Chunk 2: API and Dashboard

### Task 3: Extend controller and DTOs

**Files:**
- Modify: `backend/src/main/java/com/westflow/plm/api/PLMController.java`
- Modify: `backend/src/main/java/com/westflow/plm/api/CreatePLMEcrBillRequest.java`
- Modify: `backend/src/main/java/com/westflow/plm/api/CreatePLMEcoBillRequest.java`
- Modify: `backend/src/main/java/com/westflow/plm/api/CreatePLMMaterialChangeBillRequest.java`
- Modify: `backend/src/main/java/com/westflow/plm/api/*DetailResponse.java`
- Create or modify DTOs for revision diffs, object links, implementation tasks, dashboard analytics.

- [ ] Expand create/update payloads with object-role, revision, diff, and task draft sections.
- [ ] Add task lifecycle endpoints and dashboard analytics endpoint.
- [ ] Keep existing endpoints backward compatible where possible.
- [ ] Run: `mvn -q -f backend/pom.xml -Dtest=PLMControllerTest test`

### Task 4: Add analytics service

**Files:**
- Create: `backend/src/main/java/com/westflow/plm/service/PlmDashboardService.java`
- Modify: `backend/src/main/java/com/westflow/plm/api/PLMController.java`

- [ ] Aggregate summary, type distribution, stage distribution, trend series, task alerts, and owner ranking from SQL.
- [ ] Expose dashboard response via controller.
- [ ] Add assertions in controller test for analytics response shape.
- [ ] Run: `mvn -q -f backend/pom.xml -Dtest=PLMControllerTest test`

## Chunk 3: Frontend Workspace

### Task 5: Extend PLM API client and types

**Files:**
- Modify: `frontend/src/lib/api/plm.ts`

- [ ] Add typed support for deep object links, revision diffs, implementation tasks, and dashboard analytics.
- [ ] Keep existing v3 calls stable.
- [ ] Run: `pnpm -C frontend typecheck`

### Task 6: Add dashboard panels and detail workspace sections

**Files:**
- Create: `frontend/src/features/plm/components/plm-dashboard-panels.tsx`
- Create: `frontend/src/features/plm/components/plm-object-link-table.tsx`
- Create: `frontend/src/features/plm/components/plm-revision-diff-panel.tsx`
- Create: `frontend/src/features/plm/components/plm-implementation-task-board.tsx`
- Modify: `frontend/src/features/plm/pages.tsx`

- [ ] Render enterprise analytics panels on the PLM workspace landing view.
- [ ] Add object link table, diff panel, and task board to all three detail pages.
- [ ] Add lifecycle action controls for task orchestration.
- [ ] Keep the current page entrypoints and routes unchanged.
- [ ] Run: `pnpm -C frontend exec vitest run src/features/plm/pages.test.tsx --reporter=verbose`
- [ ] Run: `pnpm -C frontend typecheck`

## Chunk 4: AI and Reporting

### Task 7: Extend AI PLM summarization

**Files:**
- Modify: `backend/src/main/java/com/westflow/ai/config/AiCopilotConfiguration.java`
- Modify: `backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- Modify: `backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java`

- [ ] Add summaries for object types, revision diffs, task progress, blocked tasks, and close readiness.
- [ ] Ensure `plm.bill.query` can answer “影响哪些对象”、“为什么不能关闭”、“哪些任务阻塞”.
- [ ] Run: `mvn -q -f backend/pom.xml -Dtest=AiCopilotServiceTest,AiPlanAgentServiceTest test`

## Chunk 5: Final Verification

### Task 8: Full integration verification

**Files:**
- Modify: none expected; fix regressions wherever found

- [ ] Run: `pnpm -C frontend exec vitest run src/features/plm/pages.test.tsx --reporter=verbose`
- [ ] Run: `pnpm -C frontend typecheck`
- [ ] Run: `mvn -q -f backend/pom.xml -Dtest=PLMControllerTest,BusinessProcessBindingServiceTest,AiCopilotServiceTest,AiPlanAgentServiceTest test`
- [ ] Run: `mvn -q -f backend/pom.xml -DskipTests compile`
- [ ] Review resulting PLM files for unrelated drift before commit.

Plan complete and saved to `docs/superpowers/plans/2026-04-07-plm-v4-enterprise-plan.md`. Ready to execute.
