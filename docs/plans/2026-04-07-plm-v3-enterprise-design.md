# PLM v3 企业级补强设计

## 目标

在现有 `PLM v2` 的基础上，把三类单据从“可管理的变更审批模块”继续推进到“企业级变更工作区 v1”。

本轮聚焦三件事：

1. 结构化受影响对象
2. 实施 / 验证 / 关闭生命周期
3. 详情工作区升级为真正的变更执行面板

本轮仍然不引入 CAD / BOM / PDM 外部系统集成，但要把数据结构和生命周期补到后续可接入的程度。

## 现状差距

`PLM v2` 已经具备：

- 草稿、提交、取消
- 工作台与业务台账
- 业务详情工作区
- AI 摘要与查询

但离企业级仍差一整层：

1. 受影响对象仍然是纯文本字段
2. 业务状态停留在 `DRAFT / RUNNING / COMPLETED / REJECTED / CANCELLED`
3. 没有实施、验证、关闭的执行过程
4. 详情页无法沉淀实施记录、验证结论和关闭信息
5. AI 能总结单据，但不能准确解释“影响了哪些对象、现在执行到哪一步”

## 本轮范围

### 1. 结构化受影响对象

新增统一受影响对象表，适配三类业务单：

- `plm_bill_affected_item`

字段：

- `id`
- `business_type`
- `bill_id`
- `item_type`
- `item_code`
- `item_name`
- `before_version`
- `after_version`
- `change_action`
- `owner_user_id`
- `remark`
- `sort_order`

说明：

- `business_type` 取值：`PLM_ECR / PLM_ECO / PLM_MATERIAL`
- `item_type` 先支持：`PART / DOCUMENT / BOM / MATERIAL / PROCESS`
- `change_action` 先支持：`ADD / UPDATE / REMOVE / REPLACE`

约束：

- 创建草稿和更新草稿时允许一次性覆盖该单据的受影响对象列表
- 已进入 `RUNNING` 之后不允许再编辑受影响对象

### 2. 生命周期扩展

新增状态：

- `IMPLEMENTING`
- `VALIDATING`
- `CLOSED`

三类单据统一支持以下动作：

- `START_IMPLEMENTATION`
- `MARK_VALIDATING`
- `CLOSE`

状态流转：

- `DRAFT -> RUNNING`
- `RUNNING -> REJECTED / CANCELLED / IMPLEMENTING`
- `IMPLEMENTING -> VALIDATING / CANCELLED`
- `VALIDATING -> CLOSED / IMPLEMENTING`
- `COMPLETED -> IMPLEMENTING`

说明：

- `COMPLETED` 保留，用于审批流结束
- 审批结束后，业务单仍需进入实施和验证阶段，最后才进入 `CLOSED`
- 这样业务生命周期与审批生命周期解耦

### 3. 实施 / 验证 / 关闭记录

在三类主表中补充：

- `implementation_owner`
- `implementation_summary`
- `implementation_started_at`
- `validation_owner`
- `validation_summary`
- `validated_at`
- `closed_by`
- `closed_at`
- `close_comment`

新增动作接口：

- 开始实施
- 提交验证结果
- 关闭单据

### 4. 详情工作区升级

详情页区块重组为：

1. 业务摘要
2. 受影响对象
3. 实施与验证
4. 审批联查
5. 生命周期操作

页面要求：

- 受影响对象表格化展示
- 当前阶段状态清晰可见
- 可见实施责任人、验证责任人、关闭信息
- 只有合法状态下才显示对应动作按钮

### 5. AI 补强

AI 查询结果补充：

- 受影响对象数量与摘要
- 当前业务阶段
- 是否已进入实施 / 验证 / 关闭
- 实施责任人与验证责任人

新增 AI 解释能力：

- “这个 ECO 影响哪些对象”
- “这个变更现在执行到哪一步”
- “哪些单据已经审批完成但尚未关闭”

## 方案比较

### 方案 A：只补状态，不补结构化对象

优点：

- 改动小

缺点：

- 仍然无法支撑企业级变更视图
- AI 也拿不到真正的影响对象

不采用。

### 方案 B：结构化对象 + 生命周期扩展（推荐）

优点：

- 补齐最关键的企业级缺口
- 不依赖外部系统
- 前后端和 AI 都能获得稳定结构

缺点：

- 需要数据库迁移
- 详情与创建接口都要扩展

采用本方案。

### 方案 C：直接引入 BOM / CAD / PDM 集成

优点：

- 更像完整 PLM

缺点：

- 范围过大
- 需要外部系统依赖

不采用。

## 架构设计

### 后端

新增：

- 受影响对象记录模型与 Mapper
- 生命周期动作接口
- 详情响应的结构化扩展

调整：

- `PlmLaunchService` 同时负责：
  - 草稿写入
  - 结构化对象覆写
  - 生命周期状态流转
  - 详情聚合读取

### 前端

调整：

- 三类创建表单增加“受影响对象”编辑区
- 三类详情页增加：
  - 受影响对象表
  - 实施 / 验证 / 关闭卡片
  - 生命周期动作区

### AI

保留现有 `plm.bill.query`，增强结果结构：

- `affectedItems`
- `affectedItemCount`
- `lifecycleStage`
- `implementationSummary`
- `validationSummary`
- `closedAt`

## 错误处理

- 非法状态流转：`409`
- 非法编辑已提交单据：`409`
- 受影响对象为空但必填：`400`
- 单据不存在：`404`
- 无权限关闭他人单据：`403`

## 测试策略

后端：

- 受影响对象增删改查映射
- 生命周期状态流转
- 详情聚合结果
- AI 查询摘要

前端：

- 创建/编辑受影响对象
- 详情工作区渲染
- 生命周期动作按钮显隐

