# 剩余任务总路线图 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按固定顺序推进后续开发，避免继续在 demo 运行态上横向铺功能。

**Architecture:** 先做全仓注释治理与系统管理补齐，再切换真实运行态，随后补流程管理后台、高级审批能力和 AI/PLM 扩展。每个阶段单独收口，前一阶段不完成不进入下一阶段的大规模实现。

**Tech Stack:** `React 19`, `TanStack Router`, `TanStack Query`, `zustand`, `shadcn/ui`, `Spring Boot`, `MyBatis-Plus`, `Flyway`, `Flowable`, `LiteFlow`, `Aviator`, `Spring AI`

---

## Chunk 1: 路线图冻结与文档同步

### Task 1: 冻结剩余任务执行顺序

**Files:**
- Create: `docs/superpowers/specs/2026-03-22-remaining-roadmap-design.md`
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`

- [ ] **Step 1: 写入新的路线图设计文档**

将剩余任务固定为以下顺序：

1. 全仓中文注释治理
2. 系统管理补齐与清理，再加上通知、消息管理
3. 真运行态
4. 流程管理后台
5. 高级审批能力
6. AI + PLM

- [ ] **Step 2: 在总设计文档中加入最新优先级覆盖说明**

要求：

- 后续默认以新路线图为准
- 旧的 M0-M4 叙述保留历史价值，但不再作为唯一执行顺序依据

- [ ] **Step 3: 运行文档校验**

Run: `cd /Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation && ./scripts/validate-contracts.sh`
Expected: PASS

## Chunk 2: Phase 1 注释治理

### Task 2: 输出注释治理专项计划

**Files:**
- Create: `docs/superpowers/plans/2026-03-22-comment-governance-plan.md`
- Reference: `backend/src/main/java`
- Reference: `frontend/src/features`

- [ ] **Step 1: 盘点需要强制补注释的代码范围**

至少覆盖：

- 后端控制器
- 后端服务类
- 流程 DSL / BPMN 转换
- 流程运行态
- 编排自动化
- 前端流程设计器
- 前端流程中心
- 前端 OA 页面

- [ ] **Step 2: 明确注释规则**

规则：

- 类、方法写中文注释
- 注释解释业务意图与约束
- 不做逐行废话注释
- `record`、纯常量类只在必要处补说明

- [ ] **Step 3: 形成独立实现计划**

输出一份只针对注释治理的详细计划，后续单独执行。

## Chunk 3: Phase 2 系统管理补齐与清理

### Task 3: 输出系统管理补齐专项计划

**Files:**
- Create: `docs/superpowers/plans/2026-03-22-system-management-completion-plan.md`
- Create: `docs/superpowers/specs/2026-03-22-system-management-completion-design.md`
- Reference: `frontend/src/routes/_authenticated/system`
- Reference: `frontend/src/components/layout/data/sidebar-data.ts`
- Reference: `backend/src/main/java/com/westflow/system`
- Reference: `backend/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: 盘点现有系统管理模块与缺口**

补齐目标至少包含：

- 字典管理
- 日志管理
- 监控管理
- 文件管理
- 通知管理
- 消息管理

- [ ] **Step 2: 盘点需要清理的残留页面与菜单**

至少检查：

- `apps`
- `chats`
- `tasks`
- `help-center`

- [ ] **Step 3: 拆成前后端并行任务**

要求：

- 每个功能独立 CRUD 页面
- 中文菜单
- 后端分页、模糊查询、筛选、排序、分组协议齐全

## Chunk 4: Phase 3 真运行态

### Task 4: 输出真实运行态替换专项计划

**Files:**
- Create: `docs/superpowers/plans/2026-03-22-real-runtime-plan.md`
- Reference: `backend/src/main/java/com/westflow/processruntime`
- Reference: `backend/src/main/java/com/westflow/processaction`
- Reference: `backend/src/main/java/com/westflow/orchestrator`
- Reference: `backend/src/main/java/com/westflow/processdef`
- Reference: `frontend/src/features/workbench`

- [ ] **Step 1: 盘点 demo 运行态入口**

至少覆盖：

- `ProcessRuntimeController`
- `ProcessDemoService`
- `/api/v1/process-runtime/demo/*`

- [ ] **Step 2: 明确真实运行态替换边界**

要求：

