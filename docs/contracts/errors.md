# 统一错误码协议

> 状态：Frozen v1
> Owner：后端基础设施 owner
> 生效里程碑：M0

## 目标

统一成功与失败响应结构，覆盖字段校验、权限校验、业务状态错误、流程动作错误和 AI 工具调用错误。

## 成功响应结构

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "req_20260321_0001"
}
```

## 失败响应结构

```json
{
  "code": "VALIDATION.FIELD_INVALID",
  "message": "请求参数校验失败",
  "requestId": "req_20260321_0002",
  "path": "/api/v1/oa/leaves",
  "timestamp": "2026-03-21T12:00:00+08:00",
  "details": {},
  "fieldErrors": [
    {
      "field": "title",
      "code": "REQUIRED",
      "message": "标题不能为空"
    }
  ]
}
```

## 顶层字段说明

| 字段 | 说明 |
| --- | --- |
| `code` | 稳定错误码，前端以此判断交互分支 |
| `message` | 对用户或日志可读的简短错误消息 |
| `requestId` | 请求追踪 ID |
| `path` | 请求路径 |
| `timestamp` | 服务端时间 |
| `details` | 扩展信息 |
| `fieldErrors` | 字段级错误，仅字段校验类错误使用 |

## 错误码命名规范

- 格式：`DOMAIN.SCENARIO`
- 全大写
- 不使用数字尾缀区分语义

示例：

- `AUTH.UNAUTHORIZED`
- `AUTH.FORBIDDEN`
- `VALIDATION.FIELD_INVALID`
- `VALIDATION.REQUEST_INVALID`
- `BIZ.STATE_CONFLICT`
- `PROCESS.ACTION_NOT_ALLOWED`
- `PROCESS.INSTANCE_NOT_FOUND`
- `AI.TOOL_FORBIDDEN`
- `AI.TOOL_CONFIRM_REQUIRED`
- `SYS.INTERNAL_ERROR`

## HTTP 状态码映射

| HTTP 状态码 | 业务含义 | 典型错误码 |
| --- | --- | --- |
| `400` | 请求格式或参数错误 | `VALIDATION.REQUEST_INVALID` |
| `401` | 未登录或登录失效 | `AUTH.UNAUTHORIZED` |
| `403` | 无权限 | `AUTH.FORBIDDEN` |
| `404` | 资源不存在 | `BIZ.RESOURCE_NOT_FOUND` |
| `409` | 状态冲突 | `BIZ.STATE_CONFLICT` |
| `422` | 合法请求但当前动作不允许 | `PROCESS.ACTION_NOT_ALLOWED` |
| `500` | 系统错误 | `SYS.INTERNAL_ERROR` |

## 字段校验规则

- 字段错误必须放入 `fieldErrors`
- `fieldErrors[].field` 使用前端表单字段名
- 同一字段可返回多个错误，但建议优先返回最关键错误
- 前端优先展示字段级错误，其次展示页面级错误

## 权限与流程错误规则

- 权限错误禁止只返回“失败”，必须明确是未登录、无菜单权限、无数据权限或无 AI 权限
- 流程动作错误必须指出当前动作、当前状态、禁止原因

`details` 示例：

```json
{
  "action": "WITHDRAW",
  "currentStatus": "APPROVED",
  "reason": "仅审批中单据允许撤销"
}
```

## AI 错误规则

- AI 工具需要二次确认但未确认：`AI.TOOL_CONFIRM_REQUIRED`
- AI 无权限调用工具：`AI.TOOL_FORBIDDEN`
- AI 工具执行失败：`AI.TOOL_EXECUTION_FAILED`
- AI 上下文缺失：`AI.CONTEXT_INCOMPLETE`

## 前端处理约定

- `401`：跳转登录页
- `403`：展示无权限页或按钮级无权限提示
- `fieldErrors`：映射到表单字段
- `PROCESS.ACTION_NOT_ALLOWED`：在审批页展示业务化提示，不直接弹系统异常
