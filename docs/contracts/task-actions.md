# 审批动作与状态机协议

> 状态：Frozen v2
> Owner：流程运行时 owner
> 冻结时点：M2 Advanced Runtime

## 目标

定义当前运行时 demo 的审批任务动作、状态流转、表单载荷、审批单详情、流程跟踪与审计留痕规范。

## 当前冻结范围

- `CLAIM`
- `TRANSFER`
- `RETURN`
- `APPROVE`
- `REJECT`
- `READ`
- `ADD_SIGN`
- `REMOVE_SIGN`
- `REVOKE`
- `URGE`
- `CC_DELIVER`

本批次不覆盖：

- 驳回到发起人
- 驳回到任意节点
- 委派
- 代理
- 离职转办
- 拿回
- 唤醒
- 跳转
- 追加
- 动态构建
- 定时 / 触发 / 自动提醒 / 超时审批
- 包容分支
- Flowable 引擎级回滚

## 任务状态

- `PENDING_CLAIM`
- `PENDING`
- `COMPLETED`
- `TRANSFERRED`
- `RETURNED`
- `REVOKED`
- `CC_PENDING`
- `CC_READ`

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

### READ

- 仅 `CC_PENDING` 抄送任务允许已阅
- 当前用户必须在抄送目标范围内
- 已阅后任务转为 `CC_READ`

### ADD_SIGN

- 仅当前 `assigneeUserId` 可发起加签
- 加签会为目标用户生成新的人工任务
- 被加签任务完成后，原任务才允许继续处理
- 必须记录来源任务、加签任务、加签说明

### REMOVE_SIGN

- 仅当前 `assigneeUserId` 可发起减签
- 仅允许移除尚未处理的加签任务
- 被减签的任务必须保留轨迹，不做物理删除

### REVOKE

- 仅发起人允许撤销
- 仅实例存在活动人工任务时允许撤销
- 撤销后实例进入终止态，所有活动任务结束

### URGE

- 发起人或管理员允许催办
- 催办不会改变任务状态
- 必须记录催办动作、催办目标、催办说明、催办时间

### CC_DELIVER

- 进入 `cc` 节点时必须生成真实抄送任务
- 抄送任务默认状态为 `CC_PENDING`
- 抄送任务进入 `抄送我` 列表，且可打开审批单详情页

## 运行态接口

- `GET /api/v1/process-runtime/demo/tasks/{taskId}/actions`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/claim`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/transfer`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/return`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/complete`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/add-sign`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/remove-sign`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/revoke`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/urge`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/read`
- `POST /api/v1/process-runtime/demo/approval-sheets/page`

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

- `processFormKey`
- `processFormVersion`
- `nodeFormKey`
- `nodeFormVersion`
- `effectiveFormKey`
- `effectiveFormVersion`
- `fieldBindings`
- `taskFormData`
- `businessType`
- `businessKey`
- `businessData`
- `flowNodes`
- `flowEdges`
- `instanceEvents`
- `taskTrace`
- `receiveTime`
- `readTime`
- `handleStartTime`
- `handleEndTime`
- `handleDurationSeconds`

生效规则：

- 若节点配置了 `nodeFormKey/nodeFormVersion`，则 `effectiveFormKey/effectiveFormVersion` 取节点表单
- 否则回退到流程默认表单 `processFormKey/processFormVersion`

## 审批单详情扩展字段

### `businessData`

审批单详情必须直接返回业务正文数据，供“审批单详情页”展示，不再要求前端按业务类型额外跳接口拼接。

当前 OA 范围内至少包括：

- `billId`
- `billNo`
- `sceneCode`
- `status`
- 业务字段本体

### `flowNodes / flowEdges`

审批单详情必须直接返回当前流程实例对应的流程图快照，供前端在详情页中渲染只读 React Flow 画布。

节点至少包含：

- `id`
- `type`
- `name`
- `position`

边至少包含：

- `id`
- `source`
- `target`
- `label`

### `instanceEvents`

用于动画回顾。事件必须按时间顺序返回。

每条事件至少包含：

- `eventId`
- `instanceId`
- `taskId`
- `nodeId`
- `eventType`
- `eventName`
- `operatorUserId`
- `occurredAt`
- `details`

说明：

- 当前 demo 先返回 `operatorUserId`，暂不扩展展示名
- 节点显示信息由 `nodeId + flowNodes/taskTrace` 组合推导
- 事件备注、目标用户、动作上下文等统一收敛在 `details`
- 动作轨迹需覆盖 `CLAIM / TRANSFER / RETURN / APPROVE / REJECT / ADD_SIGN / REMOVE_SIGN / REVOKE / URGE / READ`

### `taskTrace`

用于节点明细与时序展示。每条记录对应一次真实任务处理或流转。

每条轨迹至少包含：

- `taskId`
- `nodeId`
- `nodeName`
- `status`
- `assigneeUserId`
- `candidateUserIds`
- `action`
- `operatorUserId`
- `receiveTime`
- `readTime`
- `handleStartTime`
- `handleEndTime`
- `handleDurationSeconds`
- `comment`
- `sourceTaskId`
- `targetTaskId`
- `targetUserId`
- `isCcTask`
- `isAddSignTask`
- `isRevoked`

说明：

- 当前 demo 先返回办理人用户 ID，不扩展显示名
- 是否超时由后续 SLA/超时策略模块补齐，本期详情页先按 `false` 展示
- 抄送任务也进入 `taskTrace`，但在详情页中按“抄送轨迹”样式展示

## 审计字段

每次动作至少记录：

- `taskId`
- `instanceId`
- `action`
- `operatorUserId`
- `comment`
- `receiveTime`
- `readTime`
- `handleStartTime`
- `handleEndTime`
- `handleDurationSeconds`
- `createdAt`
- `sourceTaskId`
- `targetTaskId`
- `targetUserId`
- `actionCategory`
