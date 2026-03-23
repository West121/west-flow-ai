# Phase 3 真实 Flowable 运行态重构设计

> 状态：Archived v2026-03-23
> 日期：2026-03-22
> 适用范围：`codex/m0-foundation` Phase 3 历史重构设计

> 历史说明：本文件记录从旧 demo 运行态迁移到真实 `Flowable` 的设计背景。迁移现已完成；下文如提到旧 demo 路径或旧内存运行态服务，只用于描述当时起点，不再代表当前接口现状。

## 1. 背景

当前平台已经具备流程设计、发布、OA 发起、流程中心、审批单详情、运行态动作、自动化配置等完整产品骨架，但运行态核心仍然建立在：

- 旧 demo 运行态路径
- 旧内存运行态服务
- 内存态实例、任务、轨迹、事件拼装

这意味着当前平台虽然页面和动作已经齐全，但底层不是真实 BPMN 引擎，无法作为后续流程管理后台、高级审批能力、AI 审批和 PLM 案例的稳定基础。

Phase 3 的目标不是继续增强 demo，而是直接切换到真实 `Flowable BPMN` 运行时。

## 2. 目标

本阶段完成后，平台需要满足以下目标：

- 所有流程实例、任务、历史、变量、动作、评论、轨迹均建立在真实 `Flowable` 引擎之上
- 废弃旧 demo 运行态路径语义
- 保留当前已经具备的流程主链路功能，并全部迁移到真实引擎运行
- 只保留 `Flowable BPMN/Process Engine` 必需能力，禁用无关模块，避免多余表和功能扩散
- 为后续 `Phase 4 流程管理后台` 与 `Phase 5 高级审批能力` 提供稳定基础

## 3. 范围

### 3.1 本阶段必须完成

#### 流程主链路

- 流程发起
- 待办
- 已办
- 我发起
- 抄送我
- 审批单详情
- 审批过程流程图预览
- 审批过程动画回顾
- 业务单与流程实例绑定

#### 已有运行态动作

- 认领
- 转办
- 退回
- 驳回
- 跳转
- 拿回
- 唤醒
- 加签
- 减签
- 撤销
- 催办
- 已阅
- 委派
- 代理
- 离职转办
- 抄送我真实模型

#### 已有自动化能力

- 超时审批
- 自动提醒
- 定时节点
- 触发节点

#### 真实运行态基础设施

- 真实实例查询
- 真实任务查询
- 真实历史查询
- 真实流程变量
- 真实评论与意见
- 真实动作权限与状态校验
- 真实事件轨迹组装

### 3.2 本阶段明确不新增

本阶段不新增当前尚未真正落地的高级流程产品能力：

- 主子流程
- 追加
- 动态构建
- 包容分支
- 穿越时空
- 新的复杂票签/或签/会签策略
- AI 审批

也就是说，本阶段是“把现有流程相关功能全部切到真实 Flowable 上”，而不是顺手做完 `Phase 5`。

## 4. Flowable 技术边界

### 4.1 只保留 BPMN 引擎

后端只保留 `Flowable BPMN/Process Engine` 必需能力：

- `RepositoryService`
- `RuntimeService`
- `TaskService`
- `HistoryService`
- `ManagementService`
- `ProcessEngineConfiguration`

明确不引入或不启用下列无关模块能力：

- `CMMN`
- `DMN`
- `Form`
- `Content`
- `App`
- `Event Registry`
- `IDM` 平台化能力

目标是减少无关表、无关功能和无关配置扩散，只围绕 BPMN 审批引擎建设。

### 4.2 Helper / Facade 层

为避免业务服务到处直接散调 Flowable 原生 API，新增独立封装层：

- `com.westflow.flowable.engine.FlowableEngineFacade`
- `com.westflow.flowable.helper.FlowableQueryHelper`
- `com.westflow.flowable.helper.FlowableCommandHelper`
- `com.westflow.flowable.helper.FlowableVariableHelper`
- `com.westflow.flowable.helper.FlowableHistoryHelper`

约束：

- `helper/facade` 只负责引擎调用、查询转换、变量命名规范、通用命令封装
- 它不承载审批业务规则
- 审批业务规则仍在 `processruntime/service` 等平台域服务中完成

## 5. 后端架构

### 5.1 包结构

建议新增和重构如下包：

- `backend/src/main/java/com/westflow/flowable/engine`
- `backend/src/main/java/com/westflow/flowable/helper`
- `backend/src/main/java/com/westflow/flowable/model`
- `backend/src/main/java/com/westflow/processruntime/service`
- `backend/src/main/java/com/westflow/processruntime/assembler`
- `backend/src/main/java/com/westflow/processruntime/api`

### 5.2 核心服务

#### FlowableDeploymentService

负责：

- 发布 BPMN 到 Flowable
- 查询流程定义、版本、部署信息
- 将平台流程定义与 Flowable 部署记录关联

