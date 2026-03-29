# FlowableProcessRuntimeService 重构设计

## 背景

当前 [FlowableProcessRuntimeService.java](/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java) 已达到 `6826` 行，承担了过多职责：

- 任务分页查询与审批单分页查询
- 任务可见性判断与阻塞判断
- 运行时任务 DTO 组装
- 审批详情 trace / automation / notification 查询
- 各类动作可用性判断
- 一部分性能优化缓存与耗时打点

这已经偏离了正常 Java 服务层的单一职责规范，也让后续性能优化、测试定位和代码评审成本持续升高。

当前压测日志还表明：

- `tasks.page`
- `approval-sheets.page`

两条读链的热点并不在分页切片，而是在：

- `visibleActiveTasks(...)`
- `resolveTaskKind(...)`
- `taskLocalVariables(...)`
- `toTaskListItem(...)`

也就是说，类结构问题和性能问题已经耦合在一起，不适合继续在巨石类里叠补丁。

## 目标

这轮重构目标不是“把所有方法拆成很多 service”，而是按正常 Java 规范做 **中等拆分**：

1. 保留 `FlowableProcessRuntimeService` 作为运行态门面
2. 把查询链、可见性判断、组装链、trace 查询拆出去
3. 保持外部接口语义尽量不变
4. 不过度拆分动作执行链，避免引入新的复杂度
5. 在新结构上继续深挖 `tasks.page` / `approval-sheets.page` 性能瓶颈
6. 代码层优先使用 `MyBatis-Plus` 简化 Mapper / 查询逻辑，复杂 Flowable 运行时查询保持现状，确实不适合时再保留 `JdbcTemplate`

## 设计原则

### 1. 保留门面，不保留巨石

`FlowableProcessRuntimeService` 继续作为：

- Controller 依赖入口
- 事务边界承接层
- 运行态查询和动作的高层编排门面

但不再直接承接大段查询构建、过滤与 DTO 组装细节。

### 2. 中等拆分，不做过度服务化

不采用“一个方法一个类”的过度设计。第一批只抽出最重、最容易独立测试的职责：

- `RuntimeTaskQueryService`
- `RuntimeApprovalSheetQueryService`
- `RuntimeTaskVisibilityService`
- `RuntimeTaskAssembler`
- `RuntimeTraceQueryService`
- `RuntimeTaskActionSupportService`

这 6 个类足够把当前最重的职责分层开，同时不会把目录拆得过碎。

### 3. 查询链与动作链分阶段处理

这一轮先优先收口：

- `tasks.page`
- `approval-sheets.page`
- `visibleActiveTasks(...)`
- `toTaskListItem(...)`
- 详情页 trace / event / automation / notification 查询

动作执行主链暂时不大拆，只把动作“可用性判断”和部分通用辅助逻辑抽出去，避免一轮重构同时动太多高风险路径。

### 4. 性能优化必须依附在新边界内

后续性能优化不再直接加在门面类里，而是放进真正承担职责的服务：

- `RuntimeTaskVisibilityService` 负责 `visibleActiveTasks`、阻塞判断、缓存
- `RuntimeTaskAssembler` 负责任务 DTO 组装和变量读取优化
- `RuntimeApprovalSheetQueryService` 负责审批单列表快速路径与实例投影

这样后续定位慢点时，日志和代码边界可以对得上。

## 目标结构

### 1. FlowableProcessRuntimeService

职责：

- 门面
- 事务边界
- 聚合依赖
- 对外暴露现有 service API

不再直接包含：

- 任务可见性循环
- DTO 组装细节
- 审批单分页快速路径实现细节
- trace 查询细节

### 2. RuntimeTaskQueryService

职责：

- `tasks.page` 主链
- 默认分页快速路径
- 非默认查询路径编排
- 请求级缓存创建与传递

会依赖：

- `RuntimeTaskVisibilityService`
- `RuntimeTaskAssembler`

### 3. RuntimeApprovalSheetQueryService

职责：

- `approval-sheets.page`
- `TODO` 快速路径
- initiated / done / copied 组装
- 审批单列表快速投影

### 4. RuntimeTaskVisibilityService

职责：

- `visibleActiveTasks(...)`
- 候选/候选组查询合并
- `taskKind` 判定
- append 结构阻塞判断
- 请求级缓存与分段耗时日志

这是下一轮性能持续深挖的主战场。

### 5. RuntimeTaskAssembler

职责：

- `toTaskListItem(...)`
- assignment mode / candidate users / candidate groups
- 运行时变量 / 流程定义 / identity links 预取与复用

### 6. RuntimeTraceQueryService

职责：

- 审批详情 trace、instance events、automation trace、notification records
- trace store 聚合
- 详情页对外查询辅助

### 7. RuntimeTaskActionSupportService

职责：

- 动作可用性判断的公共逻辑
- `canTakeBack / canRevoke / canUrge`
- 可复用的 task semantic / status / blocking support

这一层只拆“support”，不急着接管动作执行。

## MyBatis-Plus 使用策略

这轮不强行把 Flowable Runtime API 查询改成 MyBatis-Plus，因为：

- `TaskService.createTaskQuery()`
- `HistoryService.createHistoricTaskInstanceQuery()`

这些本来就不是 MP 该接管的部分。

但对于自有表查询，优先策略是：

1. 能用现有 Mapper + MyBatis-Plus 简化的，就用 MP
2. 已存在自定义 SQL 且复杂度合理的，保留 Mapper XML/注解 SQL
3. 只有在确实不适合 MP 的批量聚合、诊断 SQL 时，才保留 `JdbcTemplate`

尤其这轮里：

- `RuntimeAppendLinkService / RuntimeAppendLinkMapper`

后续若继续扩批量查询，会优先朝 MP 风格收敛，而不是继续散落更多 `JdbcTemplate` 查询。

## 错误处理

重构后仍保持：

- 资源不存在：现有 `resourceNotFound(...)`
- 任务不存在：现有 `taskNotFound(...)`
- 合同错误：`ContractException`

不改变 controller 层的错误协议。

## 测试策略

### 1. 保持现有回归

至少保留并持续通过：

- `FlowableProcessRuntimeControllerTest`
- `FlowableRuntimeStartServiceTest`
- 现有审批复杂动作专项测试

### 2. 新增服务级测试

为抽出的类新增更聚焦的单元/集成测试：

- `RuntimeTaskVisibilityServiceTest`
- `RuntimeTaskAssemblerTest`
- `RuntimeApprovalSheetQueryServiceTest`

### 3. 性能回归

重构后继续复跑：

- `20 / 30 / 50 / 100` 并发阶梯压测

目标不是一次把性能打满，而是确保：

- 结构变好
- 性能不回退
- 热点更容易定位

## 非目标

这一轮明确不做：

- 全量重写所有动作执行 service
- 把每个小方法都拆成单独类
- 重写 Flowable 查询 API
- 重构前端审批页
- 一次性解决全部读链性能问题

## 预期结果

完成后应达到：

1. `FlowableProcessRuntimeService` 明显缩短，聚焦门面职责
2. `tasks.page` / `approval-sheets.page` 查询链独立成可测试服务
3. `visibleActiveTasks` 热点可以在单一服务里持续优化
4. 现有审批功能行为不变
5. 后续继续性能优化时，不必再在巨石类中加补丁
