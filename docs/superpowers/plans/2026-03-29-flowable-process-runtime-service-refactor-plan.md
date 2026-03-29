# FlowableProcessRuntimeService Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `FlowableProcessRuntimeService` 从巨石运行态服务重构为“门面 + 查询服务 + 可见性服务 + 组装器 + trace 查询”的中等拆分结构，并在新结构上继续深挖 `tasks.page` 与 `approval-sheets.page` 的性能瓶颈。

**Architecture:** 保留 `FlowableProcessRuntimeService` 作为门面和事务边界，把任务分页、审批单分页、任务可见性、任务 DTO 组装、trace 查询、动作辅助逻辑抽到 5-6 个服务中。优先迁移最重的查询链和组装链，动作执行主链暂时不大拆。自有表查询优先沿用或收敛到 MyBatis-Plus 风格，复杂场景再保留 `JdbcTemplate`。

**Tech Stack:** Spring Boot, Flowable, MyBatis-Plus/MyBatis Mapper, PostgreSQL, existing pressure test scripts, JUnit.

---

## Chunk 1: 设计落地与边界收口

### Task 1: 补齐设计文档与目标边界

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/docs/plans/2026-03-29-flowable-process-runtime-service-refactor-design.md`
- Create: `/Users/west/dev/code/west/west-flow-ai/docs/superpowers/plans/2026-03-29-flowable-process-runtime-service-refactor-plan.md`

- [ ] **Step 1: 写清门面保留职责、第一批拆分类和非目标**
- [ ] **Step 2: 自查文档是否明确“中等拆分、不做过度服务化”**
- [ ] **Step 3: Commit**

```bash
git add docs/plans/2026-03-29-flowable-process-runtime-service-refactor-design.md docs/superpowers/plans/2026-03-29-flowable-process-runtime-service-refactor-plan.md
git commit -m "docs: add runtime service refactor design"
```

## Chunk 2: 抽出任务可见性服务

### Task 2: 新增 `RuntimeTaskVisibilityService`

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskVisibilityService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/RuntimeAppendLinkService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/mapper/RuntimeAppendLinkMapper.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖批量可见任务查询主链不回退**
- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest#shouldSupportBatchClaimReadCompleteAndRejectOnRealFlowableRuntime test`
Expected: FAIL with missing class or wiring error after introducing service skeleton.

- [ ] **Step 3: 创建 `RuntimeTaskVisibilityService`，迁移以下职责**
  - `visibleActiveTasks(...)`
  - `prefetchRunningAppendLinks(...)`
  - `isVisibleTask(...)`
  - `isBlockedByPendingAppendStructures(...)`
  - `blockingDynamicBuilderNodeIds(...)`
- [ ] **Step 4: 保留现有分段耗时日志，并把 request-scope cache 作为参数对象或局部上下文传递**
- [ ] **Step 5: 在 `FlowableProcessRuntimeService` 中改为调用新服务**
- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest#shouldSupportBatchClaimReadCompleteAndRejectOnRealFlowableRuntime test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskVisibilityService.java backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/main/java/com/westflow/processruntime/service/RuntimeAppendLinkService.java backend/src/main/java/com/westflow/processruntime/mapper/RuntimeAppendLinkMapper.java backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java
git commit -m "refactor: extract runtime task visibility service"
```

## Chunk 3: 抽出任务组装器与任务分页查询服务

### Task 3: 新增 `RuntimeTaskAssembler`

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskAssembler.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖任务列表 DTO 仍包含 businessType / nodeName / status**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 抽出以下职责到 `RuntimeTaskAssembler`**
  - `toTaskListItem(...)`
  - `candidateUsers(...)`
  - `candidateGroups(...)`
  - `resolveAssignmentMode(...)`
  - 与 task list item 组装直接相关的预取逻辑
- [ ] **Step 4: 保持 `resolveTaskKind/resolveTaskStatus` 先仍由门面或 visibility 支撑，不过度搬迁**
- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest#shouldSupportBatchClaimReadCompleteAndRejectOnRealFlowableRuntime test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskAssembler.java backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java
git commit -m "refactor: extract runtime task assembler"
```

