# 微信小程序平台 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `west-flow-ai` 新增独立微信小程序端，覆盖登录、工作台、审批详情、审批动作、AI Copilot，以及通过 `web-view` 打开的流程图回顾能力。

**Architecture:** 新增 `apps/weapp` 作为 Taro + React 小程序应用，继续复用现有后端 API、共享类型包与纯数据逻辑包。流程图回顾不在小程序端重写图引擎，而是新增 H5 只读播放器页面供 `web-view` 承载，并通过短期访问票据解决鉴权。

**Tech Stack:** Taro, React, TypeScript, TanStack Query, Zustand, React Hook Form, Zod, 微信小程序 `web-view`, existing Java backend APIs

---

## Chunk 1: 工程骨架与共享接线

### Task 1: 新增小程序工程

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/package.json`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/project.config.json`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/app.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/app.config.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/pnpm-workspace.yaml`

- [ ] **Step 1: 建立 Taro 小程序工程最小目录**

创建 `apps/weapp` 基础目录、包配置、TypeScript 配置和 Taro app 入口。

- [ ] **Step 2: 把小程序工程接入 root workspace**

确认 workspace 能解析 `apps/weapp`，并和现有 `frontend`、`apps/mobile` 并存。

- [ ] **Step 3: 运行基础类型检查**

Run: `pnpm --filter @westflow/weapp typecheck`
Expected: PASS

- [ ] **Step 4: 提交工程骨架**

```bash
git add apps/weapp pnpm-workspace.yaml
git commit -m "feat: 初始化微信小程序工程骨架"
```

### Task 2: 接入共享类型与基础 API 客户端

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/lib/api/client.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/lib/api/auth.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/stores/auth-store.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/package.json`
- Reference: `/Users/west/dev/code/west/west-flow-ai/packages/shared-types`

- [ ] **Step 1: 定义小程序端 API base URL 与拦截器**

统一附加 Bearer Token，并兼容小程序上传请求。

- [ ] **Step 2: 接入登录、当前用户接口**

实现 `login()`、`getCurrentUser()` 并在状态仓库中管理会话态。

- [ ] **Step 3: 运行类型检查**

Run: `pnpm --filter @westflow/weapp typecheck`
Expected: PASS

- [ ] **Step 4: 提交基础 API 与认证层**

```bash
git add apps/weapp
git commit -m "feat: 接入小程序认证与基础 API 客户端"
```

## Chunk 2: 工作台与审批主链

### Task 3: 工作台首页

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/pages/workbench/index.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/features/workbench/api.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/features/workbench/components/TaskCard.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/app.config.ts`

- [ ] **Step 1: 接入待办 / 已办 / 我发起列表查询**

工作台页支持三段切换和摘要统计。

- [ ] **Step 2: 完成列表卡片与导航**

点击记录进入审批详情页。

- [ ] **Step 3: 运行类型检查**

Run: `pnpm --filter @westflow/weapp typecheck`
Expected: PASS

- [ ] **Step 4: 提交工作台首页**

```bash
git add apps/weapp
git commit -m "feat: 新增小程序工作台首页"
```

### Task 4: 审批详情与审批动作

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/pages/approval/detail.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/features/approval/api.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/features/approval/components/ActionBar.tsx`

- [ ] **Step 1: 接入审批详情查询**

展示表单字段、时间轴摘要、附件与可执行动作。

- [ ] **Step 2: 接入认领、同意、驳回等基础动作**

动作成功后刷新详情和工作台列表。

- [ ] **Step 3: 运行类型检查**

Run: `pnpm --filter @westflow/weapp typecheck`
Expected: PASS

- [ ] **Step 4: 提交审批详情链路**

```bash
git add apps/weapp
git commit -m "feat: 新增小程序审批详情与动作链路"
```

## Chunk 3: AI Copilot

### Task 5: 小程序 AI 对话页

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/pages/ai/index.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/features/ai/api.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/features/ai/components/MessageBubble.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/features/ai/components/InputBar.tsx`

- [ ] **Step 1: 接入会话列表与消息发送**

支持新建会话、发送文本消息、读取历史消息。

- [ ] **Step 2: 接入图片上传**

使用小程序上传能力把图片送到现有 `/ai/copilot/assets`。

- [ ] **Step 3: 接入语音录音 / 上传**

把录音文件上传到现有转写接口，再把转写文本送入对话。

- [ ] **Step 4: 运行类型检查**

Run: `pnpm --filter @westflow/weapp typecheck`
Expected: PASS

- [ ] **Step 5: 提交 AI Copilot 页面**

```bash
git add apps/weapp
git commit -m "feat: 新增小程序 AI Copilot"
```

## Chunk 4: 流程图回顾 web-view 集成

### Task 6: 小程序 web-view 容器页

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/pages/process-player/index.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/src/features/process-player/url.ts`

- [ ] **Step 1: 新增小程序流程图回顾容器页**

提供 `web-view` 页面并接收审批详情跳转参数。

- [ ] **Step 2: 定义 H5 播放器 URL 拼装逻辑**

支持业务单标识、任务标识、默认播放位置和 ticket 参数。

- [ ] **Step 3: 运行类型检查**

Run: `pnpm --filter @westflow/weapp typecheck`
Expected: PASS

- [ ] **Step 4: 提交流程图回顾容器页**

```bash
git add apps/weapp
git commit -m "feat: 新增小程序流程图回顾容器页"
```

### Task 7: H5 播放器访问票据链路

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/workbench/api/ProcessPlayerTicketController.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/workbench/service/ProcessPlayerTicketService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/workbench/api/ProcessPlayerTicketControllerTest.java`

- [ ] **Step 1: 编写失败测试**

覆盖：
- 登录用户可换取短期回顾 ticket
- ticket 过期或越权时拒绝访问

- [ ] **Step 2: 实现 ticket 服务与接口**

限制 ticket 生命周期和可访问的回顾资源范围。

- [ ] **Step 3: 运行后端测试**

Run: `mvn -q -f backend/pom.xml -Dtest=ProcessPlayerTicketControllerTest test`
Expected: PASS

- [ ] **Step 4: 提交 ticket 鉴权链**

```bash
git add backend
git commit -m "feat: 新增流程图回顾短期访问票据"
```

## Chunk 5: 验证与文档

### Task 8: 全链路验证

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/apps/weapp/README.md`
- Modify: `/Users/west/dev/code/west/west-flow-ai/README.md`

- [ ] **Step 1: 跑小程序类型检查**

Run: `pnpm --filter @westflow/weapp typecheck`
Expected: PASS

- [ ] **Step 2: 跑共享包类型检查**

Run: `pnpm -r --filter @westflow/shared-types --filter @westflow/shared-workflow typecheck`
Expected: PASS

- [ ] **Step 3: 跑后端相关测试**

Run: `mvn -q -f backend/pom.xml -DskipTests compile`
Expected: PASS

- [ ] **Step 4: 更新使用文档**

补充微信开发者工具调试说明、后端 API 地址和 `web-view` 配置要求。

- [ ] **Step 5: 最终提交**

```bash
git add apps/weapp backend README.md
git commit -m "feat: 新增微信小程序端一期能力"
```

Plan complete and saved to `docs/superpowers/plans/2026-04-02-weapp-plan.md`. Ready to execute?
