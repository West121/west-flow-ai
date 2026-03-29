# 审批平台压测结果报告

## 测试范围

本轮正式压测聚焦审批平台的核心读写路径，验证在不同并发下的响应时间、稳定性和失败率：

- `tasks.page`
- `approval-sheets.page`
- `tasks.detail`
- `oa.leaves.create`
- `tasks.claim`
- `tasks.complete`

其中：

- 读路径重点关注列表、详情、审批单分页接口
- 写路径重点关注请假发起、认领、同意等核心动作

## 测试环境

- 仓库：`/Users/west/dev/code/west/west-flow-ai`
- 压测对象：当前本地审批平台服务
- 运行环境：本机开发环境
- 并发方式：按接口分别做 `20 / 30 / 50 / 100` 并发阶梯压测
- 判定依据：
  - 平均响应时间
  - `p95`
  - 成功率 / 失败率
  - 最大响应时间
- 压测脚本：
  - `/Users/west/dev/code/west/west-flow-ai/backend/perf/python/approval_perf_baseline.py`

## 本轮收口

压测前先做了两类收口：

1. **压测脚本收口**
   - 显式禁用环境代理继承，避免本机代理把请求转发到 `127.0.0.1:1082`
   - 将超时和请求异常记录为失败样本，不再让压测脚本直接中断

2. **读路径低风险优化**
   - `tasks.page` 默认查询场景增加快速分页路径，只对当前页任务做 enrich
   - `approval-sheets.page` 的 `TODO` 视图默认场景增加快速分页路径，不再通过 `page(Integer.MAX_VALUE)` 先构造全量待办
   - 为列表读链补了请求内缓存，减少以下重复查询：
     - `taskLocalVariables`
     - `taskKind` 的 BPMN 解析
     - `runtimeAppendLinkService.listByParentInstanceId`
     - 动态追加阻塞节点计算
     - `sourceTaskId` 活跃性判断

## 压测结果

### 20 并发

| 接口 | avg | p95 | max | 结论 |
| --- | ---: | ---: | ---: | --- |
| `tasks.page` | 2076.3ms | 2176.0ms | 2209.2ms | 读链可用，但仍偏慢 |
| `approval-sheets.page` | 1750.2ms | 1834.5ms | 1837.3ms | 已明显优于前一轮 |
| `tasks.detail` | 237.6ms | 334.0ms | 334.9ms | 详情查询稳定 |
| `oa.leaves.create` | 76.3ms | 119.3ms | 119.3ms | 发起接口稳定 |
| `tasks.claim` | 31.0ms | 41.8ms | 41.8ms | 认领接口稳定 |
| `tasks.complete` | 83.5ms | 96.5ms | 96.5ms | 办理接口稳定 |

### 30 并发

| 接口 | success | avg | p95 | max | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| `tasks.page` | `30 / 30` | 2770.8ms | 2929.7ms | 3017.0ms | 进入 3s 内，退化可控 |
| `approval-sheets.page` | `30 / 30` | 2740.7ms | 2921.8ms | 2928.9ms | 与 `tasks.page` 基本持平 |
| `tasks.detail` | `30 / 30` | 329.4ms | 463.7ms | 471.3ms | 详情稳定 |
| `oa.leaves.create` | `2 / 2` | 41.3ms | 55.1ms | 55.1ms | 写链稳定 |
| `tasks.claim` | `2 / 2` | 16.8ms | 18.6ms | 18.6ms | 写链稳定 |
| `tasks.complete` | `2 / 2` | 52.5ms | 53.1ms | 53.1ms | 写链稳定 |

### 50 并发

| 接口 | success | avg | p95 | max | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| `tasks.page` | `50 / 50` | 4522.3ms | 4810.1ms | 4936.5ms | 从 10s 级下降到 5s 内 |
| `approval-sheets.page` | `50 / 50` | 4997.2ms | 5233.6ms | 5283.3ms | 仍偏慢，但已明显收敛 |
| `tasks.detail` | `50 / 50` | 528.1ms | 773.0ms | 783.8ms | 详情可承受 |
| `oa.leaves.create` | `2 / 2` | 38.9ms | 47.3ms | 47.3ms | 写链稳定 |
| `tasks.claim` | `2 / 2` | 18.3ms | 19.0ms | 19.0ms | 写链稳定 |
| `tasks.complete` | `2 / 2` | 56.8ms | 62.4ms | 62.4ms | 写链稳定 |

### 100 并发

