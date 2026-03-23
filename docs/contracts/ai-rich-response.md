# AI 富响应块协议

> 状态：Current v2026-03-23
> Owner：AI Gateway owner + Copilot Frontend owner
> 适用范围：AI Copilot 前后端消息块协议

## 1. 目标

定义 AI Copilot 在前后端之间传递富响应消息块的当前正式协议。

本协议用于统一：

- 普通文本回复
- 写操作确认卡
- 表单预览
- 统计卡
- 工具执行结果
- 失败与重试建议
- 轨迹与解释链路

## 2. 设计原则

- 一条 AI 消息可以包含多个块
- 前端只消费统一块协议，不再根据业务对象自行猜测结构
- 当前正式字段为 `type`，不再使用旧的 `blockType`
- 业务补充信息统一通过 `fields`、`metrics`、`result`、`failure`、`trace` 等稳定字段承载

## 3. 当前块类型

当前正式支持以下 8 种块类型：

- `text`
- `confirm`
- `form-preview`
- `stats`
- `result`
- `failure`
- `retry`
- `trace`

## 4. 通用结构

后端返回时，每个消息块遵循以下结构：

```ts
type AiMessageBlockResponse = {
  type: 'text' | 'confirm' | 'form-preview' | 'stats' | 'result' | 'failure' | 'retry' | 'trace'
  title?: string
  body?: string
  confirmationId?: string
  summary?: string
  detail?: string
  confirmLabel?: string
  cancelLabel?: string
  status?: string
  resolvedAt?: string
  resolvedBy?: string
  resolutionNote?: string
  sourceType?: string
  sourceKey?: string
  sourceName?: string
  toolType?: string
  result?: Record<string, unknown>
  failure?: {
    code?: string
    message?: string
    detail?: string
  }
  trace?: Array<{
    stage?: string
    label?: string
    detail?: string
    status?: string
  }>
  fields?: Array<{
    label: string
    value?: string
    hint?: string
  }>
  metrics?: Array<{
    label: string
    value?: string
    hint?: string
    tone?: string
  }>
}
```

## 5. 各块语义

### text

- 用于普通解释性回复
- `body` 为主要文本内容

### confirm

- 用于待确认的写操作
- 应携带 `confirmationId`
- 建议同时提供 `summary`、`detail`、`fields`
- 可通过 `confirmLabel` 与 `cancelLabel` 指定按钮文案

### form-preview

- 用于展示即将发起或即将提交的数据预览
- 建议通过 `fields` 呈现主要字段

### stats

- 用于统计问答结果
- 建议通过 `metrics` 呈现指标，通过 `result` 附带结构化数据

### result

- 用于成功完成后的结果卡
- 可携带 `sourceType`、`sourceKey`、`sourceName` 标记业务来源
- `result` 承载结构化结果
- `fields` 与 `metrics` 用于前端直接展示业务摘要

### failure

- 用于执行失败后的错误卡
- 失败信息放在 `failure`
- 可附加 `summary`、`detail` 和补救建议

### retry

- 用于“可重试”的失败或待处理语义
- 允许同时携带 `failure`、`fields` 和下一步建议

### trace

- 用于展示工具命中、审批轨迹、推理路径或处理过程
- `trace` 为步骤数组

## 6. 当前业务化要求

### task.handle

`task.handle` 的 `confirm / result / failure / retry` 块应尽量带完整业务语义，包括：

- 业务类型
- 业务标识
- 业务单号或任务标识
- 业务标题
- 来源页面或来源入口
- 处理意见
- 下一步建议

### plm.bill.query / plm.change.summary

PLM 读工具结果不应只输出通用 `result`。当前要求结果块优先提供：

- 命中的业务类型
- 首条命中单据
- 业务摘要字段
- 关键指标或统计

推荐通过 `result + fields + metrics` 组合表达，必要时再配合 `summary`。

## 7. 状态建议

`status` 当前为展示语义字段，推荐使用：

- `pending`
- `success`
- `warning`
- `error`
- `info`

约束：

- 待确认块建议使用 `pending`
- 成功结果块建议使用 `success`
- 失败块建议使用 `error`

## 8. 前端消费规则

- 前端按 `type` 分发渲染，不再读取旧 `blockType`
- 未识别块类型必须降级展示，而不是静默丢失
- 前端不要求再按业务名硬编码拆分 AI 消息，只消费稳定字段

## 9. 废弃口径

以下旧协议字段与块名已废弃：

- `blockType`
- `payload`
- `actions`
- `confirmation` 块名

当前正式块名为 `confirm`，失败与可重试状态分别使用 `failure` 和 `retry`
