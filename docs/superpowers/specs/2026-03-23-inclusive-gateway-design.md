# 包容分支设计

> 状态：Approved v1
> 日期：2026-03-23
> 适用范围：`main` 高级流程能力第三组

## 1. 背景

当前平台已经完成：

- 真实 `Flowable BPMN` 运行态
- 主子流程与终止高级策略
- 追加与动态构建附属结构
- 会签、或签、票签

但 `包容分支` 还没有真实闭环。现有设计器只支持：

- `condition` 排他分支
- `parallel` 并行分支

缺少：

- `inclusive gateway` 的设计器表达
- DSL 与 BPMN 的真实映射
- 分支命中信息的轨迹展示

## 2. 目标

本轮只做三件事：

1. 增加 `inclusive` 节点，统一表达包容分支的分支与汇聚。
2. 打通 `设计器 -> DSL -> BPMN inclusive gateway -> 运行态轨迹`。
3. 顺手把现有 `parallel` 的 `split / join` 方向一起收口，避免继续沿用错误的单态语义。

## 3. 产品边界

### 3.1 本轮包含

- `inclusive split`
- `inclusive join`
- 多条满足条件的分支同时命中
- 详情页轨迹补充“命中哪些分支”

### 3.2 本轮不包含

- 动态插入包容分支
- 嵌套包容分支可视化高级分析
- 包容分支专属统计面板

## 4. DSL 方案

设计器只暴露一个节点种类：

- `inclusive`

但节点配置增加：

- `gatewayDirection`
  - `SPLIT`
  - `JOIN`

DSL 节点类型映射为：

- `inclusive_split`
- `inclusive_join`

同理，现有 `parallel` 也统一补齐：

- `parallel_split`
- `parallel_join`

## 5. BPMN 方案

- `inclusive_split` 映射为 `InclusiveGateway`
- `inclusive_join` 映射为 `InclusiveGateway`
- 网关方向通过扩展属性 `gatewayType=split/join` 保留，供平台展示和后续轨迹解释使用
- `inclusive_split` 的出边条件沿用现有 `edge.condition.expression`

## 6. 运行态与轨迹方案

- `Flowable` 负责真实包容分支命中与汇聚
- 平台轨迹补充分支命中摘要：
  - 来源节点
  - 命中的 edge id 列表
  - 命中的目标节点 id 列表

本轮先不新增独立大表，优先复用实例事件模型。

## 7. 前端交互

- 调色板新增 `包容分支`
- `parallel` 与 `inclusive` 都在节点面板展示“节点方向”：
  - 分支
  - 汇聚
- 审批详情和实例监控后续展示“包容分支命中信息”

## 8. 实施顺序

1. 文档与专项计划
2. 设计器、DSL、BPMN 第一批
3. 运行态轨迹与详情展示第二批

