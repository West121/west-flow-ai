# Phase 6C：AI + PLM 并行推进设计

> 状态：Approved v1
> 日期：2026-03-23
> 适用范围：`main` 分支 `AI + PLM` 后续并行开发批次

> Archive note (2026-03-23): 本设计记录当日并行分工冻结结果；其中 `aimcpdemo` 仅指演示 / 集成测试域，不代表当前正式 AI 管理后台或流程运行态契约。

## 1. 目标

在现有基线下，同时推进以下 4 条线：

- AI 业务闭环
- PLM 业务深化
- AI 运维与可观测
- AI 前端体验与生产化收尾

本阶段的核心要求不是“同时做很多事”，而是“并行但不互相阻塞”。

## 2. 当前基线

当前仓库已经具备：

- 真实数据库登录
- 真实 Flowable 运行态
- OA + PLM 完整流程定义 SQL 种子与启动同步
- AI Copilot 统一入口
- `Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.0`
- AI 管理后台
- 真实外部 MCP 演示链路

因此下一阶段不再需要重做基础设施，而是以现有平台层为底座继续扩展。

## 3. 并行原则

### 3.1 允许真并行的 3 条线

以下 3 条线写集和职责边界清晰，可以同时推进：

- Lane A：AI 业务闭环
- Lane B：PLM 业务深化
- Lane C：AI 运维与可观测

### 3.2 需要分段推进的 1 条线

Lane D（前端体验与生产化）不能一开始全量并行，因为它依赖 Lane A 和 Lane C 的响应协议稳定。

因此 Lane D 分为两段：

- D1：先做独立于业务协议的外壳与前端基础优化
- D2：等 A / C 稳定后再接实际卡片、命中轨迹和执行反馈

## 4. 4 条并行线定义

### 4.1 Lane A：AI 业务闭环

目标：

- AI 发起流程
- AI 处理待办
- AI 统计问答

必须具备：

- 真实工具调用
- 写操作确认卡
- 执行后回写结果卡
- 权限校验
- 流程中心与 Copilot 的上下文联动

不做：

- PLM 业务页大改
- AI 管理后台
- MCP 诊断页

写集范围：

- `backend/src/main/java/com/westflow/ai/**`
- `backend/src/test/java/com/westflow/ai/**`
- `frontend/src/lib/api/ai-copilot.ts`
- `frontend/src/features/ai/**`

### 4.2 Lane B：PLM 业务深化

目标：

- `ECR`
- `ECO`
- `物料主数据变更`

都具备：

- 列表页
- 详情页
- 业务状态展示
- 与审批单双向联查
- 可被 AI 工具读取

不做：

- 改 AI 主面板
- 改 Copilot 消息协议

写集范围：

- `backend/src/main/java/com/westflow/plm/**`
- `backend/src/test/java/com/westflow/plm/**`
- `frontend/src/features/plm/**`
- `frontend/src/lib/api/plm.ts`

### 4.3 Lane C：AI 运维与可观测

目标：

- Agent / Tool / MCP / Skill 运营后台补强
- MCP 连通性检测
- 调用链日志
- 失败诊断
- 确认记录与执行记录联动增强

必须具备：

- 查看当前启用对象
- 查看调用记录
- 查看确认记录
- 查看失败原因
- 检测外部 MCP 是否可连通

不做：

- OA / PLM 业务页
- AI 发起流程逻辑

写集范围：

- `backend/src/main/java/com/westflow/aiadmin/**`
- `backend/src/main/java/com/westflow/aimcpdemo/**`
- `backend/src/test/java/com/westflow/aiadmin/**`
- `backend/src/test/java/com/westflow/aimcpdemo/**`
- `frontend/src/features/ai-admin/**`
- `frontend/src/lib/api/ai-admin.ts`

### 4.4 Lane D：前端体验与生产化收尾

#### D1：先做壳

目标：

- Copilot 面板布局
- 会话列表信息架构
- 上下文摘要展示
- 富消息容器与卡片槽位
- 前端 chunk 优化
- 历史 warning 清理

#### D2：后接真实数据

目标：

- 展示命中的 agent / tool / skill / mcp
- 展示执行过程与失败信息
- 展示确认卡与结果卡
- 衔接 Lane A / C 产出的真实响应块

写集范围：

- `frontend/src/features/ai/**`
- `frontend/src/components/layout/ai-copilot-launcher.tsx`
- `frontend/src/routes/_authenticated/ai.tsx`
- 必要时少量 `frontend/src/lib/api/ai-copilot.ts`

## 5. 共享契约

并行前必须先冻结 3 个共享契约。

### 5.1 AI 富响应块协议

统一响应块类型：

- `text`
- `confirmation`
- `result`
- `stats`
- `form-preview`
- `trace`

要求：

- 所有 Lane A 和 Lane C 产出的 UI 数据都必须走同一协议
- Lane D 只能消费该协议，不能再自行拼装业务对象

### 5.2 AI 工具执行协议

统一字段：

- `toolKey`
- `toolSource`
- `actionMode`
- `requiresConfirmation`
- `summary`
- `payload`
- `result`
- `failureReason`
- `contextTags`

要求：

- 读操作可直执
- 写操作必须先落确认记录
- 确认后再执行真实 handler

### 5.3 PLM 助手工具协议

统一能力：

- 查询业务列表
- 查询业务详情
- 查询审批状态
- 解释业务单当前流程状态

要求：

- 只提供已建模的 ECR / ECO / 物料主数据变更能力
- 不允许直接让 AI 绕过业务页修改业务主数据

## 6. 推荐执行顺序

### 6.1 文档冻结

先写：

- 本设计文档
- 并行实施计划

### 6.2 先冻结共享契约

由主线程负责：

- AI 富响应块
- AI 工具协议
- PLM 助手协议

### 6.3 再并行启动

- Agent 1：Lane A
- Agent 2：Lane B
- Agent 3：Lane C
- 主线程：Lane D1 + 契约 + 最终联调

### 6.4 最后收口 D2

待 Lane A / C 接口稳定后，由主线程接回：

- 真实命中展示
- 确认卡
- 结果卡
- 执行轨迹

## 7. 验收标准

### Lane A 验收

- AI 能真实发起流程
- AI 能真实处理待办
- AI 能返回真实统计卡
- 写操作全走确认

### Lane B 验收

- 3 个 PLM 业务都有列表和详情
- 能从业务详情跳审批单
- 能从审批单回业务详情

### Lane C 验收

- 能查看 Agent / Tool / MCP / Skill
- 能查看对话、工具调用、确认记录
- 能检测外部 MCP 连通性

### Lane D 验收

- Copilot 会话面板能展示上下文
- 能展示命中对象
- 能展示确认和执行结果
- `typecheck / lint / build` 通过

## 8. 当前推荐下一步

下一步不直接写功能代码，而是先生成并行实施计划：

- `docs/superpowers/plans/2026-03-23-ai-plm-parallel-lanes-plan.md`

然后按该计划正式拆分多代理任务。
