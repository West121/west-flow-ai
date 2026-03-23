# PLM 助手工具协议

> 状态：Current v2026-03-23
> Owner：PLM owner + AI Gateway owner
> 适用范围：AI Copilot 中的 PLM 查询与摘要工具

## 1. 目标

定义当前 PLM 助手在 AI Copilot 中可调用的真实工具范围、能力边界与结果要求。

## 2. 当前支持的业务域

当前只覆盖以下 3 类业务：

- `PLM_ECR`
- `PLM_ECO`
- `PLM_MATERIAL`

说明：

- `PLM_MATERIAL` 表示物料主数据变更业务
- 旧口径 `PLM_MATERIAL_MASTER` 已不作为当前正式业务类型使用

## 3. 当前工具清单

### 3.1 主读工具

- `plm.bill.query`

用途：

- 查询 PLM 业务单
- 输出命中单据和业务摘要
- 为前端提供可展示的业务化结果块

### 3.2 兼容别名

- `plm.change.summary`

说明：

- 当前作为 `plm.bill.query` 的兼容别名保留
- 对外能力边界与 `plm.bill.query` 一致

### 3.3 相关写工具边界

PLM 业务自身当前不单独暴露 `plm.*.write` 工具。

如涉及写操作，当前遵循：

- 发起业务单走正式业务接口与 `process.start`
- 处理审批任务走 `task.handle`
- 全部写操作都必须进入确认流并保留审计

## 4. 查询能力边界

当前 PLM 助手支持：

- ECR 列表与摘要查询
- ECO 列表与摘要查询
- 物料主数据变更列表与摘要查询
- 业务单与审批单的关联信息摘要

当前不支持：

- 直接修改 ECR / ECO / 物料主数据变更正文
- 绕过业务页面直接改业务表
- BOM、图纸、CAD、PDM 等扩展域

## 5. 依赖的真实业务接口

PLM 助手能力建立在以下真实业务接口基础上：

- `POST /api/v1/plm/ecrs`
- `GET /api/v1/plm/ecrs/{billId}`
- `POST /api/v1/plm/ecrs/page`
- `POST /api/v1/plm/ecos`
- `GET /api/v1/plm/ecos/{billId}`
- `POST /api/v1/plm/ecos/page`
- `POST /api/v1/plm/material-master-changes`
- `GET /api/v1/plm/material-master-changes/{billId}`
- `POST /api/v1/plm/material-master-changes/page`
- `GET /api/v1/process-runtime/approval-sheets/by-business`

## 6. 返回结果要求

PLM 读工具结果必须是“业务化摘要”，而不是只返回一个通用结果对象。

当前推荐输出：

- `summary`：一句话总结命中情况
- `result`：结构化原始结果
- `fields`：首条或重点单据摘要字段
- `metrics`：命中数量、状态分布或关键指标

至少应覆盖：

- 命中的业务类型
- 首条命中单据的 `billId / billNo / title`
- 当前状态或审批状态摘要
- 业务单与审批单联查所需关键标识

## 7. 安全限制

- PLM 助手读工具必须继承当前用户数据权限
- 无 `ai:plm:assist` 能力时，不得命中 PLM 工具
- 任何写动作都必须走确认流和正式业务服务层

## 8. 废弃口径

以下口径已废弃：

- `plm.ecr.list`
- `plm.eco.detail`
- `plm.material-master.approval-status`
- `PLM_MATERIAL_MASTER` 作为当前正式业务类型

当前正式读工具统一收敛为 `plm.bill.query`，并保留 `plm.change.summary` 作为兼容别名
