# 审批平台 P0 收口实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐审批平台 P0 缺口：主子流程前端 E2E、复杂审批动作前端 E2E、第一轮正式压测与性能基线收口。

**Architecture:** 以“浏览器端到端验证 + 压测基线 + 性能收口”三条主线并行推进。前端 E2E 负责把工作台真实交互补齐，压测主线负责给出并发数据和瓶颈，主线程负责结果收口、性能优化和最终回归。

**Tech Stack:** Playwright, Vitest, Spring Boot, Flowable, PostgreSQL, k6, Python requests

---

## Chunk 1：主子流程前端 E2E

### Task 1：父子流程浏览器自动化

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/e2e/approval-subprocess.spec.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/package.json`

- [ ] **Step 1: 准备可复用登录与发起辅助函数**
- [ ] **Step 2: 发起主流程带子流程实例**
- [ ] **Step 3: 验证父流程等待子流程**
- [ ] **Step 4: 完成子流程，验证父流程恢复**
- [ ] **Step 5: 验证列表、详情、时间轴、流程图一致性**
- [ ] **Step 6: 接入 Playwright 脚本并跑通**

## Chunk 2：复杂审批动作前端 E2E

### Task 2：协同审批和复杂动作浏览器自动化

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/e2e/approval-collaboration-and-batch.spec.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/package.json`

- [ ] **Step 1: 覆盖督办 / 会办 / 阅办 / 传阅**
- [ ] **Step 2: 覆盖批量认领 / 批量同意 / 批量驳回**
- [ ] **Step 3: 覆盖会签下加签 / 减签 / 驳回 / 退回**
- [ ] **Step 4: 验证前端动作反馈和异常提示**
- [ ] **Step 5: 跑通 Playwright**

## Chunk 3：第一轮正式压测

### Task 3：读写压测与基线报告

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/perf/k6/approval-read.js`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/perf/k6/approval-actions.js`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/perf/python/approval_perf_baseline.py`
- Create: `/Users/west/dev/code/west/west-flow-ai/docs/plans/2026-03-29-approval-platform-pressure-test-report.md`

- [ ] **Step 1: 跑 10 并发基线**
- [ ] **Step 2: 跑 50 并发中等负载**
- [ ] **Step 3: 跑 100 并发高负载**
- [ ] **Step 4: 记录 p95 / p99 / 错误率**
- [ ] **Step 5: 标记瓶颈热点并给出优化建议**

## Chunk 4：性能收口与最终回归

### Task 4：性能修复与统一验证

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/resources/db/migration/V13__approval_runtime_indexes.sql`
- Reference: `/Users/west/dev/code/west/west-flow-ai/docs/plans/2026-03-29-approval-platform-test-results.md`

- [ ] **Step 1: 优化压测暴露的读写热点**
- [ ] **Step 2: 回归后端复杂动作测试**
- [ ] **Step 3: 回归前端 E2E**
- [ ] **Step 4: 更新测试结果清单**
- [ ] **Step 5: 提交并推送**

## 多代理边界

- **Worker A：主子流程前端 E2E**
  - 只负责 `/Users/west/dev/code/west/west-flow-ai/frontend/e2e/approval-subprocess.spec.ts`
- **Worker B：复杂审批动作前端 E2E**
  - 只负责 `/Users/west/dev/code/west/west-flow-ai/frontend/e2e/approval-collaboration-and-batch.spec.ts`
- **Main Thread：压测、性能优化、集成与回归**
  - 负责脚本、后端优化、文档、脚本接线和最终验证

## 完成定义

- Playwright 至少新增 2 条审批 E2E 并稳定通过
- 10/50/100 并发压测有正式报告
- 至少完成一轮针对热点的性能优化和回归
- 测试结果文档更新到当前真实状态