### Task 4: 新增 `RuntimeTaskQueryService`

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskQueryService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖 `tasks.page` 默认快速路径与关键词过滤路径**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 抽出 `page(PageRequest)` 相关主链到 `RuntimeTaskQueryService`**
  - 默认分页快速路径
  - 全量 enrich + keyword filter 路径
  - page 日志打点
- [ ] **Step 4: `FlowableProcessRuntimeService.page(...)` 改为 facade 调用**
- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskQueryService.java backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java
git commit -m "refactor: extract runtime task query service"
```

## Chunk 4: 抽出审批单分页与 trace 查询

### Task 5: 新增 `RuntimeApprovalSheetQueryService`

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/RuntimeApprovalSheetQueryService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖 `approval-sheets.page` TODO 快速路径和 initiated/done/copied 路径**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 抽出以下职责**
  - `pageApprovalSheets(...)`
  - `pageTodoApprovalSheetsFast(...)`
  - `buildInitiatedApprovalSheets(...)`
  - `buildDoneApprovalSheets(...)`
  - `buildCopiedApprovalSheets(...)`
  - `toApprovalSheetFromTask(...)`
- [ ] **Step 4: 保持现有 `ApprovalSheetListItemResponse` 结构不变**
- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/RuntimeApprovalSheetQueryService.java backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java
git commit -m "refactor: extract runtime approval sheet query service"
```

### Task 6: 新增 `RuntimeTraceQueryService`

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/RuntimeTraceQueryService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖详情 trace / instance events / automation trace / notification records 仍可查询**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 抽出 trace 查询聚合辅助**
- [ ] **Step 4: 让门面保留 detail API，但把底层查询交给新服务**
- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/RuntimeTraceQueryService.java backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java
git commit -m "refactor: extract runtime trace query service"
```

## Chunk 5: 性能继续深挖与压测复跑

### Task 7: 在新结构上继续优化 `tasks.page` / `approval-sheets.page`

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskVisibilityService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskAssembler.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/docs/plans/2026-03-29-approval-platform-pressure-test-results.md`

- [ ] **Step 1: 针对 `taskLocalVariables / resolveTaskKind` 继续打点并确认热点**
- [ ] **Step 2: 优化不必要的本地变量读取**
- [ ] **Step 3: 复跑 `20 / 30 / 50 / 100` 阶梯压测**

Run: `python3 backend/perf/python/approval_perf_baseline.py --read-concurrency 20 --read-iterations 20 --action-concurrency 1 --action-iterations 1`
Expected: 成功输出 `tasks.page` 和 `approval-sheets.page` 指标。

- [ ] **Step 4: 把结果写回压测文档**
- [ ] **Step 5: 运行回归测试**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest#shouldSupportBatchClaimReadCompleteAndRejectOnRealFlowableRuntime test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskVisibilityService.java backend/src/main/java/com/westflow/processruntime/service/RuntimeTaskAssembler.java docs/plans/2026-03-29-approval-platform-pressure-test-results.md
git commit -m "perf: optimize runtime task visibility after refactor"
```

## Chunk 6: 收口与验证

### Task 8: 最终回归与整理

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 检查 `FlowableProcessRuntimeService` 是否已收缩到门面职责**
- [ ] **Step 2: 运行后端专项回归**

Run: `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest,FlowableRuntimeStartServiceTest test`
Expected: PASS

- [ ] **Step 3: 如有必要补充 facade 层整理**
- [ ] **Step 4: 提交最终收口**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/test/java/com/westflow/processruntime/api/controller/FlowableProcessRuntimeControllerTest.java backend/src/test/java/com/westflow/processruntime/service/FlowableRuntimeStartServiceTest.java
git commit -m "refactor: simplify flowable process runtime facade"
```

Plan complete and saved to `docs/superpowers/plans/2026-03-29-flowable-process-runtime-service-refactor-plan.md`. Ready to execute?
