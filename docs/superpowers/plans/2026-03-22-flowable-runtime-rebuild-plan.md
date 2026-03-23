# Phase 3 真实 Flowable 运行态重构 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Archive note (2026-03-23):** 本计划记录从 demo 运行态切换到真实 `Flowable` 的执行步骤；文中旧 demo 运行态路径仅表示迁移前的历史起点。

**Goal:** 将当前基于旧内存运行态服务的内存态运行替换为真实 `Flowable BPMN` 运行态，并完成流程主链路、现有中国式动作能力、自动化能力和工作台前端的真实闭环。

**Architecture:** 后端新增 `flowable helper/facade` 封装层与真实运行态服务层，统一接管 BPMN 部署、实例查询、任务动作、历史轨迹、变量和评论；前端工作台和审批单详情切换到真实实例/任务/历史接口；最终删除旧 demo 运行态路径与旧内存运行态服务。实现按 `3A 引擎接线`、`3B 工作台重建`、`3C 自动化与轨迹重接` 三个子闭环推进，但整体仍属于同一个 Phase 3 闭环。

**Tech Stack:** Spring Boot 3.4, Java 21, Flowable 7 BPMN Engine, MyBatis-Plus, PostgreSQL/H2, Sa-Token, React, TanStack Query, React Hook Form, Zod, React Flow, pnpm, Maven

---

## 文件结构

### 后端新增/重构

- Create: `backend/src/main/java/com/westflow/flowable/engine/FlowableEngineFacade.java`
- Create: `backend/src/main/java/com/westflow/flowable/helper/FlowableQueryHelper.java`
- Create: `backend/src/main/java/com/westflow/flowable/helper/FlowableCommandHelper.java`
- Create: `backend/src/main/java/com/westflow/flowable/helper/FlowableVariableHelper.java`
- Create: `backend/src/main/java/com/westflow/flowable/helper/FlowableHistoryHelper.java`
- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableDeploymentService.java`
- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableRuntimeQueryService.java`
- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableTaskActionService.java`
- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableRuntimeEventService.java`
- Create: `backend/src/main/java/com/westflow/processruntime/assembler/FlowableApprovalSheetAssembler.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDefinitionService.java`
- Modify: `backend/src/main/java/com/westflow/orchestrator/service/OrchestratorService.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-test.yml`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Delete: `backend/src/main/java/com/westflow/processruntime/service/<legacy-runtime-service>.java`

### 后端测试

- Create: `backend/src/test/java/com/westflow/processruntime/service/FlowableRuntimeQueryServiceTest.java`
- Create: `backend/src/test/java/com/westflow/processruntime/service/FlowableTaskActionServiceTest.java`
- Create: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerFlowableTest.java`
- Create: `backend/src/test/java/com/westflow/flowable/helper/FlowableHistoryHelperTest.java`
- Modify: 现有与 `demo` 运行态耦合的运行态测试

### 前端新增/重构

- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-list.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-graph.tsx`
- Modify: `frontend/src/features/workbench/automation-sections.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-helpers.ts`
- Modify: `frontend/src/features/oa/pages.tsx`
- Modify: `frontend/src/features/oa/detail-sections.tsx`
- Modify: `frontend/src/lib/api/workflow.ts`
- Modify: 相关工作台路由与审批单详情路由

### 文档/契约

- Modify: `docs/contracts/task-actions.md`
- Modify: `docs/contracts/process-dsl.md`
- Modify: `docs/superpowers/specs/2026-03-22-remaining-roadmap-design.md`

---

## Chunk 1: 真实 Flowable 基础接线

### Task 1: 启用 Flowable BPMN 测试环境并移除 demo 依赖前提

**Files:**
- Modify: `backend/src/main/resources/application-test.yml`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/westflow/processruntime/service/FlowableRuntimeQueryServiceTest.java`

- [ ] **Step 1: 写失败的 Flowable 启动测试**

编写集成测试，启动 Spring 容器后能注入 `RepositoryService`、`RuntimeService`、`TaskService`、`HistoryService`。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=FlowableRuntimeQueryServiceTest test`

