# 追加与动态构建设计

> 状态：Approved v1
> 日期：2026-03-23
> 适用范围：`main` 高级流程能力第二组

## 1. 背景

当前平台已经完成：

- 真实 `Flowable BPMN` 运行态
- 主子流程与终止高级策略
- 加签、减签、转办、驳回、跳转、撤销、委派、代理、离职转办
- 会签、或签、票签
- 自动化基础：超时审批、提醒、定时、触发

但 `追加` 与 `动态构建` 仍然缺少统一实现。当前最大风险不是“少两个功能”，而是如果继续沿用零散动作接口临时拼接，会把：

- DSL 扩展字段
- Flowable 映射方式
- 父子/附属实例关系
- 审批详情轨迹
- 运行态权限与确认策略

再次打散。

因此本轮先冻结这一组设计，再按统一模型开发。

## 2. 目标

本设计只做三件事：

1. 定义 `追加` 和 `动态构建` 的产品边界，避免和已有 `加签/减签/子流程` 混淆。
2. 固定它们在 `设计器 -> DSL -> BPMN/运行态 -> 轨迹 -> 详情/监控` 的落点。
3. 约束当前实现范围，确保这一组可以做成真实闭环。

## 3. 能力定义

### 3.1 追加

`追加` 定义为：

- 在流程已经运行到某个节点后，由办理人或流程管理员显式追加一段新的审批链路
- 追加链路可以是：
  - 一个或多个附属人工审批任务
  - 一个附属子流程调用

`追加` 与已有能力的区别：

- 不同于 `加签`
  - 加签是围绕当前任务增加协同办理人
  - 追加是增加新的审批链路单元
- 不同于 `跳转`
  - 跳转是改变当前执行位置
  - 追加是在原流程基础上增加附属处理链
- 不同于 `主子流程`
  - 主子流程是设计时固定结构
  - 追加是运行时临时插入结构

### 3.2 动态构建

`动态构建` 定义为：

- 流程运行到某个动态构建节点时
- 平台根据规则、变量或人工输入
- 动态生成后续审批任务或子流程调用

本轮 `动态构建` 只支持两类构建结果：

- 动态生成审批任务列表
- 动态生成子流程调用列表

明确不支持：

- 动态改写整张流程图
- 动态插入包容分支
- 动态改写历史节点定义

## 4. 统一设计原则

### 4.1 Flowable 负责主流程，平台负责附属结构语义

- 主流程仍由 `Flowable` 驱动
- `追加` 与 `动态构建` 产生的附属结构由平台显式落表
- 平台只补结构关系和轨迹语义，不自建第二套执行引擎

### 4.2 附属结构统一建模

`追加` 和 `动态构建` 都会生成“附属结构”，统一分成两类：

- `ADHOC_TASK`
  - 平台附属人工任务
- `ADHOC_SUBPROCESS`
  - 运行时附属子流程实例

这样后续详情、监控、终止、轨迹只需要围绕一种附属关系模型扩展。

### 4.3 读详情必须能还原结构

一旦产生追加或动态构建结构，审批单详情和实例监控必须能回答：

- 是谁追加/构建的
- 在哪个源节点触发
- 追加/构建了什么
- 当前状态是什么
- 是否完成/终止
- 对主流程推进造成了什么影响

## 5. DSL 设计

### 5.1 新节点类型

本轮新增一个设计器节点类型：

- `dynamic-builder`

用途：

- 设计时声明“这里运行时会动态生成审批链路”

### 5.2 `dynamic-builder` 节点字段

- `buildMode`
  - `APPROVER_TASKS`
  - `SUBPROCESS_CALLS`
- `sourceMode`
  - `RULE`
  - `MANUAL_TEMPLATE`
- `ruleExpression`
- `manualTemplateCode`
- `appendPolicy`
  - `SERIAL_AFTER_CURRENT`
  - `PARALLEL_WITH_CURRENT`
  - `SERIAL_BEFORE_NEXT`
- `maxGeneratedCount`
- `terminatePolicy`
  - `TERMINATE_GENERATED_ONLY`
  - `TERMINATE_PARENT_AND_GENERATED`

### 5.3 追加动作字段

追加不作为设计器固定节点，而是运行时动作。追加请求统一字段：

- `appendType`
  - `TASK`
  - `SUBPROCESS`
- `sourceTaskId`
- `sourceNodeId`
- `appendPolicy`
  - `SERIAL_AFTER_CURRENT`
  - `PARALLEL_WITH_CURRENT`
- `targetUserIds`
- `calledProcessKey`
- `calledVersionPolicy`
- `calledVersion`
- `comment`

