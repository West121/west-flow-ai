# 移动端平台架构设计

## 目标

为现有 `west-flow-ai` 平台新增独立移动端，覆盖审批主链、AI Copilot 和流程图回顾。移动端不复用 Web UI 渲染层，但复用现有后端 API、业务数据模型和部分纯逻辑。

本次设计的核心约束：

- 保留现有 `frontend/` Web 应用，不迁移为跨端单 UI。
- 新增 `apps/mobile/`，主做 iOS / Android。
- 流程图回顾不使用 WebView，直接实现原生只读播放器。
- 流程图回顾播放器必须支持：
  - 缩放 / 平移
  - 节点与连线状态高亮
  - 历史回顾动画
  - 时间轴联动

## 技术选型

### 移动端框架

- `React Native`
- `Expo`
- `Expo Router`
- `TypeScript`

选择原因：

- 现有前端已经是 React / TypeScript / TanStack 风格，团队迁移成本最低。
- Expo 对路由、开发体验、原生能力接入更稳，适合中后台移动端。

### UI 方案

- `NativeWind`
- 自定义业务组件层

不引入重型整套 UI 框架，避免和现有平台视觉语言脱节。移动端沿用现有 token 语义，但不直接复用 Web DOM 组件。

### 数据与状态

- `TanStack Query`
- `Zustand`
- `React Hook Form`
- `Zod`

### 流程图回顾播放器

- `React Native Skia`
- `react-native-reanimated`
- `react-native-gesture-handler`

原因：

- 需求是只读流程图播放器，不是编辑器。
- 重点在大画布、路径高亮、历史动画、时间轴联动。
- `Skia` 更适合做播放器型渲染，而不是静态 SVG 图。

## 仓库结构

新增 monorepo 边界：

- `apps/mobile/`
- `packages/shared-types/`
- `packages/shared-workflow/`

保留：

- `frontend/`
- `backend/`
- `services/`

### shared-types

只放跨端稳定类型：

- workbench / approval DTO
- AI Copilot DTO
- 流程图节点、边、轨迹、实例事件类型

### shared-workflow

只放纯逻辑，不放任何 Web 或 RN 渲染代码：

- playback event 构造
- timeline entry 构造
- 节点状态映射
- 连线状态映射
- 回顾播放器状态推进

## 共享边界

### 可以共享

- 后端 REST API
- 类型定义
- 纯数据逻辑
- 流程图回顾事件构造与状态推导

### 不共享

- Web UI 组件
- 页面布局
- Web 路由
- React Flow 渲染层
- 移动端流程图渲染层

## 功能分期

### Phase 1：移动端基础骨架

- 建立 root workspace
- 创建 `apps/mobile`
- Expo Router 基础导航
- 认证、API client、Query Provider、基础主题 token
- `packages/shared-types`
- `packages/shared-workflow` 初始骨架

### Phase 2：审批主链

- 工作台待办 / 已办 / 我发起
- 审批详情
- 审批动作
- 常用表单发起

### Phase 3：流程图回顾播放器

- 抽离 Web 流程图回顾的纯逻辑
- Skia 只读流程图播放器
- 时间轴联动
- 播放 / 暂停 / 重播

### Phase 4：AI Copilot

- 对话页
- 文本问答
- 图片上传
- OCR 发起
- 语音输入

## 多代理开发边界

### Agent A：基础工程

负责：

- root `package.json`
- `pnpm-workspace.yaml`
- `apps/mobile/` 基础工程

### Agent B：共享包

负责：

- `packages/shared-types`
- `packages/shared-workflow`

### Agent C：审批主链

负责：

- 移动端工作台
- 审批详情
- 审批动作

### Agent D：流程图播放器

负责：

- Skia 只读流程图回顾播放器
- 时间轴联动

### Agent E：AI Copilot

负责：

- 移动端 AI 页
- 图片 / 语音入口
- OCR 发起预览

主线程负责：

- 统一收口目录结构
- 解决跨包依赖
- 统一验证与集成

## 验收标准

- `pnpm` workspace 可正常安装和类型检查
- `apps/mobile` 可启动 Expo 开发环境
- 审批主链在移动端可查看、可处理
- 流程图回顾播放器支持播放历史动画
- AI Copilot 基础链路可用
