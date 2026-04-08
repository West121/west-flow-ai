# 流程预测 v3 实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把流程预测升级成可运营、可解释、可触发自动动作的企业级能力，并为未来模型化预测打好基础。

**Architecture:** 保持规则/统计主预测链路不变，在后端增加样本治理、运营聚合、自动动作和 AI 解释增强，在前端增加运营驾驶舱、路径级高亮和更强的风险调度入口。模型化预测暂不替换主链，只补数据基础。

**Tech Stack:** Spring Boot, Flowable, Java 21, React, TanStack Query, Vitest

---

## Chunk 1: 预测精度 v3

### Task 1: 扩展预测响应模型与样本画像

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionNextNodeCandidateResponse.java`
- Modify: `frontend/src/lib/api/workbench.ts`

- [ ] 增加 `p90`、`sampleTier`、`outlierFilteredSampleSize`、`workingDayProfile`、`organizationProfile`
- [ ] 前端类型同步

### Task 2: 实现样本分层与异常值清洗

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionService.java`

- [ ] 增加按办理人、组织、工作日/节假日的样本命中链路
- [ ] 实现异常值清洗和样本衰减
- [ ] 增加 `p90` 与更细置信度判断

### Task 3: 详情和列表接入新画像

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeTaskAssembler.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeTaskDetailQueryService.java`
- Modify: `frontend/src/features/workbench/approval-prediction-section.tsx`

- [ ] 列表与详情都返回并展示新画像
- [ ] 详情卡中展示更具体的样本口径

## Chunk 2: 预测运营驾驶舱

### Task 4: 新增驾驶舱聚合后端

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionAnalyticsService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessQueryFacadeService.java`
- Modify: related response DTOs

- [ ] 聚合高风险数量、今日预计超期、风险分布、超期趋势、瓶颈排行
- [ ] 提供首页消费接口

### Task 5: 首页接入运营面板

**Files:**
- Modify: `frontend/src/features/workbench/pages.tsx`
- Create: `frontend/src/features/workbench/approval-prediction-dashboard.tsx`

- [ ] 首页新增预测运营面板
- [ ] 支持风险分布、超期趋势、节点瓶颈排行

## Chunk 3: AI 解释 v3

### Task 6: 扩展 AI 解释结构

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionAiNarrationService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionResponse.java`

- [ ] 输出 `narrativeExplanation`
- [ ] 输出 `bottleneckAttribution`
- [ ] 输出 `optimizationSuggestions`
- [ ] 失败时稳定回退到规则说明

### Task 7: 前端渲染 AI 解释 v3

**Files:**
- Modify: `frontend/src/features/workbench/approval-prediction-section.tsx`

- [ ] 渲染瓶颈归因
- [ ] 渲染流程优化建议

## Chunk 4: 自动动作

### Task 8: 自动动作后端服务

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/service/RuntimeProcessPredictionAutomationService.java`
- Modify: relevant notification/urge integration points

- [ ] 高风险自动催办
- [ ] SLA 临近自动提醒
- [ ] 下一审批人预提醒
- [ ] 记录审计事件和通知结果

### Task 9: 前端展示自动动作结果

**Files:**
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workbench/approval-prediction-section.tsx`

- [ ] 展示预测触发的通知/催办状态
- [ ] 区分建议动作与已执行动作

## Chunk 5: 模型化预测预备层

### Task 10: 训练/评估预备数据层

**Files:**
- Create: prediction sample snapshot / aggregate migrations and services as needed
- Modify: prediction analytics services

- [ ] 增加样本快照和聚合结构
- [ ] 记录用于未来模型化的特征字段
- [ ] 不替换当前主预测链

## Chunk 6: 测试与验证

### Task 11: 后端测试

**Files:**
- Modify: `backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`
- Add: service tests as needed

- [ ] 覆盖样本分层命中
- [ ] 覆盖异常值清洗
- [ ] 覆盖驾驶舱聚合
- [ ] 覆盖自动催办与提醒
- [ ] 覆盖 AI 解释成功与回退

### Task 12: 前端测试

**Files:**
- Modify: `frontend/src/features/workbench/pages.test.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-graph.test.tsx`
- Add: dashboard tests as needed

- [ ] 覆盖运营面板
- [ ] 覆盖路径级高亮
- [ ] 覆盖 AI 解释 v3 与自动动作展示

### Task 13: 验证命令

- [ ] Run: `corepack pnpm -C frontend typecheck`
- [ ] Run: `corepack pnpm -C frontend exec vitest run src/features/workbench/pages.test.tsx src/features/workbench/approval-sheet-graph.test.tsx --reporter=verbose`
- [ ] Run: `/Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f backend/pom.xml -DskipTests compile`
- [ ] Run: `/Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test`
