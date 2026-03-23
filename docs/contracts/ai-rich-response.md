# AI 富响应块协议

> 状态：Frozen v1
> Owner：AI Gateway owner + Copilot Frontend owner
> 生效里程碑：Phase 6C

## 1. 目标

定义 AI Copilot 在前后端之间传递富响应消息块的统一协议。

本协议用于解决以下问题：

- AI 回复不再只有纯文本
- 业务工具执行结果可以被统一渲染
- 写操作确认卡可以复用
- 统计结果、流程轨迹、表单预览都走同一消息体系

## 2. 设计原则

- 同一条 AI 消息可以包含多个块
- 前端只消费统一块协议，不再自己根据业务对象二次猜测
- 每个块都必须有稳定 `blockType`
- 每个块都可以携带 `title`、`summary`、`status`
- 写操作结果和失败结果必须可单独展示

## 3. 块类型

本阶段冻结以下 6 种块类型：

- `text`
- `confirmation`
- `result`
- `stats`
- `form-preview`
- `trace`

## 4. 通用结构

后端返回时，每个消息块必须遵循以下结构：

```ts
type AiRichResponseBlock = {
  blockType: 'text' | 'confirmation' | 'result' | 'stats' | 'form-preview' | 'trace'
  title?: string
  summary?: string
  status?: 'info' | 'success' | 'warning' | 'error' | 'pending'
  payload?: Record<string, unknown>
  actions?: AiRichResponseAction[]
}
```

动作结构：

```ts
type AiRichResponseAction = {
  actionKey: string
  label: string
  actionMode: 'READ' | 'WRITE'
  variant?: 'default' | 'outline' | 'secondary' | 'destructive'
  confirmationRequired?: boolean
  payload?: Record<string, unknown>
}
```

## 5. 各块语义

### 5.1 `text`

用于普通解释性回复。

`payload` 建议字段：

- `content`
- `markdown`

### 5.2 `confirmation`

用于写操作确认卡。

`payload` 必须至少包含：

- `confirmationId`
- `toolCallId`
- `toolKey`
- `targetLabel`
- `arguments`
- `riskLevel`

要求：

- 只能用于待确认的写操作
- 必须与确认记录一一对应

### 5.3 `result`

用于执行完成后的结果卡。

`payload` 建议字段：

- `toolCallId`
- `toolKey`
- `executionStatus`
- `result`
- `resultLabel`
- `businessLink`

要求：

- 成功和失败都走 `result`
- 失败时 `status=error`

### 5.4 `stats`

用于统计问答结果。

`payload` 建议字段：

- `metricCards`
- `chartType`
- `chartData`
- `timeRange`

### 5.5 `form-preview`

用于展示即将发起或即将提交的数据预览。

`payload` 建议字段：

- `formKey`
- `businessType`
- `fields`
- `readonly`

### 5.6 `trace`

用于流程轨迹、审批状态、工具命中链路等可追踪内容。

`payload` 建议字段：

- `traceType`
- `nodes`
- `events`
- `currentStep`

## 6. 状态规范

推荐状态：

- `info`
- `success`
- `warning`
- `error`
- `pending`

约束：

- 待确认卡必须使用 `pending`
- 失败结果卡必须使用 `error`
- 成功执行结果建议使用 `success`

## 7. 前端消费规则

- 前端渲染层只根据 `blockType` 和 `payload` 渲染
- 不允许前端再按业务名硬编码拆分 AI 消息
- 未识别块类型必须降级为 JSON 预览，而不是静默丢失

## 8. 非目标

本协议不定义：

- 模型原始 token 流结构
- 底层 ChatClient provider 协议
- MCP 传输协议
- Agent 内部思维链输出
