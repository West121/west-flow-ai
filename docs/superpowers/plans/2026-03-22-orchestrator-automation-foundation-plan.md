# Orchestrator Automation Foundation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Archive note (2026-03-23):** 本计划保留自动化能力接入 demo 运行态时期的实现顺序；文中 `processruntime` demo 模型与“旧内存运行态服务”仅表示历史前提，不代表当前正式平台。

**Goal:** 打通超时审批、自动提醒、定时节点、触发节点从设计器配置到运行态执行再到详情展示的完整闭环。

**Architecture:** 继续复用现有 `processruntime` 模型，在其上增加 `orchestrator` 和 `notification` 两个平台域。`orchestrator` 负责自动化扫描和执行，`notification` 负责渠道配置、发送器和发送日志；前端同步扩展设计器节点配置、系统管理独立页面，以及流程中心和审批单详情的自动化展示。

**Tech Stack:** Spring Boot, Sa-Token, MyBatis-Plus, Flyway, React, TanStack Router, TanStack Query, React Hook Form, Zod, shadcn/ui, React Flow, Axios

---

## Chunk 1: 契约与失败测试

### Task 1: 为自动化运行态补后端失败测试

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/test/java/com/westflow/orchestrator/api/OrchestratorControllerTest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/test/java/com/westflow/notification/api/NotificationChannelControllerTest.java`

- [ ] Step 1: 先写超时审批、自动提醒、定时节点、触发节点和通知渠道配置的失败测试
- [ ] Step 2: 运行 `mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest,OrchestratorControllerTest,NotificationChannelControllerTest test`
- [ ] Step 3: 确认测试按预期失败

### Task 2: 为前端设计器和管理页补失败测试

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workflow/pages.test.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/pages.test.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/system/notification-pages.test.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/system/trigger-pages.test.tsx`

- [ ] Step 1: 先写设计器配置、通知渠道页、触发器页和详情展示的失败测试
- [ ] Step 2: 运行 `pnpm --dir frontend test --run src/features/workflow/pages.test.tsx src/features/workbench/pages.test.tsx src/features/system/notification-pages.test.tsx src/features/system/trigger-pages.test.tsx`
- [ ] Step 3: 确认测试按预期失败

## Chunk 2: 后端 notification 域

### Task 3: 增加通知渠道配置 API

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/notification/api/*.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/notification/service/NotificationChannelService.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/notification/mapper/NotificationChannelMapper.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/resources/db/migration/V1__init.sql`

- [ ] Step 1: 先建通知渠道表和种子数据
- [ ] Step 2: 实现列表、详情、创建、编辑、options 接口
- [ ] Step 3: 跑通知渠道后端测试

### Task 4: 增加通知发送器与发送日志

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/notification/service/NotificationDispatchService.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/notification/provider/*.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/notification/mapper/NotificationLogMapper.java`

- [ ] Step 1: 实现 `IN_APP`、`EMAIL`、`WEBHOOK` 发送器
- [ ] Step 2: 实现 `SMS`、`WECHAT`、`DINGTALK` mock provider
- [ ] Step 3: 写发送日志并跑针对性测试

## Chunk 3: 后端 orchestrator 域

### Task 5: 增加调度作业模型与扫描入口

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/orchestrator/api/*.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/orchestrator/service/OrchestratorService.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/orchestrator/mapper/*.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/resources/db/migration/V1__init.sql`

- [ ] Step 1: 建调度作业表、自动执行表、种子数据
- [ ] Step 2: 实现手动扫描执行接口
- [ ] Step 3: 跑 orchestrator 针对性测试

### Task 6: 实现超时审批与自动提醒执行

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/service/legacy-runtime-service.java`（历史 demo 运行态实现，占位说明）
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/processruntime/api/*.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/orchestrator/service/OrchestratorService.java`

- [ ] Step 1: 先支持超时自动 `APPROVE/REJECT`
- [ ] Step 2: 再支持提醒扫描和多渠道发送
- [ ] Step 3: 把自动动作和提醒写进实例事件、轨迹和日志
- [ ] Step 4: 跑后端针对性测试

### Task 7: 实现定时节点与触发节点执行

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/service/legacy-runtime-service.java`（历史 demo 运行态实现，占位说明）
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/orchestrator/service/TriggerExecutionService.java`

- [ ] Step 1: 先支持 `timer` 节点到时自动推进
- [ ] Step 2: 再支持 `trigger` 节点立即执行和定时执行
- [ ] Step 3: 实现失败重试和执行事件记录
- [ ] Step 4: 跑后端针对性测试

## Chunk 4: 前端设计器与 DSL

### Task 8: 扩展设计器节点配置

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workflow/designer/node-config-panel.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workflow/designer/config.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workflow/designer/dsl.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workflow/pages.tsx`

- [ ] Step 1: 给审批节点增加超时审批和自动提醒配置
- [ ] Step 2: 增加 `timer` 和 `trigger` 节点配置 UI
- [ ] Step 3: 让 DSL 保存、详情回填和校验通过
- [ ] Step 4: 跑前端设计器测试

## Chunk 5: 前端系统管理页面

### Task 9: 实现通知渠道配置页面

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/system/notification-pages.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/lib/api/notification-channels.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/routes/_authenticated/system/notification-channels/*.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/components/layout/data/sidebar-data.ts`

- [ ] Step 1: 列表页接真实分页、模糊查询、筛选、排序
- [ ] Step 2: 创建、编辑、详情页接真实接口
- [ ] Step 3: 跑前端通知渠道测试

### Task 10: 实现触发器管理页面

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/system/trigger-pages.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/lib/api/triggers.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/routes/_authenticated/system/triggers/*.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/components/layout/data/sidebar-data.ts`

- [ ] Step 1: 实现触发器列表、详情、新建、编辑页
- [ ] Step 2: 接真实接口并保持中文独立页面
- [ ] Step 3: 跑前端触发器测试

## Chunk 6: 流程中心与审批单详情展示

### Task 11: 展示自动动作与通知记录

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/approval-sheet-helpers.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/lib/api/workbench.ts`

- [ ] Step 1: 详情页增加自动动作轨迹区
- [ ] Step 2: 详情页增加通知发送记录区
- [ ] Step 3: 流程中心列表补自动化状态展示
- [ ] Step 4: 跑前端工作台测试

## Chunk 7: 文档、回归与提交

### Task 12: 同步契约并做全量验证

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/docs/contracts/process-dsl.md`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/docs/contracts/task-actions.md`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`

- [ ] Step 1: 同步 DSL 与运行态契约文档
- [ ] Step 2: 运行 `./scripts/validate-contracts.sh`
- [ ] Step 3: 运行 `mvn -f backend/pom.xml test`
- [ ] Step 4: 运行 `pnpm --dir frontend test --run`
- [ ] Step 5: 运行 `pnpm --dir frontend typecheck`
- [ ] Step 6: 运行 `pnpm --dir frontend lint`
- [ ] Step 7: 运行 `pnpm --dir frontend build`
- [ ] Step 8: 提交本批次代码
