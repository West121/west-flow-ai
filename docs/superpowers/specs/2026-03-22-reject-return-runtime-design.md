# 驳回与回退策略运行态设计

> 状态：Archived v2026-03-23
> 日期：2026-03-22
> 适用范围：`codex/m0-foundation` 历史设计冻结稿，供回看当时动作语义

> 历史说明：本文件保留 2026-03-22 设计冻结时的表述。当前真实接口已统一到 `/api/v1/process-runtime/*`，下文出现的 `/api/v1/process-runtime/demo/*` 仅代表当时草案，不再代表现状。

## 1. 背景

当前仓库已经具备：

- `认领 / 转办 / 退回上一步`
- `加签 / 减签 / 撤销 / 催办`
- `真实抄送模型 / 已阅`
- 审批单详情页、流程图回顾、动作轨迹

但“回退类动作”仍然割裂：

- `REJECT` 只是基础审批动作，没有目标策略与重审策略
- `退回` 只支持上一步，不能覆盖发起人或任意节点场景
- 缺少平台级 `跳转`
- 缺少“当前办理人未处理前上一节点提交人可拿回”的能力
- 缺少“历史任务唤醒，重新进入审批流程”的能力

本设计用于冻结这一批运行态动作与重审语义。

## 2. 本批次范围

### 2.1 冻结范围

- 驳回到上一步人工节点
- 驳回到发起人
- 驳回到任意人工节点
- 重新审批策略
- 跳转
- 拿回
- 唤醒
- 审批单详情动作轨迹补齐上述动作

### 2.2 明确不在本批次

- 委派
- 代理
- 离职转办
- 追加
- 动态构建
- 定时 / 触发 / 自动提醒 / 超时审批
- Flowable 引擎级真实回滚

## 3. 动作语义

### 3.1 驳回

驳回是“带目标与重审策略的回退动作”，不再混同为普通 `REJECT`。

当前冻结三种目标策略：

- `PREVIOUS_USER_TASK`
- `INITIATOR`
- `ANY_USER_TASK`

当前冻结两种重审策略：

- `CONTINUE`
  - 驳回目标重新审批通过后，按目标节点原有出边继续向后执行
- `RETURN_TO_REJECTED_NODE`
  - 驳回目标重新审批通过后，优先回到“发起驳回的那个节点”继续处理

说明：

- 原发起驳回的任务终态为 `REJECTED`
- 驳回不会直接终止实例，实例保持 `RUNNING`
- 驳回轨迹必须显式显示来源任务、目标任务、目标节点、重审策略

### 3.2 跳转

跳转是平台级强制路由动作。

规则：

- 仅平台特权用户允许执行
- 仅当前活动人工任务允许跳转
- 本批次仅允许跳转到：
  - 任意人工审批节点
  - `end` 节点
- 被跳转的当前任务终态为 `JUMPED`
- 若跳转到人工节点，则创建新的待办
- 若跳转到 `end`，则实例结束

### 3.3 拿回

拿回是“上一节点提交人员在当前办理人未处理前收回审批单”的动作。

规则：

- 当前任务必须尚未阅读、尚未开始办理
- 仅上一节点实际提交人允许拿回
- 被拿回任务终态为 `TAKEN_BACK`
- 系统在上一人工节点重新创建任务，交还给拿回人

### 3.4 唤醒

唤醒用于把终态实例重新拉回运行态。

规则：

- 仅终态实例允许唤醒
- 本批次只支持选择一条历史人工任务作为唤醒源
- 唤醒后实例恢复为 `RUNNING`
- 历史任务本体保留，系统额外创建新的待办任务

## 4. 数据与审计要求

### 4.1 任务终态扩展

- `REJECTED`
- `JUMPED`
- `TAKEN_BACK`

### 4.2 审批单详情动作轨迹

动作轨迹必须补齐以下信息：

- `actionCategory`
- `sourceTaskId`
- `targetTaskId`
- `targetUserId`
- `targetStrategy`
- `targetNodeId`
- `targetNodeName`
- `reapproveStrategy`

### 4.3 流程图回顾

流程图回顾中必须可识别：

- 驳回路径
- 跳转路径
- 拿回路径
- 唤醒后重新进入的路径

## 5. 接口设计

当时冻结的新增运行态接口草案：

- `POST /api/v1/process-runtime/demo/tasks/{taskId}/reject`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/jump`
- `POST /api/v1/process-runtime/demo/tasks/{taskId}/take-back`
- `POST /api/v1/process-runtime/demo/instances/{instanceId}/wake-up`

当前真实接口对应为：

- `POST /api/v1/process-runtime/tasks/{taskId}/reject`
- `POST /api/v1/process-runtime/tasks/{taskId}/jump`
- `POST /api/v1/process-runtime/tasks/{taskId}/take-back`
- `POST /api/v1/process-runtime/instances/{instanceId}/wake-up`

当时设计说明：

- 现有 `POST /complete` 保留给 `APPROVE` 和基础 `REJECT` 兼容逻辑
- 前端新页面与详情页统一切到专用动作接口，不再靠 `complete(action=REJECT)` 承载复杂策略

## 6. 前端交互

### 6.1 审批单详情动作区

新增动作：

- 驳回
- 跳转
- 拿回
- 唤醒

交互要求：

- 驳回使用独立对话框，必须选择驳回目标策略
- 选择“任意节点”时，需展示可选人工节点列表
- 选择“继续审批策略”时，必须展示中文说明
- 跳转使用独立对话框，必须选择目标节点
- 拿回与唤醒使用确认型对话框

### 6.2 详情页时序展示

轨迹时间线需要新增中文标签：

- `驳回到上一步`
- `驳回到发起人`
- `驳回到任意节点`
- `跳转`
- `拿回`
- `唤醒`

## 7. 当时实现约束

- 继续复用当前 `processruntime` 单一 demo 运行时，不引入第二套动作引擎
- 不在本批次内修改 OA 业务表结构
- 不做 Flowable 引擎级真实迁移，只做平台层 demo 语义闭环
- 必须先补测试，再补实现