- 接入 Flowable 实例与任务模型
- 接入真实动作状态机
- 接入真实实例事件
- 保留现有前端审批单详情与流程中心页面语义

- [ ] **Step 3: 输出独立的 TDD 执行计划**

必须包含：

- 后端失败测试
- 接口兼容层策略
- 数据迁移方案
- 联调验证顺序

## Chunk 5: Phase 4 流程管理后台

### Task 5: 输出流程管理后台专项计划

**Files:**
- Create: `docs/superpowers/plans/2026-03-22-workflow-admin-completion-plan.md`
- Reference: `docs/superpowers/specs/2026-03-22-workflow-management-replan.md`
- Reference: `frontend/src/routes/_authenticated/workflow`
- Reference: `backend/src/main/java/com/westflow/processdef`
- Reference: `backend/src/main/java/com/westflow/processbinding`

- [ ] **Step 1: 固定后台模块范围**

至少包含：

- 流程版本
- 流程发布记录
- 流程实例监控
- 流程操作日志
- 审批意见配置
- 业务流程绑定

- [ ] **Step 2: 规划前后端页面与接口**

要求：

- 每个模块独立列表、详情、新建、编辑页
- 后端接口统一支持分页、模糊查询、筛选、排序

- [ ] **Step 3: 形成多代理实施计划**

拆分为：

- 流程定义/版本线
- 实例监控/操作日志线
- 审批意见/业务绑定线

## Chunk 6: Phase 5 高级审批能力

### Task 6: 输出高级审批能力专项计划

**Files:**
- Create: `docs/superpowers/plans/2026-03-22-advanced-approval-capabilities-plan.md`
- Reference: `docs/contracts/task-actions.md`
- Reference: `backend/src/main/java/com/westflow/processruntime`
- Reference: `backend/src/main/java/com/westflow/processaction`
- Reference: `frontend/src/features/workbench`

- [ ] **Step 1: 按能力分组剩余高级审批能力**

建议分组：

- 流程结构类：主子流程、包容分支、动态构建、追加
- 审批策略类：顺序会签、并行会签、或签、票签
- 运行态动作类：终止、沟通、穿越时空
- SLA 类：更完整的超时、提醒、调度与策略

- [ ] **Step 2: 明确每项能力必须是“设计器 + 运行态 + 详情轨迹”闭环**

- [ ] **Step 3: 输出独立实施计划**

## Chunk 7: Phase 6 AI + PLM

### Task 7: 输出 AI 与 PLM 专项计划

**Files:**
- Create: `docs/superpowers/plans/2026-03-22-ai-plm-plan.md`
- Reference: `backend/src/main/java/com/westflow/ai`
- Reference: `backend/pom.xml`
- Reference: `frontend/src/features`
- Reference: `docs/contracts/ai-tools.md`

- [ ] **Step 1: 拆分 AI 与 PLM 两条子线**

AI 子线至少包含：

- AI Copilot
- 对话历史
- 权限控制
- 流程设计智能体
- 智能填报
- AI 发起流程
- AI 处理待办
- AI 统计问答

PLM 子线至少包含：

- PLM 业务表
- 流程绑定
- 实际审批样例

- [ ] **Step 2: 明确依赖前置条件**

前置条件：

- 真运行态稳定
- 流程管理后台稳定
- 审批动作和轨迹模型稳定

- [ ] **Step 3: 输出 AI 与 PLM 分阶段计划**

## Chunk 8: 最终路线图验证与提交

### Task 8: 校验并提交路线图文档

**Files:**
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`
- Create: `docs/superpowers/specs/2026-03-22-remaining-roadmap-design.md`
- Create: `docs/superpowers/plans/2026-03-22-remaining-roadmap-plan.md`

- [ ] **Step 1: 检查文档表述一致**

确认：

- 总设计文档
- 流程管理重规划文档
- 新路线图设计
- 新路线图计划

之间不存在顺序冲突。

- [ ] **Step 2: 提交文档变更**

Run:

```bash
git -C /Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation add \
  docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md \
  docs/superpowers/specs/2026-03-22-remaining-roadmap-design.md \
  docs/superpowers/plans/2026-03-22-remaining-roadmap-plan.md
git -C /Users/west/dev/code/west/west-flow-ai/.worktrees/codex-m0-foundation commit -m "docs: 冻结剩余任务执行顺序"
```

Expected: PASS
