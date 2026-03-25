# ProTable / ProForm Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 WestFlow 自研 `ProTable / ProForm`，并首批接入组织管理与 OA 列表/表单。

**Architecture:** 在现有 `TanStack Table + react-hook-form + zod + PageShell + ListQuerySearch` 基线上新增壳层组件，不引入外部 Pro 组件体系。先保留旧 `ResourceListPage`，通过新增 `ProTable/ProForm` 做渐进替换。

**Tech Stack:** React, TanStack Table, react-hook-form, zod, TanStack Router, shadcn/ui

---

## Chunk 1: ProTable Core

### Task 1: 创建 ProTable 基础外壳

**Files:**
- Create: `frontend/src/features/shared/pro-table/pro-table.tsx`
- Create: `frontend/src/features/shared/pro-table/index.ts`
- Modify: `frontend/src/features/shared/crud/resource-list-page.tsx`
- Test: `frontend/src/features/shared/pro-table/pro-table.test.tsx`

- [ ] **Step 1: 写失败测试，定义 ProTable 的基础渲染能力**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现最小 ProTable 外壳，承接搜索、表格、分页**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: commit**

### Task 2: 统一工具栏能力

**Files:**
- Create: `frontend/src/features/shared/pro-table/pro-table-toolbar.tsx`
- Create: `frontend/src/features/shared/pro-table/pro-table-density.tsx`
- Create: `frontend/src/features/shared/pro-table/pro-table-refresh.tsx`
- Modify: `frontend/src/components/data-table/toolbar.tsx`
- Test: `frontend/src/features/shared/pro-table/pro-table-toolbar.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖刷新、密度、搜索区布局**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 ProTable Toolbar，并保留列设置接线**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: commit**

### Task 3: 统一导出入口

**Files:**
- Create: `frontend/src/features/shared/pro-table/pro-table-export.tsx`
- Modify: `frontend/src/features/shared/pro-table/pro-table.tsx`
- Test: `frontend/src/features/shared/pro-table/pro-table-export.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖导出范围菜单**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现导出入口与范围选择**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: commit**

---

## Chunk 2: 筛选与看板模式

### Task 4: 统一筛选面板

**Files:**
- Create: `frontend/src/features/shared/pro-table/pro-table-filters.tsx`
- Modify: `frontend/src/features/shared/table/query-contract.ts`
- Modify: `frontend/src/features/shared/pro-table/pro-table.tsx`
- Test: `frontend/src/features/shared/pro-table/pro-table-filters.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖筛选面板开关与查询态回写**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现统一筛选面板**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: commit**

### Task 5: 新增看板模式

**Files:**
- Create: `frontend/src/features/shared/pro-table/pro-table-board.tsx`
- Modify: `frontend/src/features/shared/pro-table/pro-table.tsx`
- Test: `frontend/src/features/shared/pro-table/pro-table-board.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖 `table / board` 切换**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 board 模式和工具栏模式切换**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: commit**

---

## Chunk 3: ProForm Core

### Task 6: 创建 ProForm 壳层

**Files:**
- Create: `frontend/src/features/shared/pro-form/pro-form-shell.tsx`
- Create: `frontend/src/features/shared/pro-form/pro-form-section.tsx`
- Create: `frontend/src/features/shared/pro-form/pro-form-actions.tsx`
- Create: `frontend/src/features/shared/pro-form/index.ts`
- Test: `frontend/src/features/shared/pro-form/pro-form-shell.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖标题、分组、提交区布局**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 ProForm 壳层**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: commit**

---

## Chunk 4: 首批接入组织管理

### Task 7: 组织管理列表切换到 ProTable

**Files:**
- Modify: `frontend/src/features/system/org-pages.tsx`
- Modify: `frontend/src/lib/api/system-org.ts`
- Test: `frontend/src/features/system/org-pages.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖公司/部门/岗位/角色列表的统一工具栏**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 用 ProTable 替换组织管理列表骨架**
- [ ] **Step 4: 接入刷新、筛选、导出、导入能力**
- [ ] **Step 5: 运行测试确认通过**
- [ ] **Step 6: commit**

### Task 8: 组织管理表单切换到 ProForm

**Files:**
- Modify: `frontend/src/features/system/org-pages.tsx`
- Test: `frontend/src/features/system/org-pages.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖公司/部门/岗位编辑页表单分组和操作区**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 使用 ProForm 重构组织管理编辑表单**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: commit**

---

## Chunk 5: 首批接入 OA

### Task 9: OA 列表切换到 ProTable

**Files:**
- Modify: `frontend/src/features/oa/pages.tsx`
- Modify: `frontend/src/lib/api/oa.ts`
- Modify: `frontend/src/lib/api/workbench.ts`
- Test: `frontend/src/features/oa/pages.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖 OA 三个业务列表的统一工具栏**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 使用 ProTable 替换 OA 列表页**
- [ ] **Step 4: 接入刷新、筛选、导出**
- [ ] **Step 5: 运行测试确认通过**
- [ ] **Step 6: commit**

### Task 10: OA 看板模式

**Files:**
- Modify: `frontend/src/features/oa/pages.tsx`
- Modify: `frontend/src/features/shared/pro-table/pro-table-board.tsx`
- Test: `frontend/src/features/oa/pages.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖 OA `table / board` 切换**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 OA 状态看板**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: commit**

### Task 11: OA 发起表单切换到 ProForm

**Files:**
- Modify: `frontend/src/features/oa/pages.tsx`
- Test: `frontend/src/features/oa/pages.test.tsx`

- [ ] **Step 1: 写失败测试，覆盖 OA 发起页分组和操作区**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 使用 ProForm 重构 OA 发起表单**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: commit**

---

## Chunk 6: 封板与验证

### Task 12: 验收与回归

**Files:**
- Modify: `frontend/src/features/shared/pro-table/*`
- Modify: `frontend/src/features/shared/pro-form/*`
- Test: `frontend/src/features/system/org-pages.test.tsx`
- Test: `frontend/src/features/oa/pages.test.tsx`
- Test: `frontend/src/features/shared/pro-table/*.test.tsx`
- Test: `frontend/src/features/shared/pro-form/*.test.tsx`

- [ ] **Step 1: 运行首批单元/集成测试**
  - Run: `pnpm -C frontend exec vitest run src/features/system/org-pages.test.tsx src/features/oa/pages.test.tsx src/features/shared/pro-table/*.test.tsx src/features/shared/pro-form/*.test.tsx --reporter=verbose`
- [ ] **Step 2: 运行类型检查**
  - Run: `pnpm -C frontend typecheck`
- [ ] **Step 3: 运行 lint**
  - Run: `pnpm -C frontend lint`
- [ ] **Step 4: 运行构建**
  - Run: `pnpm -C frontend build`
- [ ] **Step 5: commit**

Plan complete and saved to `docs/superpowers/plans/2026-03-25-pro-table-pro-form-plan.md`. Ready to execute?
