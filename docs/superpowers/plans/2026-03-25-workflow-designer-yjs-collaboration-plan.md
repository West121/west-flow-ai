# Workflow Designer Yjs Collaboration Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为流程设计器增加基于 Yjs 的 Phase 1 协同编辑能力，在不破坏现有保存草稿/发布链路的前提下支持多人同步节点、连线、属性和在线状态。

**Architecture:** 保留现有 React Flow + zustand 设计器架构，在其下增加 `Yjs` 协同层。所有 UI 仍只读写 store，协同绑定层负责 store 与 ydoc 双向同步，并通过 awareness 展示在线成员、远端选中态和编辑提示。

**Tech Stack:** React 19, Zustand, React Flow (@xyflow/react), Yjs, y-websocket, Vitest

---

## Chunk 1: 协同基础设施

### Task 1: 安装依赖并建立协同目录

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/package.json`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer-collab/types.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer-collab/ydoc.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer-collab/provider.ts`

- [ ] **Step 1: 添加协同依赖**

添加：
- `yjs`
- `y-websocket`

- [ ] **Step 2: 定义协同层类型**

在 `types.ts` 中定义：
- 协同房间标识
- awareness 用户态
- 协同快照结构

- [ ] **Step 3: 创建 ydoc 工具**

在 `ydoc.ts` 中实现：
- 创建 ydoc
- 获取 `nodes / edges / meta / ui` 容器
- 销毁 ydoc

- [ ] **Step 4: 创建 provider 封装**

在 `provider.ts` 中封装：
- 建立 websocket provider
- 关闭 provider
- 连接状态事件监听

- [ ] **Step 5: 运行类型检查**

Run: `pnpm -C frontend typecheck`
Expected: PASS

## Chunk 2: store 与 Yjs 双向绑定

### Task 2: 为设计器 store 增加远端应用入口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/store.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer-collab/bindings.ts`

- [ ] **Step 1: 给 store 增加远端写回入口**

新增无历史污染入口，例如：
- `applyRemoteSnapshot`
- `applyRemoteSelection`

要求：
- 不进入 undo/redo 历史
- 不重复广播

- [ ] **Step 2: 编写 store -> ydoc 同步**

在 `bindings.ts` 中监听 store 快照变化并写入：
- nodes
- edges
- selectedNodeId
- viewport

- [ ] **Step 3: 编写 ydoc -> store 同步**

在 `bindings.ts` 中监听远端变更并回写 store。

- [ ] **Step 4: 防止回环**

使用本地标志位区分：
- 本地提交
- 远端回放

- [ ] **Step 5: 补单测**

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer-collab/bindings.test.ts`

覆盖：
- 本地更新写入 ydoc
- 远端更新回写 store
- 远端更新不污染历史栈

- [ ] **Step 6: 运行测试**

Run: `pnpm -C frontend exec vitest run src/features/workflow/designer-collab/bindings.test.ts --reporter=verbose`
Expected: PASS

## Chunk 3: awareness 与 UI 接入

### Task 3: 在线成员、选中态和编辑提示

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer-collab/awareness.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/designer-layout.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/workflow-node.tsx`

- [ ] **Step 1: 实现 awareness 映射**

在 `awareness.ts` 中同步：
- `userId`
- `displayName`
- `color`
- `selectedNodeId`
- `editingNodeId`

- [ ] **Step 2: 顶部增加在线成员**

在 `designer-layout.tsx` 增加：
- 在线头像
- 协同状态 badge

- [ ] **Step 3: 节点增加远端状态提示**

在 `workflow-node.tsx` 中展示：
- 远端选中边框
- 正在编辑标签

- [ ] **Step 4: 补组件测试**

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.test.tsx`

验证：
- 在线成员 UI 可见
- 协同状态可见

- [ ] **Step 5: 运行测试**

Run: `pnpm -C frontend exec vitest run src/features/workflow/pages.test.tsx --reporter=verbose`
Expected: PASS

## Chunk 4: 设计器页面接入

### Task 4: 在设计器页面挂载协同生命周期

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.tsx`

- [ ] **Step 1: 计算协同房间 key**

规则：
- 已有流程：`workflow-designer:{processDefinitionId}`
- 新建流程：`workflow-designer:draft:{draftKey}`

- [ ] **Step 2: 页面挂载 provider**

在 `WorkflowDesignerWorkspace` 生命周期内：
- 创建 ydoc
- 创建 provider
- 建立 store 绑定
- 建立 awareness

- [ ] **Step 3: 保存/发布保持不变**

确认：
- 保存草稿仍从当前 store 快照导出 DSL
- 发布流程仍调用现有发布接口

- [ ] **Step 4: 处理新建流程切换**

保存成功后：
- 从 draft room 切换到正式 room

- [ ] **Step 5: 补集成测试**

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.test.tsx`

覆盖：
- 设计器挂载协同层
- 保存后 room key 切换逻辑

- [ ] **Step 6: 运行测试**

Run: `pnpm -C frontend exec vitest run src/features/workflow/pages.test.tsx src/features/workflow/designer-collab/bindings.test.ts --reporter=verbose`
Expected: PASS

## Chunk 5: 回归与验证

### Task 5: 统一验证

**Files:**
- No code changes expected

- [ ] **Step 1: 前端专项测试**

Run:
`pnpm -C frontend exec vitest run src/features/workflow/pages.test.tsx src/features/workflow/designer/store.test.ts src/features/workflow/designer/dsl.test.ts src/features/workflow/designer-collab/bindings.test.ts --reporter=verbose`

Expected: PASS

- [ ] **Step 2: 前端类型检查**

Run: `pnpm -C frontend typecheck`
Expected: PASS

- [ ] **Step 3: 前端构建**

Run: `pnpm -C frontend build`
Expected: PASS

- [ ] **Step 4: 手工验证**

两个浏览器窗口打开同一流程定义，验证：
- 节点拖拽同步
- 连线同步
- 属性修改同步
- 选中态显示
- 保存草稿正常
- 发布流程正常

- [ ] **Step 5: 提交**

```bash
git add frontend/package.json frontend/src/features/workflow frontend/src/features/workflow/designer-collab
git commit -m "feat: 增加流程设计器Yjs协同编辑基础能力"
```

Plan complete and saved to `docs/superpowers/plans/2026-03-25-workflow-designer-yjs-collaboration-plan.md`. Ready to execute?
