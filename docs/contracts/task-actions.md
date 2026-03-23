# 审批动作与状态机协议

> 状态：Current v2026-03-23
> Owner：流程运行时 owner
> 适用范围：真实 `Flowable` 运行时与工作台任务处理

## 1. 目标

定义当前正式运行时的审批任务动作、状态流转、主要请求载荷和对外接口。

本协议以当前真实接口为准，不再沿用旧 demo 运行态路径口径。

## 2. 当前覆盖动作

当前正式支持以下任务与实例动作：

- `CLAIM`
- `APPROVE`
- `REJECT`
- `TRANSFER`
- `RETURN`
- `ADD_SIGN`
- `REMOVE_SIGN`
- `REVOKE`
- `URGE`
- `READ`
- `JUMP`
- `TAKE_BACK`
- `WAKE_UP`
- `DELEGATE`
- `HANDOVER`

说明：

- `APPROVE` 当前通过 `POST /api/v1/process-runtime/tasks/{taskId}/complete` 表达
- `REJECT` 当前通过 `POST /api/v1/process-runtime/tasks/{taskId}/reject` 表达
- 离职转办另有预览与执行接口，属于任务批量迁移，不等同于单任务动作

## 3. 任务状态

当前运行时任务状态约定：

- `PENDING_CLAIM`
- `PENDING`
- `COMPLETED`
- `TRANSFERRED`
- `RETURNED`
- `REJECTED`
- `JUMPED`
- `TAKEN_BACK`
- `REVOKED`
- `CC_PENDING`
- `CC_READ`

## 4. 动作约束

### CLAIM

- 仅 `PENDING_CLAIM` 任务允许认领
- 成功后任务转为 `PENDING`

### APPROVE

- 仅当前办理人可审批
- 使用 `complete` 接口提交
- 原任务转为 `COMPLETED`

### REJECT

- 仅当前办理人可驳回
- 驳回必须记录 `targetStrategy`
- 可按需要携带 `targetTaskId`、`targetNodeId`、`reapproveStrategy`、`comment`

### TRANSFER

- 仅当前办理人可转办
- 必须指定 `targetUserId`

### RETURN

- 仅当前办理人可退回
- 当前请求只要求 `targetStrategy` 与 `comment`

### ADD_SIGN / REMOVE_SIGN

- 仅当前办理人可加签或减签
- 加签必须指定 `targetUserId`
- 减签必须指定待减签任务标识

### REVOKE

- 仅发起人或具备对应管理权限的用户可撤销
- 撤销后实例进入终态

### URGE

- 发起人或管理员允许催办
- 催办不改变任务状态，但必须留下审计记录

### READ

- 仅抄送待阅任务允许已阅

### JUMP

- 仅具备平台特权的用户允许跳转
- 必须指定 `targetNodeId`

### TAKE_BACK

- 仅满足拿回条件的上一办理人允许发起
- 拿回必须记录来源任务与说明

### WAKE_UP

- 仅终态实例允许唤醒
- 必须指定 `sourceTaskId`

### DELEGATE

- 仅当前办理人可委派
- 必须指定 `targetUserId`

### HANDOVER

- 单任务移交由 `POST /tasks/{taskId}/transfer` 或委派动作处理
- 用户级离职转办使用 `/users/{sourceUserId}/handover/*`

## 5. 正式运行态接口

### 查询接口

- `POST /api/v1/process-runtime/tasks/page`
- `POST /api/v1/process-runtime/approval-sheets/page`
- `GET /api/v1/process-runtime/tasks/{taskId}`
- `GET /api/v1/process-runtime/approval-sheets/by-business`
- `GET /api/v1/process-runtime/instances/{instanceId}/task-groups`
- `GET /api/v1/process-runtime/tasks/{taskId}/actions`

### 任务动作接口

- `POST /api/v1/process-runtime/tasks/{taskId}/claim`
- `POST /api/v1/process-runtime/tasks/{taskId}/complete`
- `POST /api/v1/process-runtime/tasks/{taskId}/transfer`
- `POST /api/v1/process-runtime/tasks/{taskId}/return`
- `POST /api/v1/process-runtime/tasks/{taskId}/reject`
- `POST /api/v1/process-runtime/tasks/{taskId}/add-sign`
- `POST /api/v1/process-runtime/tasks/{taskId}/remove-sign`
- `POST /api/v1/process-runtime/tasks/{taskId}/revoke`
- `POST /api/v1/process-runtime/tasks/{taskId}/urge`
- `POST /api/v1/process-runtime/tasks/{taskId}/read`
- `POST /api/v1/process-runtime/tasks/{taskId}/jump`
- `POST /api/v1/process-runtime/tasks/{taskId}/take-back`
- `POST /api/v1/process-runtime/tasks/{taskId}/delegate`

### 实例与用户级动作接口

- `POST /api/v1/process-runtime/instances/{instanceId}/wake-up`
- `POST /api/v1/process-runtime/users/{sourceUserId}/handover/preview`
- `POST /api/v1/process-runtime/users/{sourceUserId}/handover/execute`

## 6. 主要请求载荷

### complete

```json
{
  "action": "APPROVE",
  "operatorUserId": "usr_001",
  "comment": "同意",
  "taskFormData": {
    "approvedDays": 2
  }
}
```

### reject

```json
{
  "targetStrategy": "PREVIOUS_USER_TASK",
  "targetTaskId": "task_001",
  "targetNodeId": "approve_manager",
  "reapproveStrategy": "RESTART_FROM_TARGET",
  "comment": "补充材料后再提交"
}
```

### transfer / delegate / add-sign

```json
{
  "targetUserId": "usr_002",
  "comment": "请协助处理"
}
```

### return

```json
{
  "targetStrategy": "PREVIOUS_USER_TASK",
  "comment": "退回上一处理人"
}
```

### wake-up

```json
{
  "sourceTaskId": "task_001",
  "comment": "重新激活流程"
}
```

## 7. 任务详情与动作可用性

任务详情当前已经返回真实运行时所需字段，包括但不限于：

- 业务标识与业务数据
- 表单版本与生效表单信息
- 流程节点、连线、实例事件、任务轨迹
- 收件、已阅、开始处理、处理完成等关键时间
- 当前动作的目标策略、目标节点和重审策略

任务动作可用性接口返回以下布尔能力位：

- `canClaim`
- `canApprove`
- `canReject`
- `canTransfer`
- `canReturn`
- `canAddSign`
- `canRemoveSign`
- `canRevoke`
- `canUrge`
- `canRead`
- `canRejectRoute`
- `canJump`
- `canTakeBack`
- `canWakeUp`
- `canDelegate`
- `canHandover`

## 8. 废弃口径

以下描述自本版起视为废弃：

- 旧 demo 运行态路径口径
- “当前仍是 demo 运行态”
- 以旧内存运行态服务作为对外正式协议说明

说明：

- 历史任务快照类名统一按 `ProcessTaskSnapshot` 这一任务快照命名描述归档
- `aimcpdemo` 之类独立演示域不属于流程运行态对外协议，不能据此推断任务接口仍走 demo 口径
