# 流程预测功能设计

**目标**

在现有审批详情与流程回顾基础上，增加企业可用的流程预测能力，提供：
- 预计完成时间
- 剩余审批时长
- 超期风险等级
- 下一节点候选
- 预测依据说明

并保持接口稳定、响应足够快，不把核心预测完全交给大模型。

## 设计结论

采用 `历史统计主预测 + 候选路径分析 + AI 解释预留` 的混合方案。

- 核心预测由后端确定性逻辑完成
  - 基于同流程历史实例统计
  - 基于当前节点历史任务耗时
  - 基于历史“当前节点 -> 下一节点”转移频次
- AI 不参与主预测数值计算
  - 避免结果漂移
  - 避免详情接口变慢
- AI 解释层先预留结构化字段
  - 当前先返回确定性解释文案
  - 后续可接入 Copilot 做更自然的解释

## 用户可见能力

在审批详情与回顾页新增“流程预测”区块，展示：

- `预计完成时间`
- `剩余时长`
- `超期风险`
- `预测置信度`
- `下一节点候选`
- `预测依据`

如果历史样本不足，返回：
- 样本不足提示
- 候选节点仍可展示
- 预测置信度降为低

## 数据来源

复用当前流程详情链已有数据：

- 当前实例：
  - `taskTrace`
  - `instanceEvents`
  - `flowNodes`
  - `flowEdges`
  - `businessData`
  - `formData`
- 历史样本：
  - Flowable `HistoricTaskInstance`
  - Flowable `HistoricProcessInstance`

## 核心预测逻辑

### 1. 剩余时长预测

优先使用“同流程、同当前节点”的历史剩余时长中位数：

- 对最近 N 个已完成实例
- 找到这些实例中“命中过当前节点”的任务
- 计算该任务开始/接收时间到实例结束时间的间隔
- 取中位数作为 `remainingDurationMinutes`

如果样本不足，降级：

- 使用同节点任务处理时长中位数
- 叠加候选后续节点时长中位数

### 2. 预计完成时间

- `predictedFinishTime = now + remainingDurationMinutes`

### 3. 超期风险

按当前节点在历史中的处理分布做风险分级：

- `LOW`
  - 当前停留时长 <= 历史 p50
- `MEDIUM`
  - 当前停留时长 > p50 且 <= p75
- `HIGH`
  - 当前停留时长 > p75

若存在明确 SLA 元数据，则优先结合 SLA 阈值判断。

### 4. 置信度

由历史样本量与路径稳定性共同决定：

- `HIGH`
  - 样本 >= 20，且下一节点集中度高
- `MEDIUM`
  - 样本 >= 8
- `LOW`
  - 样本 < 8 或分支离散

### 5. 下一节点候选

使用历史实例中“当前节点后的下一个人工/关键节点”转移统计：

- 输出最多 3 个候选节点
- 展示：
  - 节点 ID
  - 节点名称
  - 命中次数
  - 概率

如果历史不足，则退化为 DSL 图上的直接下游候选。

## 接口设计

在 `ProcessTaskDetailResponse` 增加：

- `prediction`

新增响应模型：

- `ProcessPredictionResponse`
- `ProcessPredictionNextNodeCandidateResponse`

字段建议：

- `predictedFinishTime`
- `remainingDurationMinutes`
- `overdueRiskLevel`
- `confidence`
- `historicalSampleSize`
- `basisSummary`
- `topDelayReasons`
- `nextNodeCandidates`

## 后端落点

新增服务：

- `RuntimeProcessPredictionService`

职责：

- 拉取历史实例样本
- 计算剩余时长中位数
- 计算超期风险
- 统计下一节点候选
- 生成解释摘要

接入点：

- `RuntimeTaskDetailQueryService.buildDetailResponse(...)`

## 前端展示

展示位置：

- 审批详情 `概览` 页签
- `review/player/$ticket`

新增组件：

- `ApprovalPredictionSection`

内容结构：

- 左侧关键预测数字
- 右侧风险/置信度 badge
- 下方候选节点列表
- 底部预测依据说明

## 错误处理

- 没有足够历史样本：
  - 不报错
  - 显示“历史样本不足”
- 已结束实例：
  - 预测区显示“流程已结束”
- 当前没有活动节点：
  - 只展示历史完成概况，不展示剩余时长

## 测试策略

后端：

- 单元测试
  - 中位数计算
  - 风险等级计算
  - 候选节点统计
- 集成测试
  - 详情接口返回 prediction

前端：

- 组件渲染测试
  - 正常预测
  - 样本不足
  - 流程已完成

## 后续扩展

- 接入 Copilot，让 AI 用 `prediction` 数据生成更自然的解释
- 接入真实 SLA 配置中心
- 接入按组织/审批人/业务类型分层模型