#### FlowableRuntimeQueryService

负责：

- 查询运行中实例
- 查询活动任务
- 查询我发起实例
- 查询抄送任务
- 查询历史任务、历史活动、变量、评论

#### FlowableTaskActionService

负责：

- 认领
- 完成
- 驳回
- 退回
- 跳转
- 转办
- 委派
- 加签
- 减签
- 已阅
- 撤销
- 拿回
- 唤醒
- 离职转办

#### FlowableApprovalSheetAssembler

负责：

- 将业务单、流程实例、任务、历史、变量、事件组装成审批单详情
- 组装待办、已办、我发起、抄送我的页面视图
- 组装流程图节点执行态与轨迹明细

#### FlowableRuntimeEventService

负责：

- 记录平台动作事件
- 记录通知与自动化事件
- 为审批图回放和动作时间线提供扩展数据

说明：

- Flowable 历史表解决“引擎事实”
- 平台扩展事件表解决“产品级动作轨迹和展示语义”

## 6. 数据模型

### 6.1 Flowable 引擎表

由 Flowable BPMN 引擎维护：

- 运行时表
- 历史表
- 必需 identity link / variable / comment / job 表

### 6.2 平台扩展表

保留并补充平台自己的业务表：

- 业务单表
- 业务与实例绑定表
- 平台动作事件表
- 自动化事件表
- 通知发送记录表
- 业务扩展配置表

扩展表的职责不是复制 Flowable 全量状态，而是承载平台产品需要但引擎原生不直接提供的展示和业务语义。

## 7. 接口重构

### 7.1 基本原则

- 废弃旧 demo 运行态路径
- 启用正式 `/api/v1/process-runtime/*`
- 接口模型更贴近真实引擎语义
- 但仍保留平台页面所需的中文产品视图字段

### 7.2 资源分组

#### `process-instances`

- 实例分页查询
- 实例详情
- 实例变量
- 实例状态
- 实例轨迹

#### `tasks`

- 待办分页
- 已办分页
- 抄送分页
- 任务详情
- 任务动作

#### `histories`

- 历史活动
- 历史任务
- 评论
- 动作轨迹
- 自动化事件
- 通知记录

#### `business-links`

- 业务单绑定流程
- 按业务单查询审批单详情

## 8. 前端重构原则

### 8.1 页面保留，数据源切换

前端不推翻现有页面结构，继续保留：

- 待办
- 已办
- 我发起
- 抄送我
- 流程发起
- 审批单详情

但数据源改成真实：

- Flowable 任务
- Flowable 历史任务
- Flowable 历史活动
- Flowable 变量
- Flowable 评论
- 平台扩展动作事件

### 8.2 审批单详情

继续保留当前审批单详情布局：

- 业务表单正文
- 右侧动作区
- 流程图预览
- 动画回顾
- 轨迹时间线
- 自动化记录
- 通知发送记录

只是数据组装方式改为真实运行态。

## 9. 测试策略

本阶段必须启用真实 Flowable 测试，不再允许仅依赖内存 demo 伪造运行态。

### 9.1 后端集成测试

- 发布 BPMN
- 启动实例
- 查询任务
- 认领任务
- 完成任务
- 查询历史活动
- 查询评论和变量
- 验证自动化和事件

### 9.2 API 测试

- 新 `process-runtime` 接口
- 审批单详情接口
- 任务动作接口
- 业务绑定查询接口

### 9.3 前端回归测试

- 工作台列表
- 审批单详情
- 发起流程
- 关键动作刷新
- 抄送我、我发起、已办等列表回归

## 10. 分阶段落地方式

虽然本阶段最终是一个整体闭环，但实现仍按三个子阶段推进：

### Phase 3A：引擎接线与最小实例闭环

- BPMN 部署到 Flowable
- 发起实例
- 查询待办
- 认领
- 完成任务
- 查询历史

### Phase 3B：平台工作台重建

- 待办 / 已办 / 我发起 / 抄送我
- 审批单详情
- 流程图与轨迹
- 业务单绑定查询
- 现有中国式动作迁移到真实引擎

### Phase 3C：自动化与轨迹重接

- 超时审批
- 自动提醒
- 定时
- 触发
- 自动化事件
- 通知记录
- 删除 demo 运行态残留

## 11. 删除项

本阶段收尾时应删除或停用以下内容：

- 旧内存运行态服务
- 旧 demo 运行态路径
- demo 内存实例/任务/事件模型
- 测试环境中禁用 Flowable 的配置
- 仅为 demo projection 存在的运行态拼装逻辑

## 12. 成功标准

Phase 3 完成后，应满足：

- 平台所有现有流程主链路功能都运行在真实 Flowable 上
- 不再依赖 demo 内存运行态
- 前端工作台与审批单详情继续可用
- 现有中国式动作能力未丢失
- 自动化能力重新接上真实引擎
- 为 Phase 4 与 Phase 5 提供稳定基础
