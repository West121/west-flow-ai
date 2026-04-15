# PLM 项目管理完整功能设计

## 背景

当前 `PLM` 已经具备较深的变更管理能力：

- `ECR / ECO / 物料主数据变更`
- 受影响对象
- BOM / 文档 / 基线
- 实施任务与验收清单
- 角色矩阵、权限、驾驶舱、外部边界

但这套能力仍然是“变更域视角”，缺少“项目域视角”：

- 没有项目台账
- 没有项目阶段与里程碑
- 没有项目级负责人/核心团队
- 没有项目与 `ECR / ECO / 物料变更 / BOM / 文档 / 实施任务` 的统一关联
- 没有项目维度的交付健康度与风险驾驶舱

结果是：

- 变更单很多，但无法回答“这些变更属于哪个项目”
- 可以看单据状态，但无法回答“项目目前处于什么阶段、离交付还差什么”
- 变更风险和实施阻塞不能在项目视角汇总

## 目标

交付一整层 **PLM 项目管理域**，不是简化版台账，而是能与现有 PLM 变更域、对象域、实施域、驾驶舱、AI 直接联动的完整能力。

交付后，平台需要能回答：

1. 当前有哪些 PLM 项目，分别属于什么阶段、什么风险等级、什么产品线
2. 一个项目绑定了哪些 `ECR / ECO / 物料变更`
3. 一个项目下有哪些关键对象、文档、BOM、基线、实施任务
4. 一个项目的里程碑是否按期、被哪些变更或任务阻塞
5. 项目级驾驶舱如何汇总变更进展、实施进展、关闭准备度与风险

## 非目标

本轮不做：

- 真实 `ERP / MES / PDM / CAD` 外部项目同步
- 甘特图级资源排程引擎
- 成本预算 / 财务模块
- 跨项目组合管理（Portfolio）审批

## 方案对比

### 方案 A：只给现有单据加 `projectId`

做法：

- 在 `ECR / ECO / MATERIAL` 三类表上直接加 `project_id`
- 前端只补一个项目下拉框和项目筛选

优点：

- 改动小
- 最快落地

缺点：

- 不是项目管理，只是“单据分组”
- 没有项目阶段、里程碑、团队、健康度
- 无法承载项目驾驶舱和项目工作区

### 方案 B：独立项目域，但只做台账与详情

做法：

- 新增 `plm_project`
- 只做项目列表、详情、单据关联

优点：

- 有明确项目对象
- 比方案 A 强很多

缺点：

- 没有阶段门控、里程碑、项目健康度
- 仍然不算完整项目管理

### 方案 C：独立项目域 + 阶段/里程碑/团队/关联工作区（推荐）

做法：

- 新增项目主表、里程碑、成员、关联对象、阶段事件、项目指标
- 详情页同时展示项目概览、阶段、里程碑、成员、关联变更、关联对象、实施工作区、项目驾驶舱

优点：

- 真正形成“项目域”
- 与现有 `ECR / ECO / MATERIAL / Object / Implementation / Dashboard / AI` 能自然集成
- 后续可扩展到组合管理

缺点：

- 工作量最大

推荐采用 **方案 C**。

## 核心能力范围

### 1. 项目台账

新增项目对象：

- 项目编号
- 项目名称
- 项目类型
- 产品线/业务域
- 项目经理
- 核心团队
- 当前阶段
- 生命周期状态
- 风险等级
- 目标发布日期
- 实际发布日期
- 摘要说明

台账筛选维度：

- 项目状态
- 当前阶段
- 风险等级
- 产品线
- 项目经理
- 关键词

### 2. 项目阶段与里程碑

项目阶段建议内置：

- `DISCOVERY`
- `DESIGN`
- `ENGINEERING`
- `VALIDATION`
- `RELEASE`
- `CLOSED`

里程碑能力：

- 里程碑编码
- 里程碑名称
- 计划日期
- 实际日期
- 状态
- 阻塞原因
- 关联对象/单据/任务
- 负责人

### 3. 项目团队

项目成员不是简单成员列表，而是按角色建模：

- 项目经理
- 变更经理
- 研发负责人
- 工艺负责人
- 质量负责人
- 文控负责人
- 制造负责人
- 数据负责人

每个成员需要有：

- 用户 ID
- 显示名
- 角色编码
- 角色标签
- 是否主责
- 状态

### 4. 项目关联域

项目需要可以统一挂接以下对象：

- `PLM_ECR`
- `PLM_ECO`
- `PLM_MATERIAL`
- 受影响对象
- 文档资产
- BOM 节点
- 配置基线
- 实施任务

项目详情页要能直接回答：

- 当前项目下共有多少变更单
- 哪些变更还没关闭
- 哪些实施任务阻塞项目里程碑
- 哪些基线或文档还没发布

### 5. 项目驾驶舱

项目驾驶舱需要是项目维度，而不是当前已有的单据维度聚合。

建议输出：

- 变更总量 / 运行中 / 已关闭
- 高风险变更数
- 阻塞里程碑数
- 开放实施任务数
- 基线未发布数
- 文档未发布数
- 按阶段的进度分布
- 最近项目动态

### 6. AI 项目助手

