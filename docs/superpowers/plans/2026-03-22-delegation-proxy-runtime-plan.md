# Delegation Proxy Runtime Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Archive note (2026-03-23):** 本计划是切换真实 `Flowable` 前的历史执行稿；文中 `DemoTask`、`ProcessDemoService` 和相关 demo 运行态表述仅代表当时实现起点，不代表当前正式接口或运行时现状。

**Goal:** 打通委派、代理、离职转办与转办链路增强的运行态闭环，不新增系统配置后台。

**Architecture:** 继续复用当前 `processruntime` 内存态运行模型，在 `DemoTask` 和实例事件层增加委派、代理、离职转办语义；前端继续收敛到流程中心与审批单详情页，不新增独立配置模块。代理关系复用现有鉴权 fixture 数据，离职转办只作为平台批量动作落在运行态接口和流程中心待办工具栏。

**Tech Stack:** Spring Boot, Sa-Token, MyBatis-Plus fixture auth, React, TanStack Query, React Hook Form, Zod, shadcn/ui

---

## Chunk 1: 契约与后端测试

### Task 1: 写后端失败测试覆盖委派与代理

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`

- [ ] Step 1: 新增委派、代理代办、离职转办的失败测试
- [ ] Step 2: 运行 `mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test`
- [ ] Step 3: 确认新增测试按预期失败

### Task 2: 写前端失败测试覆盖动作入口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/pages.test.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/lib/api/workbench.test.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/approval-sheet-helpers.test.ts`

- [ ] Step 1: 新增委派、代理代办文案、离职转办入口的失败测试
- [ ] Step 2: 运行 `pnpm --dir frontend test --run src/features/workbench/pages.test.tsx src/lib/api/workbench.test.ts src/features/workbench/approval-sheet-helpers.test.ts`
- [ ] Step 3: 确认新增测试按预期失败

## Chunk 2: 后端实现

### Task 3: 增加代理关系查询与管理员判断

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/identity/service/FixtureAuthService.java`

- [ ] Step 1: 增加激活代理关系查询方法与流程管理员判断方法
- [ ] Step 2: 运行后端针对性测试

### Task 4: 增加委派与离职转办接口

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/processruntime/api/DelegateTaskRequest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/processruntime/api/HandoverTasksRequest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/processruntime/api/HandoverTasksResponse.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`

- [ ] Step 1: 增加委派与离职转办接口定义
- [ ] Step 2: 运行后端针对性测试

### Task 5: 实现运行态语义

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/processruntime/service/ProcessDemoService.java`

- [ ] Step 1: 增加 `DELEGATED`、`HANDOVERED` 与代理代办可见性语义
- [ ] Step 2: 实现 `delegate` 与 `handover` 动作
- [ ] Step 3: 扩展待办、已办、详情、动作轨迹字段
- [ ] Step 4: 运行 `mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest test`

## Chunk 3: 前端实现

### Task 6: 扩展 API 契约

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/lib/api/workbench.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/lib/api/workbench.test.ts`

- [ ] Step 1: 增加委派、离职转办、新状态和新轨迹字段
- [ ] Step 2: 运行前端 API 测试

### Task 7: 扩展流程中心和审批单详情

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/pages.test.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/approval-sheet-helpers.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/approval-sheet-helpers.test.ts`

- [ ] Step 1: 增加委派对话框与离职转办对话框
- [ ] Step 2: 增加代理代办、委派、离职转办文案与轨迹显示
- [ ] Step 3: 运行前端页面测试

## Chunk 4: 联调与验证

### Task 8: 跑全量验证

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/docs/contracts/task-actions.md`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`

- [ ] Step 1: 同步契约文档
- [ ] Step 2: 运行 `./scripts/validate-contracts.sh`
- [ ] Step 3: 运行 `mvn -f backend/pom.xml test`
- [ ] Step 4: 运行 `pnpm --dir frontend test --run`
- [ ] Step 5: 运行 `pnpm --dir frontend typecheck`
- [ ] Step 6: 运行 `pnpm --dir frontend lint`
- [ ] Step 7: 运行 `pnpm --dir frontend build`
- [ ] Step 8: 提交本批次代码
