# AI 工具执行与确认协议

> 状态：Frozen v1
> Owner：AI Gateway owner + AI Admin owner
> 生效里程碑：Phase 6C

## 1. 目标

统一 AI 工具调用、确认、执行、审计和失败处理协议。

本协议覆盖：

- 平台内置工具
- Agent 专属工具
- MCP 工具
- Skill 工具

## 2. 核心原则

- 读操作：默认可直接执行
- 写操作：必须先确认
- 所有工具调用都必须落 `toolCall`
- 所有写操作确认都必须落 `confirmation`
- 所有执行结果都必须可审计

## 3. 工具调用结构

```ts
type AiToolExecutionEnvelope = {
  toolKey: string
  toolSource: 'PLATFORM' | 'AGENT' | 'MCP' | 'SKILL'
  actionMode: 'READ' | 'WRITE'
  requiresConfirmation: boolean
  summary: string
  payload: Record<string, unknown>
  result?: Record<string, unknown> | null
  failureReason?: string | null
  contextTags: string[]
}
```

## 4. 字段说明

### `toolKey`

平台内唯一工具键，例如：

- `workflow.start`
- `task.query`
- `task.approve`
- `plm.ecr.list`

### `toolSource`

- `PLATFORM`
- `AGENT`
- `MCP`
- `SKILL`

### `actionMode`

- `READ`
- `WRITE`

### `requiresConfirmation`

规则：

- `READ` 默认 `false`
- `WRITE` 默认 `true`

除非后续单独冻结更细粒度策略，本阶段不得绕过该规则。

### `summary`

给人看的执行摘要，用于消息流和审计列表。

### `payload`

执行前参数。

### `result`

执行完成后的结果。

### `failureReason`

执行失败时的统一失败说明。

### `contextTags`

来源上下文，例如：

- `route:/workbench/todos/list`
- `domain:workflow`
- `business:PLM_ECR`

## 5. 确认流

### 5.1 写操作待确认

当 `actionMode=WRITE` 时：

1. 先创建 `toolCall`
2. 状态置为 `PENDING`
3. 创建 `confirmation`
4. 向前端返回 `confirmation` 块

### 5.2 用户确认

确认通过时：

1. 更新 `confirmation=APPROVED`
2. 真正执行目标 handler
3. 回写 `toolCall` 最终状态
4. 返回 `result` 块

### 5.3 用户拒绝

拒绝时：

1. 更新 `confirmation=REJECTED`
2. 不执行真实写 handler
3. 回写审计
4. 返回拒绝结果块

## 6. 状态机

### `toolCall.status`

- `PENDING`
- `CONFIRMED`
- `EXECUTED`
- `FAILED`
- `REJECTED`

### `confirmation.status`

- `PENDING`
- `APPROVED`
- `REJECTED`

## 7. 失败语义

失败统一分为三类：

- 参数失败
- 权限失败
- 执行失败

要求：

- 都必须写 `failureReason`
- 都必须进入审计
- 前端必须能展示失败结果卡

## 8. 与富响应块的关系

- 待读确认写操作必须生成 `confirmation` 块
- 成功执行必须生成 `result` 块
- 失败执行必须生成 `result` 块且 `status=error`

## 9. 非目标

本协议不定义：

- 模型如何选择工具
- MCP 服务本身的内部协议
- Skill 文本内容格式
