# Runtime Closure And Foundation Upgrade Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Archive note (2026-03-23):** 本计划保留从 demo 运行态过渡到系统侧闭环时的历史执行背景；下文若出现“旧内存运行态服务”等旧实现路径，仅作归档说明，不代表当前正式口径。

**Goal:** 补齐委派、代理、离职转办的系统侧闭环，并完成前后端中文注释治理、后端 JDK 21 统一、record 优先的 Lombok 整理。

**Architecture:** 在现有运行态能力不回退的前提下，新增系统管理配置与执行页面，把协作动作从“只有运行态入口”升级为“配置 + 执行 + 轨迹 + 权限”完整闭环。基础改造方面，前后端仅治理手写业务代码，后端保持 DTO `record` 体系不动，只对可变内部模型与样板 POJO 选择性引入 Lombok，并统一编译运行目标到 JDK 21。

**Tech Stack:** Spring Boot, Sa-Token, MyBatis-Plus, Flyway, React, TanStack Router, TanStack Query, React Hook Form, Zod, shadcn/ui, Lombok, JDK 21

---

## Chunk 1: 设计同步与失败测试

### Task 1: 为系统侧闭环补后端失败测试

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/test/java/com/westflow/processruntime/api/ProcessRuntimeControllerTest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/test/java/com/westflow/system/agent/api/SystemAgentControllerTest.java`

- [ ] Step 1: 先写代理关系管理与离职转办预览/执行的失败测试
- [ ] Step 2: 运行 `mvn -f backend/pom.xml -Dtest=ProcessRuntimeControllerTest,SystemAgentControllerTest test`
- [ ] Step 3: 确认新测试按预期失败

### Task 2: 为前端页面补失败测试

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/system/agent-pages.test.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/workbench/pages.test.tsx`

- [ ] Step 1: 先写代理关系管理页与离职转办执行页失败测试
- [ ] Step 2: 运行 `pnpm --dir frontend test --run src/features/system/agent-pages.test.tsx src/features/workbench/pages.test.tsx`
- [ ] Step 3: 确认新测试按预期失败

## Chunk 2: 后端系统侧闭环

### Task 3: 增加代理关系领域接口

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/system/agent/api/SystemAgentController.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/system/agent/service/SystemAgentService.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/system/agent/api/*.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/resources/db/migration/V1__init.sql`

- [ ] Step 1: 建代理关系表、种子数据与 CRUD/分页/options 接口
- [ ] Step 2: 接到现有权限模型，仅流程管理员可访问
- [ ] Step 3: 运行后端针对性测试

### Task 4: 增加离职转办预览与执行接口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/legacy-runtime-service.java`（历史 demo 运行态实现，占位说明）
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/com/westflow/processruntime/api/*HandoverPreview*.java`

- [ ] Step 1: 先做离职转办预览接口
- [ ] Step 2: 再做执行接口与结果明细
- [ ] Step 3: 跑后端针对性测试

## Chunk 3: 前端系统管理页面

### Task 5: 实现代理关系管理页面

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/system/agent-pages.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/lib/api/system-agents.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/routes/_authenticated/system/agents/*.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/components/layout/data/sidebar-data.ts`

- [ ] Step 1: 列表页接真实分页、模糊查询、筛选、排序
- [ ] Step 2: 创建 / 编辑 / 详情独立页面接真实接口
- [ ] Step 3: 跑前端针对性测试

### Task 6: 实现离职转办执行页

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/features/system/agent-pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/lib/api/system-agents.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/routes/_authenticated/system/handover/*.tsx`

- [ ] Step 1: 做独立执行页与预览区
- [ ] Step 2: 接执行确认与结果明细
- [ ] Step 3: 跑前端针对性测试

## Chunk 4: 中文注释治理

### Task 7: 后端中文注释治理

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/**/*.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/test/java/**/*.java`

- [ ] Step 1: 先补流程运行时、系统管理、OA、鉴权模块中文注释
- [ ] Step 2: 再扫其余手写后端代码
- [ ] Step 3: 确保注释只解释关键意图与复杂逻辑

### Task 8: 前端中文注释治理

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/**/*.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/frontend/src/**/*.tsx`

- [ ] Step 1: 先补流程中心、OA、系统管理、共享 CRUD 组件中文注释
- [ ] Step 2: 再扫其余手写前端业务代码
- [ ] Step 3: 排除生成文件、JSON、纯素材组件

## Chunk 5: Lombok 与 JDK 21

### Task 9: 升级到 JDK 21

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/pom.xml`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`

- [ ] Step 1: 将 `java.version` 统一到 `21`
- [ ] Step 2: 补最小必要编译配置
- [ ] Step 3: 跑后端全量测试

### Task 10: 选择性引入 Lombok

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/pom.xml`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/backend/src/main/java/**/*.java`

- [ ] Step 1: 引入 Lombok 依赖
- [ ] Step 2: 只重构可变 POJO、内部模型、显式样板构造类
- [ ] Step 3: 保持 request/response/值对象 `record` 不动
- [ ] Step 4: 跑后端全量测试

## Chunk 6: 全量回归与提交

### Task 11: 跑全量验证

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/docs/contracts/*.md`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation/docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`

- [ ] Step 1: 同步契约与总规格
- [ ] Step 2: 运行 `./scripts/validate-contracts.sh`
- [ ] Step 3: 运行 `mvn -f backend/pom.xml test`
- [ ] Step 4: 运行 `pnpm --dir frontend test --run`
- [ ] Step 5: 运行 `pnpm --dir frontend typecheck`
- [ ] Step 6: 运行 `pnpm --dir frontend lint`
- [ ] Step 7: 运行 `pnpm --dir frontend build`
- [ ] Step 8: 提交本批次代码
