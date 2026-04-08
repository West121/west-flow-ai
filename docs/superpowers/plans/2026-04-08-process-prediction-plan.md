# 流程预测功能 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为审批详情和流程回顾提供企业可用的流程预测能力，返回预计完成时间、剩余时长、超期风险、下一节点候选和预测依据。

**Architecture:** 在后端新增 `RuntimeProcessPredictionService`，基于 Flowable 历史实例和当前详情数据计算确定性预测结果，并挂入 `ProcessTaskDetailResponse`。前端新增独立预测面板，展示核心预测字段和解释摘要，不把数值预测依赖到 AI。

**Tech Stack:** Spring Boot, Flowable, Java 21, React, TanStack Query, Vitest

---

## Chunk 1: 后端响应模型与预测服务

### Task 1: 新增预测响应模型

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionResponse.java`
- Create: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessPredictionNextNodeCandidateResponse.java`

- [ ] 定义预测响应记录类型
- [ ] 包含预计完成时间、剩余时长、风险等级、置信度、样本数、依据摘要、候选节点

### Task 2: 实现预测服务

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/query/RuntimeProcessPredictionService.java`

- [ ] 基于历史实例实现剩余时长中位数计算
- [ ] 基于历史当前节点时长实现超期风险计算
- [ ] 基于历史转移实现下一节点候选统计
- [ ] 提供“样本不足/实例已完成”兜底

### Task 3: 接入详情响应

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/api/response/ProcessTaskDetailResponse.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/query/RuntimeTaskDetailQueryService.java`

- [ ] 在详情响应中增加 `prediction`
- [ ] 在详情查询中调用预测服务

## Chunk 2: 前端展示

### Task 4: 扩展 workbench 类型

**Files:**
- Modify: `frontend/src/lib/api/workbench.ts`

- [ ] 增加 prediction 类型和字段映射

### Task 5: 新增预测面板组件

**Files:**
- Create: `frontend/src/features/workbench/approval-prediction-section.tsx`

- [ ] 展示预计完成时间、剩余时长、风险等级、置信度
- [ ] 展示候选节点和预测依据
- [ ] 支持“样本不足”和“已完成”态

### Task 6: 接入审批详情与回顾页

**Files:**
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/routes/review/player/$ticket.tsx`

- [ ] 在审批详情概览页加入预测区块
- [ ] 在 H5 回顾页加入预测区块

## Chunk 3: 测试与验证

### Task 7: 后端测试

**Files:**
- Modify: `backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`
- Create or Modify: processruntime query tests as needed

- [ ] 验证详情接口返回 prediction
- [ ] 验证样本不足和完成态

### Task 8: 前端测试

**Files:**
- Create: `frontend/src/features/workbench/approval-prediction-section.test.tsx`
- Modify: `frontend/src/features/workbench/pages.test.tsx`

- [ ] 验证正常预测渲染
- [ ] 验证样本不足/已完成态

### Task 9: 验证命令

- [ ] Run: `pnpm -C frontend typecheck`
- [ ] Run: `pnpm -C frontend exec vitest run src/features/workbench/approval-prediction-section.test.tsx src/features/workbench/pages.test.tsx --reporter=verbose`
- [ ] Run: `PATH=/usr/local/bin:$PATH /Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test`
- [ ] Run: `PATH=/usr/local/bin:$PATH /Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f backend/pom.xml -DskipTests compile`
