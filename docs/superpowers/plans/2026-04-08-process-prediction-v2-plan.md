# 流程预测后续增强 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有流程预测从“详情可看”升级到“工作台可调度、图谱可判断、解释可执行”的企业运营能力。

**Architecture:** 在保留当前确定性预测主链的前提下，按四条线增强：样本精度分层、工作台前置调度、流程图路径级风险可视化、AI 解释层 v2。后端先扩展预测样本与聚合结果，再把前端工作台和图谱消费能力补齐，最后让 AI 基于结构化预测结果生成更自然但不改动数值结论的解释与建议。

**Tech Stack:** Spring Boot, Flowable, Java 21, React, TanStack Router, TanStack Query, Vitest, DashScope/Spring AI Alibaba

---

## Chunk 1: 预测精度分层

### Task 1: 扩展预测样本维度

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeTaskDetailQueryService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeTaskAssembler.java`

- [ ] 识别当前预测可用的上下文维度：流程定义、节点、办理人、组织、业务类型、当前时间窗口。
- [ ] 为历史样本聚合增加“工作日/非工作日”与“办理人/组织”分层口径。
- [ ] 为样本不足场景建立逐级回退顺序，避免直接掉到全局样本。
- [ ] 在预测结果中补充 `sampleProfile` 或等价摘要字段，明确命中的样本层级。

### Task 2: 引入分位数和置信度细化

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionService.java`

- [ ] 计算并返回 `p50/p75` 或等价风险阈值摘要。
- [ ] 根据样本量、路径离散度、是否使用降级样本重新计算 `confidence`。
- [ ] 保证低置信度场景有明确说明，不伪装成高可信预测。

### Task 3: 为列表与图谱暴露路径级预测基础数据

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionNextNodeCandidateResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionResponse.java`

- [ ] 为候选节点返回更稳定的展示字段：风险权重、预测排序、路径置信度。
- [ ] 预留“预计超期点/预计完成点”字段，供前端图谱和时间轴消费。

## Chunk 2: 工作台前置化

### Task 4: 工作台列表风险优先调度

**Files:**
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeTaskQueryService.java`

- [ ] 将“按风险优先”变成真正的首屏策略入口，而不是单个附加筛选按钮。
- [ ] 增加“今日可能超期”“高风险待办”视图与筛选状态。
- [ ] 为列表项补充更强的预测摘要，不要求进入详情才能判断优先级。
- [ ] 验证列表筛选、排序与分页组合下的行为一致性。

### Task 5: 首页摘要卡改成运营视角

**Files:**
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeTaskQueryService.java`
- Create or Modify: processruntime summary response files as needed

- [ ] 在工作台顶部摘要中增加高风险数量、预计今日超期数量等运营指标。
- [ ] 避免与现有待办/已办数字冲突，保证层级清晰。
- [ ] 提供后端聚合结果，避免前端根据分页数据猜测。

## Chunk 3: 流程图与时间轴增强

### Task 6: 路径级高风险可视化

**Files:**
- Modify: `frontend/src/features/workbench/approval-sheet-graph.tsx`
- Modify: `frontend/src/routes/review/player/$ticket.tsx`

- [ ] 将当前“高风险节点 + 候选下一节点”增强为“高风险路径段”高亮。
- [ ] 在图谱中区分当前节点风险、预测下一步、预计完成路径，避免颜色语义混乱。
- [ ] 兼容默认 H5 模式与 `weapp` 兼容模式。

### Task 7: 时间轴接入预测阈值

**Files:**
- Modify: `frontend/src/routes/review/player/$ticket.tsx`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workbench/approval-prediction-section.tsx`

- [ ] 在时间轴上显示预计完成点或“预计超期阈值”提示。
- [ ] 在详情预测卡中同步显示路径级判断结果，避免图和卡信息割裂。
- [ ] 明确无历史样本、流程已结束、分支不确定时的降级表现。

## Chunk 4: AI 解释层 v2

### Task 8: 结构化预测解释输入

**Files:**
- Modify: `backend/src/main/java/com/westflow/ai/config/AiCopilotConfiguration.java`
- Modify: `backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionResponse.java`

- [ ] 为 AI 解释层准备稳定的结构化输入，不让模型直接推导数值结果。
- [ ] 输入应包含：风险等级、样本层级、当前停留时长、候选节点、预计完成时间、建议动作初稿。
- [ ] 保持 AI 只生成解释和建议，不改写主预测数值。

### Task 9: 接入 Copilot 解释与催办建议

**Files:**
- Modify: `backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- Modify: `frontend/src/features/workbench/approval-prediction-section.tsx`
- Modify: `frontend/src/features/workbench/pages.tsx`

- [ ] 提供“为什么这么预测”“建议先催谁/先做什么”的自然语言解释。
- [ ] 对无模型、模型失败或低置信度场景保留当前规则化解释兜底。
- [ ] 控制接口时延，确保详情页不因为 AI 解释层明显变慢。

## Chunk 5: 测试与验证

### Task 10: 后端测试

**Files:**
- Modify: `backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`
- Create or Modify: processruntime query tests as needed
- Modify: `backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java`

- [ ] 覆盖精度分层与样本回退。
- [ ] 覆盖列表风险过滤/排序。
- [ ] 覆盖路径级预测字段输出。
- [ ] 覆盖 AI 解释层成功与兜底分支。

### Task 11: 前端测试

**Files:**
- Modify: `frontend/src/features/workbench/pages.test.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-graph.test.tsx`
- Modify or Create: `frontend/src/features/workbench/approval-prediction-section.test.tsx`

- [ ] 验证工作台风险视图与筛选行为。
- [ ] 验证路径级高亮和预测阈值展示。
- [ ] 验证 AI 解释层正常态与兜底态。

### Task 12: 验证命令

- [ ] Run: `pnpm -C frontend typecheck`
- [ ] Run: `pnpm -C frontend exec vitest run src/features/workbench/pages.test.tsx src/features/workbench/approval-sheet-graph.test.tsx --reporter=verbose`
- [ ] Run: `PATH=/Users/west/dev/env/maven/apache-maven-3.9.11/bin:$PATH /Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f backend/pom.xml -DskipTests compile`
- [ ] Run: `PATH=/Users/west/dev/env/maven/apache-maven-3.9.11/bin:$PATH /Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f backend/pom.xml -Dtest=AiCopilotServiceTest,FlowableProcessRuntimeControllerTest test`

