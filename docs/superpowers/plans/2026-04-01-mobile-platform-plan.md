# 移动端平台实施计划

## Phase 1：基础骨架

1. 新增 root `package.json`
2. 新增 `pnpm-workspace.yaml`
3. 创建 `apps/mobile/`
4. 建立 Expo Router、TS、Query、Store、Theme 基础设施
5. 创建 `packages/shared-types/`
6. 创建 `packages/shared-workflow/`

## Phase 2：审批主链

1. 接登录与会话态
2. 接工作台待办 / 已办 / 我发起
3. 接审批详情
4. 接审批动作
5. 接常用表单发起

## Phase 3：流程图回顾播放器

1. 从 Web 抽离流程图回顾纯逻辑到 `shared-workflow`
2. 实现 Skia 画布基础能力
3. 实现节点 / 边渲染
4. 实现时间轴联动
5. 实现回顾动画播放控制

## Phase 4：AI Copilot

1. 移动端对话页
2. 文本问答
3. 图片上传
4. OCR 发起预览
5. 语音输入

## 并行执行策略

- Agent A：Phase 1 工程骨架
- Agent B：共享包
- Agent C：Phase 2 审批主链
- Agent D：Phase 3 流程图播放器
- Agent E：Phase 4 AI Copilot

主线程负责：

- 统一 root workspace
- 解决依赖冲突
- 审核代码边界
- 统一回归

## 风险控制

- 不共享 UI 组件，只共享类型和纯逻辑
- 流程图播放器仅做只读能力，不实现编辑
- 审批主链优先，管理后台不进入移动端一期范围
