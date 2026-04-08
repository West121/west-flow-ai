# 流程预测平台内最深版本 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把流程预测升级为平台内最深版本，覆盖精度、路径级预测、驾驶舱、自动化、AI 解释和模型预备层。

**Architecture:** 后端继续以规则/统计链作为主预测，按样本分层、场景口径和路径级计算强化预测引擎；前端扩展工作台与独立驾驶舱，承载更深的运营视角与图谱表达；AI 只负责解释和建议，不修改数值预测；模型化预测只打数据底座，不替换主链。

**Tech Stack:** Spring Boot, Flowable, Java, React, TanStack Query, Vitest, Maven

---

## Chunk 1: 预测精度 v5

### Task 1: 样本分层与异常清洗

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionSnapshotService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionResponse.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/test/java/com/westflow/processruntime/query/RuntimeProcessPredictionServiceTest.java`

- [ ] 写失败测试，覆盖会签、加签、转办、驳回、跳转和工作日/非工作日画像命中。
- [ ] 跑测试确认现状失败或断言缺失。
- [ ] 实现样本画像扩展、异常样本清洗、`p50/p75/p90` 输出。
- [ ] 跑测试确认通过。

### Task 2: 路径级预测

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionResponse.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/test/java/com/westflow/processruntime/query/RuntimeProcessPredictionServiceTest.java`

- [ ] 写失败测试，要求输出路径总剩余时长、风险路径段、预计完成点。
- [ ] 实现路径级预测字段和候选分支预计耗时。
- [ ] 跑测试确认通过。

## Chunk 2: 驾驶舱 v2/v3

### Task 3: 驾驶舱指标深化

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionAnalyticsService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessQueryFacadeService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/api/response/WorkbenchDashboardSummaryResponse.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] 写失败测试，要求 summary 返回部门风险贡献、办理人负载排行、催办效果指标。
- [ ] 实现新的 dashboard 指标计算。
- [ ] 跑测试确认通过。

### Task 4: 驾驶舱页面深化

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/frontend/src/lib/api/workbench.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/frontend/src/features/workbench/approval-prediction-dashboard.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/frontend/src/features/workbench/pages.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/frontend/src/features/workbench/pages.test.tsx`

- [ ] 写失败测试，要求驾驶舱显示新的钻取卡片和效果面板。
- [ ] 实现前端 summary 类型和页面展示。
- [ ] 跑测试确认通过。

## Chunk 3: 自动化 v2/v3

### Task 5: 自动动作治理深化

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionAutomationProperties.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionGovernanceService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionActionExecutorService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/resources/application.yml`
- Test: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/test/java/com/westflow/processruntime/query/RuntimeProcessPredictionActionExecutorServiceTest.java`

- [ ] 写失败测试，覆盖升级规则、多轮催办、节流与静默时间窗组合。
- [ ] 实现治理配置和动作策略深化。
- [ ] 跑测试确认通过。

### Task 6: 工作台与图谱的自动化反馈

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/frontend/src/features/workbench/approval-prediction-section.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/frontend/src/features/workbench/approval-sheet-graph.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/frontend/src/routes/review/player/$ticket.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/frontend/src/features/workbench/approval-sheet-graph.test.tsx`

- [ ] 写失败测试，要求图谱展示风险路径段、预计完成点和自动化建议态。
- [ ] 实现图谱和详情组件增强。
- [ ] 跑测试确认通过。

## Chunk 4: AI 解释 v4

### Task 7: AI 解释与优化建议

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionAiNarrationService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionResponse.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/test/java/com/westflow/processruntime/query/RuntimeProcessPredictionAiNarrationServiceTest.java`

- [ ] 写失败测试，覆盖瓶颈归因、催办建议和流程优化建议。
- [ ] 实现 AI 解释增强，但不改数值预测。
- [ ] 跑测试确认通过。

## Chunk 5: 模型预备层

### Task 8: 特征与评估底座

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionSnapshotService.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionEvaluationService.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/main/java/com/westflow/processruntime/api/response/PredictionEvaluationReportResponse.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/backend/src/test/java/com/westflow/processruntime/query/RuntimeProcessPredictionSnapshotServiceTest.java`

- [ ] 写失败测试，覆盖特征快照与误差评估报表。
- [ ] 实现评估服务和数据输出。
- [ ] 跑测试确认通过。

## Chunk 6: 文档与回归

### Task 9: 文档收口

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/docs/plans/2026-04-08-process-prediction-v5-release-notes.md`
- Create: `/Users/west/dev/code/west/west-flow-ai/.worktrees/codex-process-prediction-v5/docs/plans/2026-04-08-process-prediction-v5-ops-guide.md`

- [ ] 写发布说明。
- [ ] 写运维和治理说明。

### Task 10: 最终回归

**Files:**
- Test only

- [ ] 运行前端 typecheck。
- [ ] 运行前端 workbench 相关 vitest。
- [ ] 运行后端 compile。
- [ ] 运行后端预测、自动动作、真实流程关键路径测试。

