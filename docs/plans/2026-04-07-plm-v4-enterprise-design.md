# PLM v4 企业级补强设计

## 目标

在 `PLM v3` 的基础上，把系统从“企业级变更管理模块 v1”推进到“企业级 PLM 变更工作台 v2”。

本轮补齐四层核心能力：

1. BOM / 图纸 / PDM 深度对象模型
2. 版本基线与差异对比
3. 实施任务编排
4. 统计报表大盘

本轮仍然不做外部 CAD / PDM / ERP 的实时系统集成，但要把内部数据模型、业务状态和前端工作区补成可持续扩展的形态。

## 现状差距

`PLM v3` 已具备：

- ECR / ECO / 物料主数据变更三类业务单
- 草稿、提交、取消、实施、验证、关闭生命周期
- 结构化受影响对象
- 详情工作区与审批联查
- AI 查询增强

但仍然存在企业级缺口：

1. 受影响对象还是“列表项”，不是可追踪的 PLM 对象
2. 只有前后版本字符串，没有真正的版本基线和差异摘要
3. 实施阶段只有摘要字段，没有可执行任务清单
4. 工作台摘要有限，没有按业务维度的报表和分布

## 方案比较

### 方案 A：继续在主表上堆字段

优点：

- 改动快

缺点：

- 主表继续膨胀
- 版本对比、对象关系、任务编排很快失控
- 前后端维护成本高

不采用。

### 方案 B：新增原生 PLM v4 结构层（推荐）

新增四类结构：

- 对象主数据层
- 版本快照与对比层
- 实施任务层
- 统计聚合层

优点：

- 后续可接 BOM / 图纸 / PDM 真系统
- 前端工作区和 AI 都能得到稳定结构
- 报表与任务可以自然扩展

缺点：

- 需要数据库迁移
- 需要扩控制器与详情响应

采用本方案。

### 方案 C：直接外部 PDM / CAD 集成优先

优点：

- 最像完整企业级 PLM

缺点：

- 依赖外部系统
- 无法在当前仓库内闭环验证

不采用。

## 设计范围

### 1. 深度对象模型

新增统一对象表：

- `plm_object_master`

字段：

- `id`
- `object_type`：`PART / BOM / DOCUMENT / DRAWING / MATERIAL / PROCESS`
- `object_code`
- `object_name`
- `owner_user_id`
- `domain_code`
- `lifecycle_state`
- `source_system`
- `external_ref`
- `latest_revision`
- `latest_version_label`
- `created_at`
- `updated_at`

新增对象版本表：

- `plm_object_revision`

字段：

- `id`
- `object_id`
- `revision_code`
- `version_label`
- `version_status`
- `checksum`
- `summary_json`
- `snapshot_json`
- `created_by`
- `created_at`

新增单据对象关联表：

- `plm_bill_object_link`

字段：

- `id`
- `business_type`
- `bill_id`
- `object_id`
- `object_revision_id`
- `role_code`
- `change_action`
- `before_revision_code`
- `after_revision_code`
- `remark`
- `sort_order`

说明：

- `plm_bill_affected_item` 继续保留，作为前端表单录入层
- 提交草稿时，把受影响对象同步进 `object` / `revision` / `link` 三层
- 这让 v3 兼容数据不丢，同时新增更深结构

### 2. 版本基线与差异

新增差异表：

- `plm_revision_diff`

字段：

- `id`
- `business_type`
- `bill_id`
- `object_id`
- `before_revision_id`
- `after_revision_id`
- `diff_kind`
- `diff_summary`
- `diff_payload_json`
- `created_at`

支持的 `diff_kind`：

- `ATTRIBUTE`
- `BOM_STRUCTURE`
- `DOCUMENT`
- `ROUTING`

策略：

- 当前不做真正 BOM 引擎比对
- 先由前端 / API 录入差异摘要与结构化 payload
- 前端详情页能以“字段变更 / 结构变更 / 文档变更”方式展示

### 3. 实施任务编排

新增实施任务表：

- `plm_implementation_task`

字段：

- `id`
- `business_type`
- `bill_id`
- `task_no`
- `task_title`
- `task_type`
- `owner_user_id`
- `status`
- `planned_start_at`
- `planned_end_at`
- `started_at`
- `completed_at`
- `result_summary`
- `verification_required`
- `sort_order`
- `created_at`
- `updated_at`

任务状态：

- `PENDING`
- `RUNNING`
- `BLOCKED`
- `COMPLETED`
- `CANCELLED`

动作：

- 创建任务清单
- 更新任务
- 启动任务
- 完成任务
- 阻塞任务
- 取消任务

业务约束：

- `IMPLEMENTING` 阶段必须至少有一条任务
- `VALIDATING` 前要求所有必做任务完成
- `CLOSED` 前要求验证通过且未完成任务为 0

### 4. 统计报表大盘

新增仪表盘统计接口，覆盖：

- 总单量
- 按业务类型分布
- 按生命周期分布
- 近 30 天趋势
- 待关闭单据
- 超期实施任务
- 按负责人分布

响应结构：

- `summary`
- `typeDistribution`
- `stageDistribution`
- `trendSeries`
- `taskAlerts`
- `ownerRanking`

统计先基于数据库聚合 SQL 直接出，不引入 OLAP。

## 前端设计

### 工作台

新增 `企业级统计大盘` 区块：

- 顶部总览指标
- 类型分布
- 生命周期分布
- 任务预警
- 最近活动

### 详情页

详情工作区扩为 6 个区块：

1. 业务摘要
2. 受影响对象
3. 版本对比
4. 实施任务
5. 审批联查
6. 生命周期动作

### 创建 / 编辑页

在现有受影响对象表单基础上新增：

- 对象角色
- 版本基线
- 差异摘要
- 实施任务草案

## AI 增强

在 `plm.bill.query` 基础上增强：

- 受影响对象按类型聚合
- 版本差异摘要
- 实施任务完成度
- 超期任务提醒
- 可关闭 / 不可关闭原因

新增能力：

- “这个 ECO 影响哪些 BOM 和图纸”
- “这张变更单为什么还不能关闭”
- “列出实施阶段被阻塞的单据”

## 架构设计

### 后端

`PlmLaunchService` 继续作为业务编排核心，但新增以下边界：

- `PlmObjectService`
- `PlmRevisionDiffService`
- `PlmImplementationTaskService`
- `PlmDashboardService`

控制器继续挂在 `PLMController`，不拆资源前缀，避免前端路由震荡过大。

### 前端

`frontend/src/features/plm/pages.tsx` 已经偏大，本轮优先不大拆页面入口，但会把以下子区块抽成局部组件：

- dashboard
- diff panel
- task board
- object link table

### AI

继续复用 `DbAiCopilotService` 与 `AiCopilotConfiguration`，新增 PLM v4 查询拼装逻辑。

## 风险与控制

1. 表结构扩展过大
   - 控制方式：迁移脚本只增量，不重写 v3 结构

2. 前端详情页复杂度上升
   - 控制方式：新增局部组件，不重写现有路由

3. 测试成本上升
   - 控制方式：重点覆盖控制器、服务聚合和前端核心展示，不追求每个分支全覆盖

## 成功标准

完成后系统需要满足：

- 三类单据都能维护深度对象、版本差异、实施任务
- 详情页可查看对象关系、差异摘要、任务进度
- 生命周期动作与任务状态联动
- 工作台可看到统计大盘和预警
- AI 能解释对象影响、版本差异、任务阻塞与关闭条件
