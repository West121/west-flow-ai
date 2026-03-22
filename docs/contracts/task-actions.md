# 审批动作与状态机协议

> 状态：Frozen v1
> Owner：流程运行时 owner
> 冻结时点：M2

## 目标

定义当前运行时 demo 的审批任务动作、状态流转、表单载荷和审计留痕规范。

## 当前覆盖范围

- `CLAIM`
- `TRANSFER`
- `RETURN`
- `APPROVE`
- `REJECT`

本批次不覆盖：

- 驳回到发起人
- 驳回到任意节点
- 撤销
- 委派
- 加签 / 减签
- Flowable 引擎级回滚

## 任务状态

- `PENDING_CLAIM`
- `PENDING`
- `COMPLETED`
- `TRANSFERRED`
- `RETURNED`

## 动作约束

### CLAIM

- 仅 `PENDING_CLAIM` 任务允许认领
- 当前用户必须在 `candidateUserIds` 范围内
- 成功后任务转为 `PENDING`

### TRANSFER

- 仅当前 `assigneeUserId` 可转办
- 原任务转为 `TRANSFERRED`
- 新任务创建为 `PENDING`

### RETURN

- 仅当前 `assigneeUserId` 可退回
- 本期仅支持 `PREVIOUS_USER_TASK`
- 原任务转为 `RETURNED`
- 上一步人工节点生成新的待处理任务

### APPROVE / REJECT

- 仅当前 `assigneeUserId` 可处理
- 原任务转为 `COMPLETED`
- 继续沿流程图推进后续节点

## 运行态接口

- `GET /api/v1/process-runtime/demo/tasks/{taskId}/actions`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/claim`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/transfer`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/return`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/complete`

## 运行态表单载荷

### 发起流程

请求体使用：

```json
{
  "processKey": "oa_leave",
  "businessKey": "biz_001",
  "formData": {
    "days": 3,
    "reason": "请假"
  }
}
```

### 任务处理

审批处理请求体在 M2 新增：

```json
{
  "action": "APPROVE",
  "comment": "同意",
  "taskFormData": {
    "approvedDays": 2
  }
}
```

任务详情返回补充：

- `nodeFormKey`
- `nodeFormVersion`
- `fieldBindings`
- `taskFormData`

## 审计字段

每次动作至少记录：

- `taskId`
- `instanceId`
- `action`
- `operatorUserId`
- `comment`
- `createdAt`
