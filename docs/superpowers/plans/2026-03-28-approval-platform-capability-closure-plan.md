# 审批平台能力收口 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为审批平台补齐批量审批、督办/会办/阅办/传阅、电子签章、更完整退回语义、SLA 升级链路，并统一设计器、工作台、审批详情。

**Architecture:** 采用“运行态后端 + 设计器管理 + 工作台前端 + 签章材料”四线并行推进。先在运行态建立正式语义和审计，再补设计器配置与工作台入口，最后统一收口审批详情与回归。批量审批、退回语义、SLA 属于运行态基建；协同审批模式属于流程节点能力；电子签章单独成层，避免污染主审批链。

**Tech Stack:** Spring Boot, Flowable, MyBatis, PostgreSQL, React, TanStack Query, existing workflow DSL/designer/workbench stack.

---

## Chunk 1: 运行态基建

### Task 1: 批量审批与批量动作协议

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/controller/ProcessRuntimeController.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/request/BatchTaskActionRequest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/response/BatchTaskActionResponse.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖批量认领/已读/同意/驳回**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现批量动作请求、结果和逐项失败明细**
- [ ] **Step 4: 接入控制器与服务层**
- [ ] **Step 5: 运行测试**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test`
Expected: PASS

### Task 2: 退回语义升级

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/request/ReturnTaskRequest.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/request/RejectTaskRequest.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖发起人/上一步/任意已走节点**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 扩展 reject/return target strategy**
- [ ] **Step 4: 统一实例事件与轨迹语义**
- [ ] **Step 5: 运行测试**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test`
Expected: PASS

### Task 3: SLA 升级链路

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/orchestrator/service/FlowableOrchestratorRuntimeBridge.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessDslValidatorTest.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/orchestrator/service/FlowableOrchestratorRuntimeBridgeTest.java`

- [ ] **Step 1: 写失败测试，覆盖 escalationPolicy 校验与执行**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 增加升级策略配置与运行态执行**
- [ ] **Step 4: 记录升级审计事件**
- [ ] **Step 5: 运行测试**

Run: `mvn -q -f backend/pom.xml -Dtest=ProcessDslValidatorTest,FlowableOrchestratorRuntimeBridgeTest test`
Expected: PASS

## Chunk 2: 协同审批模式

### Task 4: 设计器与 DSL 补齐督办/会办/阅办/传阅

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/palette.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/workflow-node.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.tsx`

- [ ] **Step 1: 写失败测试，覆盖新节点类型和配置**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 增加节点 palette、DSL 校验和 BPMN 映射**
- [ ] **Step 4: 运行相关测试**

Run: `pnpm -C frontend exec vitest run src/features/workflow/pages.test.tsx --reporter=verbose && mvn -q -f backend/pom.xml -Dtest=ProcessDslValidatorTest test`
Expected: PASS

### Task 5: 协同审批运行态与轨迹

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/collaboration/service/DefaultProcessCollaborationService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/response/ProcessTaskTraceItemResponse.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/approval-sheet-graph.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.tsx`

- [ ] **Step 1: 写失败测试，覆盖督办/会办/阅办/传阅事件**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 接入运行态查询、详情和轨迹**
- [ ] **Step 4: 运行测试**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test && pnpm -C frontend exec vitest run src/features/workbench/pages.test.tsx --reporter=verbose`
Expected: PASS

## Chunk 3: 签章闭环

### Task 6: 电子签章模型与动作

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/signature/api/SignTaskRequest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/signature/api/TaskSignatureResponse.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/signature/service/TaskSignatureService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/controller/ProcessRuntimeController.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖签章动作与详情回显**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 增加签章请求、存储、审计与回显**
- [ ] **Step 4: 运行测试**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test`
Expected: PASS

### Task 7: 前端签章入口与展示

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/approval-sheet-helpers.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/workbench.ts`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖签章按钮、时间轴、详情展示**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 接入签章按钮、提交弹层与结果展示**
- [ ] **Step 4: 运行测试**

Run: `pnpm -C frontend exec vitest run src/features/workbench/pages.test.tsx --reporter=verbose`
Expected: PASS

## Chunk 4: 统一收口

### Task 8: 工作台批量动作与状态展示

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/approval-sheet-list.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/workbench.ts`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖多选、批量认领、批量已读、批量同意、批量驳回**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 接入工作台批量操作栏**
- [ ] **Step 4: 运行测试**

Run: `pnpm -C frontend exec vitest run src/features/workbench/pages.test.tsx --reporter=verbose`
Expected: PASS

### Task 9: 最终回归

**Files:**
- Verify only

- [ ] **Step 1: 跑后端审批专项**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest,ProcessDslValidatorTest,FlowableOrchestratorRuntimeBridgeTest test`
Expected: PASS

- [ ] **Step 2: 跑前端工作台/设计器专项**

Run: `pnpm -C frontend exec vitest run src/features/workbench/pages.test.tsx src/features/workflow/pages.test.tsx --reporter=verbose`
Expected: PASS

- [ ] **Step 3: 跑类型检查和构建**

Run: `pnpm -C frontend typecheck && pnpm -C frontend build`
Expected: PASS

- [ ] **Step 4: 本地人工验收**

场景：
- 发起审批后批量认领/审批
- 督办/会办/阅办/传阅事件显示
- SLA 触发与升级记录
- 签章后审批详情回显

Plan complete and saved to `docs/superpowers/plans/2026-03-28-approval-platform-capability-closure-plan.md`. Ready to execute?