| 接口 | success | avg | p95 | max | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| `tasks.page` | `100 / 100` | 9576.7ms | 10252.5ms | 10567.5ms | 仍是主要瓶颈，但较上一轮下降约 43% |
| `approval-sheets.page` | `100 / 100` | 9894.6ms | 10474.2ms | 10896.8ms | 错误率已归零，耗时下降约 49% |
| `tasks.detail` | `100 / 100` | 1141.5ms | 1686.9ms | 1915.8ms | 详情仍可承受 |
| `oa.leaves.create` | `2 / 2` | 39.0ms | 51.0ms | 51.0ms | 写链稳定 |
| `tasks.claim` | `2 / 2` | 18.8ms | 19.4ms | 19.4ms | 写链稳定 |
| `tasks.complete` | `2 / 2` | 53.3ms | 53.6ms | 53.6ms | 写链稳定 |

## 压测结果判断

### 读路径

- 主要瓶颈仍然集中在：
  - `tasks.page`
  - `approval-sheets.page`
- 请求内缓存后，这两个入口的成功率已经在 `20 / 30 / 50 / 100` 四档全部恢复到 `100%`
- `approval-sheets.page` 的下降更明显，说明前一轮确实有实例投影与追加结构判断的重复查询放大
- `tasks.page` 在高并发下仍是最重入口之一，说明剩余热点已更多落在 Flowable 任务查询本身、实例变量读取和流程定义解析，而不是纯 Java 层重复计算

### 写路径

- `oa.leaves.create`
- `tasks.claim`
- `tasks.complete`

这三条写路径在 `10 / 50 / 100` 并发下都保持稳定，没有出现错误率抬升，当前不是主要性能瓶颈

## 瓶颈判断

综合本轮压测数据，当前瓶颈优先级如下：

1. `审批单分页 / 任务分页查询`
   - 读路径入口仍然最重
   - 但这一轮已从“出现真实失败”回到“全部成功但耗时偏高”
2. `审批详情及关联数据聚合`
   - 虽然单次详情较快，但在列表联动、详情联动、统计联动时可能形成放大效应
3. `数据库读压力与索引命中`
   - 需要重点排查分页查询、关联查询、排序字段和过滤条件
4. `服务端线程与连接资源`
   - 100 并发下虽然已不再失败，但两个读接口仍在 10s 左右，说明线程/连接资源与 Flowable 服务调用放大仍值得继续盯

## 优化优先级

### P0

- 继续深挖 `tasks.page`
- 继续深挖 `approval-sheets.page`
- 把热点从 Java 层请求内缓存继续下沉到更靠近 Flowable/SQL 的查询层
- 补充更多只读场景指标采样，并核对 Flowable 查询路径与排序字段

### P1

- 优化 `tasks.detail` 的关联查询和投影字段
- 减少列表页和详情页的重复查询
- 评估缓存或预聚合是否能缓解高频只读场景

### P2

- 对写路径继续做稳定性回归
- 增强监控与慢查询采样
- 将压测结果纳入回归基线

## 下一轮压测建议

建议下一轮按以下顺序推进：

1. 先做 SQL / 索引层分析
   - 重点看 `tasks.page` 和 `approval-sheets.page`
   - 输出慢查询、执行计划、索引命中率

2. 继续做 20 / 30 / 50 / 100 并发阶梯压测
   - 观察从 30 并发到 100 并发的退化曲线
   - 重点盯 `tasks.page` 与 `approval-sheets.page` 是否还能继续压缩到 5s 内

3. 拆分读写压测
   - 只压列表
   - 只压详情
   - 只压审批单分页
   - 只压写动作

4. 加入服务端指标采样
   - 请求耗时分布
   - DB 慢查询
   - 连接池占用
   - 线程池排队情况

5. 再做 50 / 100 并发复测
   - 验证优化前后差异
   - 以 `approval-sheets.page` 错误率归零、以及两个读接口 `p95` 是否继续显著下降作为主要判定

## 结论

当前审批平台的写路径已经稳定，读路径也已经从“100 并发出现真实失败”收敛到“四档全部成功”。  
但 `tasks.page` 和 `approval-sheets.page` 在 `100` 并发下仍然处于 `10s` 左右，性能还没到上线即放心的程度。当前可以认为本轮完成了：

- 第一轮正式阶梯压测
- 一轮低风险分页优化
- 一轮请求内缓存优化

下一步仍应继续深挖 `tasks.page` 和 `approval-sheets.page` 的 Flowable 查询与变量读取热点，把高并发 `p95` 再往下压。