Expected: 因测试环境排除了 Flowable 自动配置或缺少配置而失败。

- [ ] **Step 3: 最小化修改测试配置**

调整 `application-test.yml`，去掉对 Flowable BPMN 必需自动配置的排除，只保留无关模块禁用策略。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -Dtest=FlowableRuntimeQueryServiceTest test`

Expected: 通过并能拿到核心 Flowable 服务。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application-test.yml backend/src/main/resources/application.yml backend/src/test/java/com/westflow/processruntime/service/FlowableRuntimeQueryServiceTest.java
git commit -m "test: 启用flowable bpm测试环境"
```

### Task 2: 建立 Flowable helper / facade 基础层

**Files:**
- Create: `backend/src/main/java/com/westflow/flowable/engine/FlowableEngineFacade.java`
- Create: `backend/src/main/java/com/westflow/flowable/helper/FlowableQueryHelper.java`
- Create: `backend/src/main/java/com/westflow/flowable/helper/FlowableCommandHelper.java`
- Create: `backend/src/main/java/com/westflow/flowable/helper/FlowableVariableHelper.java`
- Create: `backend/src/main/java/com/westflow/flowable/helper/FlowableHistoryHelper.java`
- Test: `backend/src/test/java/com/westflow/flowable/helper/FlowableHistoryHelperTest.java`

- [ ] **Step 1: 写失败测试，覆盖 helper 的最小查询与历史转换能力**
- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=FlowableHistoryHelperTest test`

- [ ] **Step 3: 写最小 helper/facade 实现**

要求：
- 统一封装核心引擎服务
- 统一封装任务查询、变量读取、历史活动转换
- 中文注释到位

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -Dtest=FlowableHistoryHelperTest test`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/flowable backend/src/test/java/com/westflow/flowable/helper/FlowableHistoryHelperTest.java
git commit -m "feat: 新增flowable helper封装层"
```

---

## Chunk 2: 3A 引擎接线与最小实例闭环

### Task 3: 真实 BPMN 发布与实例启动

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableDeploymentService.java`
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDefinitionService.java`
- Test: `backend/src/test/java/com/westflow/processruntime/service/FlowableRuntimeQueryServiceTest.java`

- [ ] **Step 1: 写失败测试，发布 BPMN 后能部署到 Flowable 并启动实例**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 BPMN 部署、实例启动、业务绑定变量注入**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: Commit**

### Task 4: 真实待办查询、认领、完成动作

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableRuntimeQueryService.java`
- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableTaskActionService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Test: `backend/src/test/java/com/westflow/processruntime/service/FlowableTaskActionServiceTest.java`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerFlowableTest.java`

- [ ] **Step 1: 写失败测试，覆盖待办查询、认领、完成**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现最小真实任务查询与动作**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: Commit**

---

## Chunk 3: 3B 工作台与审批单详情重建

### Task 5: 重建实例详情、历史、评论、变量与审批单组装

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/assembler/FlowableApprovalSheetAssembler.java`
- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableRuntimeEventService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerFlowableTest.java`

- [ ] **Step 1: 写失败测试，覆盖审批单详情、实例轨迹、历史任务、评论、变量**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现审批单组装与平台扩展事件记录**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: Commit**

### Task 6: 把当前中国式动作迁到真实 Flowable

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableTaskActionService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Test: `backend/src/test/java/com/westflow/processruntime/service/FlowableTaskActionServiceTest.java`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerFlowableTest.java`

- [ ] **Step 1: 写失败测试，逐项覆盖现有动作**

覆盖范围：
- 退回
- 驳回
- 跳转
- 拿回
- 唤醒
- 加签
- 减签
- 撤销
- 催办
- 已阅
- 委派
- 代理
- 离职转办
- 抄送我真实模型

- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 按最小实现逐项接入真实 Flowable**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: Commit**

### Task 7: 前端工作台和审批单详情切换到真实运行态接口

**Files:**
- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-list.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-graph.tsx`
- Modify: `frontend/src/features/workbench/automation-sections.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-helpers.ts`
- Modify: `frontend/src/features/oa/pages.tsx`
- Modify: `frontend/src/features/oa/detail-sections.tsx`
- Test: `frontend/src/features/workbench/pages.test.tsx`
- Test: `frontend/src/features/oa/pages.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖工作台列表和详情页在新接口模型上的渲染**
- [ ] **Step 2: 运行测试确认失败**

Run:
- `pnpm --dir frontend test --run workbench`
- `pnpm --dir frontend test --run oa`

- [ ] **Step 3: 最小改造前端 API 和页面**

要求：
- 去掉 `/demo` 路径依赖
- 保留现有页面结构
- 使用新实例/任务/历史模型驱动待办、已办、我发起、抄送我和详情页

- [ ] **Step 4: 运行测试确认通过**

- [ ] **Step 5: Commit**

---

## Chunk 4: 3C 自动化与轨迹重接

### Task 8: 自动化能力接回真实 Flowable 实例和任务

**Files:**
- Modify: `backend/src/main/java/com/westflow/orchestrator/service/OrchestratorService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableRuntimeEventService.java`
- Modify: `frontend/src/features/workbench/automation-sections.tsx`
- Test: `backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerFlowableTest.java`

- [ ] **Step 1: 写失败测试，覆盖超时审批、自动提醒、定时节点、触发节点的真实运行态事件**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 接回真实 Flowable 作业、实例、任务与事件**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: Commit**

### Task 9: 删除 demo 运行态残留并完成正式 API 切换

**Files:**
- Delete: `backend/src/main/java/com/westflow/processruntime/service/<legacy-runtime-service>.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Modify: `docs/contracts/task-actions.md`
- Modify: `docs/contracts/process-dsl.md`
- Modify: `docs/superpowers/specs/2026-03-22-remaining-roadmap-design.md`

- [ ] **Step 1: 写失败测试，确保旧 demo 路径不再可用，正式路径可用**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 删除 demo 服务和旧语义，更新契约文档**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: Commit**

---

## Chunk 5: 集成验证与闭环提交

### Task 10: 全量验证并提交 Phase 3

**Files:**
- Review all modified files from this plan

- [ ] **Step 1: 运行后端全量测试**

Run: `mvn -f backend/pom.xml test`

Expected: PASS

- [ ] **Step 2: 运行前端类型检查和测试**

Run:
- `pnpm --dir frontend typecheck`
- `pnpm --dir frontend test --run`

Expected: PASS

- [ ] **Step 3: 运行前端 lint 和构建**

Run:
- `pnpm --dir frontend lint`
- `pnpm --dir frontend build`

Expected: PASS，允许记录已知非阻塞 warning，但不能有 error

- [ ] **Step 4: 运行契约校验**

Run: `./scripts/validate-contracts.sh`

Expected: PASS

- [ ] **Step 5: 提交 Phase 3 总变更**

```bash
git add backend frontend docs
git commit -m "feat: 切换到真实flowable运行态"
```

---

## 多代理拆分建议

- Agent A：Flowable helper/facade + 引擎接线 + 最小实例闭环
- Agent B：真实任务动作迁移，包括中国式动作与权限校验
- Agent C：工作台、审批单详情、OA 发起与前端 API 切换
- Agent D：自动化能力、轨迹事件、通知记录重接
- 主线程：文档、迁移收口、冲突合并、正式 API 切换、全量验证

## 风险与约束

- 不要在 `Phase 3` 新增 `Phase 5` 功能
- 不要再次引入 demo 兼容层
- 不要引入无关 Flowable 模块
- Flyway 仍只维护 `V1__init.sql`
- 所有新后端类与方法继续保持简明中文注释