AI 不负责改写数据，但要支持项目级读取：

- `plm.project.query`
- `plm.project.summary`

至少能回答：

- 当前项目最主要的风险是什么
- 项目卡在哪个阶段
- 哪些变更和任务最影响交付
- 距离目标发布日期还缺什么

## 数据模型

### 新增表

建议新增：

- `plm_project`
- `plm_project_member`
- `plm_project_milestone`
- `plm_project_link`
- `plm_project_stage_event`

#### plm_project

主数据：

- `id`
- `project_no`
- `project_name`
- `project_type`
- `business_domain`
- `product_line_code`
- `product_line_name`
- `current_phase`
- `status`
- `risk_level`
- `target_release_date`
- `actual_release_date`
- `owner_user_id`
- `summary`
- `created_by`
- `created_at`
- `updated_at`

#### plm_project_member

- `id`
- `project_id`
- `role_code`
- `role_label`
- `user_id`
- `display_name`
- `primary_flag`
- `status`
- `sort_order`

#### plm_project_milestone

- `id`
- `project_id`
- `milestone_code`
- `milestone_name`
- `phase_code`
- `planned_at`
- `actual_at`
- `status`
- `owner_user_id`
- `blocker_summary`
- `sort_order`

#### plm_project_link

统一关联表：

- `id`
- `project_id`
- `link_type`
- `business_type`
- `target_id`
- `target_code`
- `target_name`
- `target_status`
- `summary`
- `sort_order`

`link_type` 允许：

- `PLM_BILL`
- `PLM_OBJECT`
- `DOCUMENT_ASSET`
- `BOM_NODE`
- `BASELINE`
- `IMPLEMENTATION_TASK`

#### plm_project_stage_event

- `id`
- `project_id`
- `event_type`
- `phase_code`
- `operator_user_id`
- `summary`
- `occurred_at`

## 后端接口设计

在现有 `PLMController` 下新增项目接口：

- `GET /api/v1/plm/projects/page`
- `POST /api/v1/plm/projects`
- `GET /api/v1/plm/projects/{projectId}`
- `PUT /api/v1/plm/projects/{projectId}`
- `GET /api/v1/plm/projects/{projectId}/members`
- `PUT /api/v1/plm/projects/{projectId}/members`
- `GET /api/v1/plm/projects/{projectId}/milestones`
- `POST /api/v1/plm/projects/{projectId}/milestones`
- `PUT /api/v1/plm/projects/{projectId}/milestones/{milestoneId}`
- `GET /api/v1/plm/projects/{projectId}/links`
- `POST /api/v1/plm/projects/{projectId}/links`
- `DELETE /api/v1/plm/projects/{projectId}/links/{linkId}`
- `GET /api/v1/plm/projects/{projectId}/dashboard`
- `POST /api/v1/plm/projects/{projectId}/phase-transition`

### 阶段流转规则

阶段流转不能随意跳：

- `DISCOVERY -> DESIGN -> ENGINEERING -> VALIDATION -> RELEASE -> CLOSED`

允许：

- `ON_HOLD`
- `CANCELLED`

但需要保留事件记录。

## 前端设计

### 路由

新增：

- `/plm/projects`
- `/plm/projects/create`
- `/plm/projects/$projectId`

### 页面结构

#### 项目台账页

- 顶部摘要
- 项目筛选
- 项目列表
- 风险标签 / 阶段标签 / 发布日期

#### 项目详情页

按工作区组织：

1. 项目概览
2. 阶段与里程碑
3. 项目团队
4. 关联变更单
5. 关联对象 / 文档 / 基线
6. 实施工作区
7. 项目驾驶舱
8. AI 项目解读入口

### 交互原则

- 与现有 `PLM` 风格保持一致，继续走工作区式详情页
- 不新增独立设计系统
- 不引入新的查询协议
- 继续复用现有 `ResourceListPage`、`PageShell`、`PLM` 组件风格

## AI 设计

在现有 `PLM` AI 能力上新增项目域：

- `plm.project.query`
- `plm.project.summary`

AI 输入：

- 项目详情
- 成员
- 里程碑
- 关联变更
- 关联对象
- 驾驶舱摘要

AI 输出：

- 项目现状
- 最大风险
- 当前阻塞
- 交付准备度
- 建议优先动作

## 测试策略

### 后端

- `PLMControllerTest`
  - 项目创建
  - 台账分页
  - 详情读取
  - 里程碑读写
  - 关联关系读写
  - 阶段流转

### 前端

- `frontend/src/features/plm/pages.test.tsx`
  - 项目台账渲染
  - 项目详情概览
  - 里程碑与成员区块
  - 关联变更与驾驶舱

## 完成标准

这轮完成后，必须满足：

1. `/plm/projects` 可真实查询项目台账
2. 可以创建和编辑项目
3. 可以维护项目成员和里程碑
4. 可以把现有 `ECR / ECO / MATERIAL / OBJECT / TASK / BASELINE / DOCUMENT` 关联到项目
5. 项目详情能展示项目级驾驶舱
6. AI 能对项目做摘要与风险解读

做到这里，PLM 才算真正具备“项目管理层”，而不是只有变更管理层。
