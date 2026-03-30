# Workflow Designer Branching Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将流程设计器的条件配置从节点面板迁移到连线配置，并支持条件网关直接新增条件分支。

**Architecture:** 保持现有 DSL 与边数据模型不变，只调整前端设计器的状态模型、画布边交互和属性面板入口。`selectedEdgeId` 作为连线编辑态的唯一来源，节点面板退回到网关级配置，连线面板负责分支条件配置。

**Tech Stack:** React, TypeScript, Zustand, React Flow, React Hook Form, Vitest

---

## Chunk 1: 状态与边交互模型

### Task 1: 扩展设计器状态支持连线选中

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/types.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/store.ts`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/store.test.ts`

- [ ] 为 `WorkflowSnapshot` 增加 `selectedEdgeId`
- [ ] 为 store 增加 `setSelectedEdgeId`
- [ ] 点击空白区域时同时清空节点和连线选中
- [ ] 补 store 测试覆盖节点/连线互斥选中

### Task 2: 重构边加号显示规则

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/workflow-edge.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/types.ts`

- [ ] 给边组件传入“是否允许 quick insert”的判定
- [ ] 隐藏条件网关出边和汇聚入边上的加号
- [ ] 保留普通主干连线的插入能力

## Chunk 2: 条件分支新增能力

### Task 3: 增加条件网关“新增条件分支”

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/store.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/config.ts`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/store.test.ts`

- [ ] 新增 `addBranchOnGateway`
- [ ] 自动创建新审批节点和新分支边
- [ ] 自动设置默认分支标签与默认条件
- [ ] 新分支创建后自动选中新边

### Task 4: 在节点卡片底部暴露新增条件分支入口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/palette.ts`（如需要模板默认文案）

- [ ] 仅对排他/包容分支节点显示按钮
- [ ] 按钮满足 44x44 触达面积
- [ ] 保持当前视觉体系，避免引入多余强调色

## Chunk 3: 右侧配置面板重构

### Task 5: 新增连线配置面板

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/config.ts`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.test.tsx`

- [ ] 新增 `selectedEdge` 输入
- [ ] 当选中分支连线时显示分支配置表单
- [ ] 支持编辑：
  - 分支名称
  - 条件类型
  - 字段比较
  - 公式
  - 表达式
  - 包容分支优先级
- [ ] 默认分支只显示说明，不显示条件编辑

### Task 6: 精简条件/包容网关节点面板

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.test.tsx`

- [ ] 排他网关仅保留默认分支等网关级配置
- [ ] 包容网关仅保留汇聚策略、默认分支、必选分支数
- [ ] 移除节点面板里逐条分支条件编辑区

## Chunk 4: 回归与验证

### Task 7: 补设计器交互测试

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/store.test.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.test.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.test.tsx`

- [ ] 新增条件分支测试
- [ ] 连线配置优先于节点配置测试
- [ ] 不该显示 quick insert 的边隐藏测试

### Task 8: 运行验证

**Files:**
- Verify only

- [ ] Run: `pnpm -C /Users/west/dev/code/west/west-flow-ai/frontend exec vitest run src/features/workflow/designer/store.test.ts src/features/workflow/designer/node-config-panel.test.tsx src/features/workflow/pages.test.tsx --reporter=verbose`
- [ ] Run: `pnpm -C /Users/west/dev/code/west/west-flow-ai/frontend typecheck`
- [ ] Run: `pnpm -C /Users/west/dev/code/west/west-flow-ai/frontend lint`
- [ ] 手动打开设计器验证：
  - 条件网关出边点击后进入连线面板
  - 中间误导性加号消失
  - 节点底部可新增条件分支

Plan complete and saved to `docs/superpowers/plans/2026-03-30-workflow-designer-branching-plan.md`. Ready to execute?