## 6. 数据模型

### 6.1 新增附属结构表

新增：

- `wf_runtime_append_link`

核心字段：

- `id`
- `root_instance_id`
- `parent_instance_id`
- `source_task_id`
- `source_node_id`
- `append_type`
  - `TASK`
  - `SUBPROCESS`
- `runtime_link_type`
  - `ADHOC_TASK`
  - `ADHOC_SUBPROCESS`
- `policy`
- `target_task_id`
- `target_instance_id`
- `target_user_id`
- `called_process_key`
- `called_definition_id`
- `status`
  - `RUNNING`
  - `COMPLETED`
  - `TERMINATED`
- `trigger_mode`
  - `APPEND`
  - `DYNAMIC_BUILD`
- `operator_user_id`
- `comment_text`
- `created_at`
- `finished_at`

### 6.2 与现有表关系

- `wf_process_link`
  - 继续负责主子流程和设计时固定子流程
- `wf_runtime_append_link`
  - 负责运行时新增的附属任务/附属子流程

两张表共同组成实例结构树。

## 7. Flowable 映射

### 7.1 追加人工任务

- 复用现有平台 `adhoc task` 机制
- 由 `FlowableTaskActionService.createAdhocTask(...)` 创建
- 新任务通过 `wf_runtime_append_link` 与源任务关联

### 7.2 追加子流程

- 复用已做好的子流程启动链路
- 由平台显式启动一个新的附属子流程实例
- 将 `trigger_mode=APPEND`
- 将关系写入 `wf_runtime_append_link`

### 7.3 动态构建

- `dynamic-builder` 节点本身不直接映射成复杂 BPMN 结构
- BPMN 里先映射成一个普通服务占位节点或用户节点后的平台运行时钩子
- 到达节点时由平台根据规则：
  - 创建附属人工任务
  - 或启动附属子流程

本轮保持“Flowable 主链 + 平台运行时附属结构”模式，不在 BPMN 里生成不可维护的动态 XML。

## 8. 运行态规则

### 8.1 追加

追加动作必须满足：

- 当前用户有原任务处理权限或流程管理员权限
- 当前源任务仍处于可追加状态
- 追加链路写事件、写关系、写日志
- 若是 `SERIAL_AFTER_CURRENT`
  - 主任务完成后再进入追加链
- 若是 `PARALLEL_WITH_CURRENT`
  - 主任务与追加链并行存在

### 8.2 动态构建

动态构建节点触发时：

- 先解析规则
- 再生成附属结构
- 生成成功后写平台事件
- 主流程继续或等待，由 `appendPolicy` 决定

### 8.3 终止

当前组新增的附属结构必须支持：

- 终止附属任务
- 终止附属子流程
- 根流程终止时级联终止全部附属结构

## 9. 轨迹与详情

审批详情、实例监控、轨迹统一新增这些事件类型：

- `TASK_APPENDED`
- `SUBPROCESS_APPENDED`
- `DYNAMIC_BUILD_TRIGGERED`
- `DYNAMIC_BUILD_TASK_CREATED`
- `DYNAMIC_BUILD_SUBPROCESS_CREATED`
- `APPEND_TERMINATED`
- `DYNAMIC_BUILD_TERMINATED`

审批详情必须新增：

- 追加链路区块
- 动态构建结果区块
- 附属结构状态

实例监控必须新增：

- 根流程 + 主子流程 + 追加/动态构建附属结构的统一树视图

## 10. 权限与确认

### 10.1 运行态权限

新增权限点：

- `workflow:task:append`
- `workflow:task:append-subprocess`
- `workflow:runtime:dynamic-build:view`
- `workflow:runtime:dynamic-build:execute`

### 10.2 AI 规则

AI 触发这类动作时继续遵守：

- 读直执
- 写必确认

所以：

- AI 可以解释“这里是否支持追加/动态构建”
- AI 可以生成追加建议卡
- 但真正追加或动态构建执行，必须用户确认

## 11. 本轮实现边界

本轮只做：

- `追加`
  - 人工附属任务
  - 附属子流程
- `动态构建`
  - 基于规则生成人工附属任务
  - 基于规则生成附属子流程
- 详情、轨迹、监控闭环

本轮不做：

- 包容分支
- 沟通
- 穿越时空
- 动态改写已有 BPMN 图结构
- 拖拽式复杂动态构建设计器

## 12. 实施顺序

下一步实施顺序固定为：

1. 追加 + 动态构建设计器与 DSL
2. 附属结构落表与运行态创建
3. 审批详情、实例监控、轨迹展示
4. 终止与权限收口

