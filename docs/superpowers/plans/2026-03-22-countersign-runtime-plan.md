# Phase 5 会签策略真实闭环 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将审批节点的顺序会签、并行会签、或签、票签全部接到真实 `Flowable` 运行态，并打通设计器配置、任务推进、审批详情展示和测试闭环。

**Architecture:** 后端基于真实 `Flowable` 多实例用户任务实现顺序/并行会签，在平台层新增 `FlowableCountersignService` 与任务组扩展表来承载或签、票签、自动结束剩余任务和票权聚合；前端复用现有设计器、任务详情页和审批单详情页，只补会签配置、会签进度卡片和成员级轨迹展示。

**Tech Stack:** Spring Boot 3.4, Java 21, Flowable 7 BPMN Engine, MyBatis, PostgreSQL/H2, Sa-Token, React, React Hook Form, Zod, TanStack Query, React Flow, pnpm, Maven

---

## 文件结构

### 后端新增/重构

- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableCountersignService.java`
- Create: `backend/src/main/java/com/westflow/processruntime/model/TaskGroupRecord.java`
- Create: `backend/src/main/java/com/westflow/processruntime/model/TaskGroupMemberRecord.java`
- Create: `backend/src/main/java/com/westflow/processruntime/model/TaskVoteSnapshotRecord.java`
- Create: `backend/src/main/java/com/westflow/processruntime/mapper/TaskGroupMapper.java`
- Create: `backend/src/main/java/com/westflow/processruntime/mapper/TaskGroupMemberMapper.java`
- Create: `backend/src/main/java/com/westflow/processruntime/mapper/TaskVoteSnapshotMapper.java`
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableTaskActionService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`

### 后端测试

- Create: `backend/src/test/java/com/westflow/processruntime/service/FlowableCountersignServiceTest.java`
- Create: `backend/src/test/java/com/westflow/processruntime/api/FlowableCountersignRuntimeControllerTest.java`
- Modify: `backend/src/test/java/com/westflow/processdef/service/ProcessDslValidatorTest.java`
- Modify: `backend/src/test/java/com/westflow/processdef/service/ProcessDslToBpmnServiceTest.java`

### 前端新增/重构

- Modify: `frontend/src/features/workflow/designer/node-config-panel.tsx`
- Modify: `frontend/src/features/workflow/designer/config.ts`
- Modify: `frontend/src/features/workflow/designer/dsl.ts`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-helpers.ts`
- Modify: `frontend/src/lib/api/workbench.ts`

### 前端测试

- Modify: `frontend/src/features/workflow/pages.test.tsx`
- Modify: `frontend/src/features/workbench/pages.test.tsx`

---

## Chunk 1: DSL 与设计器配置

### Task 1: 扩展审批节点 DSL 校验

**Files:**
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Modify: `backend/src/test/java/com/westflow/processdef/service/ProcessDslValidatorTest.java`

- [ ] **Step 1: 写失败测试**

补充以下失败用例：
- `SEQUENTIAL` 少于 2 个处理人
- `PARALLEL` 少于 2 个处理人
- `OR_SIGN` 未开启自动结束剩余任务
- `VOTE` 未配置阈值
- `VOTE` 缺少成员权重

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=ProcessDslValidatorTest test`

- [ ] **Step 3: 写最小实现**

在审批节点校验里补：
- `approvalMode`
- `voteRule`
- `reapprovePolicy`
- `autoFinishRemaining`

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -Dtest=ProcessDslValidatorTest test`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java backend/src/test/java/com/westflow/processdef/service/ProcessDslValidatorTest.java
git commit -m "feat: 增加会签dsl校验"
```

### Task 2: 让设计器支持会签模式与票签配置

**Files:**
- Modify: `frontend/src/features/workflow/designer/node-config-panel.tsx`
- Modify: `frontend/src/features/workflow/designer/config.ts`
- Modify: `frontend/src/features/workflow/designer/dsl.ts`
- Modify: `frontend/src/features/workflow/pages.test.tsx`

- [ ] **Step 1: 写失败测试**

覆盖：
- 会签模式切换
- 票签阈值输入
- 权重列表保存
- 自动结束剩余任务开关

- [ ] **Step 2: 运行测试确认失败**

Run: `pnpm --dir frontend test --run src/features/workflow/pages.test.tsx`

- [ ] **Step 3: 写最小实现**

补审批节点属性：
- `SINGLE / SEQUENTIAL / PARALLEL / OR_SIGN / VOTE`
- 成员列表
- 阈值与权重
- 重审策略

- [ ] **Step 4: 运行测试确认通过**

Run: `pnpm --dir frontend test --run src/features/workflow/pages.test.tsx`

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/workflow/designer/node-config-panel.tsx frontend/src/features/workflow/designer/config.ts frontend/src/features/workflow/designer/dsl.ts frontend/src/features/workflow/pages.test.tsx
git commit -m "feat: 增加会签设计器配置"
```

---

## Chunk 2: 顺序会签与并行会签

### Task 3: 建立会签任务组平台表与 Mapper

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/model/TaskGroupRecord.java`
- Create: `backend/src/main/java/com/westflow/processruntime/model/TaskGroupMemberRecord.java`
- Create: `backend/src/main/java/com/westflow/processruntime/model/TaskVoteSnapshotRecord.java`
- Create: `backend/src/main/java/com/westflow/processruntime/mapper/TaskGroupMapper.java`
- Create: `backend/src/main/java/com/westflow/processruntime/mapper/TaskGroupMemberMapper.java`
- Create: `backend/src/main/java/com/westflow/processruntime/mapper/TaskVoteSnapshotMapper.java`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`

- [ ] **Step 1: 写失败测试**

创建最小数据库读写测试，验证：
- 任务组可插入
- 成员可按顺序查询
- 票签快照可更新

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=FlowableCountersignServiceTest test`

- [ ] **Step 3: 写最小实现**

只补表结构和 Mapper，不写复杂业务逻辑。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -Dtest=FlowableCountersignServiceTest test`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V1__init.sql backend/src/main/java/com/westflow/processruntime/model backend/src/main/java/com/westflow/processruntime/mapper
git commit -m "feat: 增加会签任务组存储模型"
```

### Task 4: 实现顺序会签与并行会签运行态

**Files:**
- Create: `backend/src/main/java/com/westflow/processruntime/service/FlowableCountersignService.java`
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableTaskActionService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Create: `backend/src/test/java/com/westflow/processruntime/service/FlowableCountersignServiceTest.java`
- Create: `backend/src/test/java/com/westflow/processruntime/api/FlowableCountersignRuntimeControllerTest.java`
- Modify: `backend/src/test/java/com/westflow/processdef/service/ProcessDslToBpmnServiceTest.java`

- [ ] **Step 1: 写失败测试**

覆盖：
- 顺序会签按顺序生成任务
- 并行会签同时生成任务
- 全员完成后才推进
- 详情能返回会签组和成员信息

- [ ] **Step 2: 运行测试确认失败**

Run:
- `mvn -f backend/pom.xml -Dtest=ProcessDslToBpmnServiceTest,FlowableCountersignServiceTest,FlowableCountersignRuntimeControllerTest test`

- [ ] **Step 3: 写最小实现**

要求：
- 顺序/并行建立在真实 Flowable 多实例用户任务上
- 平台表记录任务组和成员
- `complete` 动作能识别会签节点

- [ ] **Step 4: 运行测试确认通过**

Run:
- `mvn -f backend/pom.xml -Dtest=ProcessDslToBpmnServiceTest,FlowableCountersignServiceTest,FlowableCountersignRuntimeControllerTest test`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/FlowableCountersignService.java backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java backend/src/main/java/com/westflow/processruntime/service/FlowableTaskActionService.java backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/test/java/com/westflow/processruntime/service/FlowableCountersignServiceTest.java backend/src/test/java/com/westflow/processruntime/api/FlowableCountersignRuntimeControllerTest.java backend/src/test/java/com/westflow/processdef/service/ProcessDslToBpmnServiceTest.java
git commit -m "feat: 打通顺序与并行会签运行态"
```

---

## Chunk 3: 或签与票签

### Task 5: 实现或签自动结束剩余任务

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableCountersignService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `backend/src/test/java/com/westflow/processruntime/service/FlowableCountersignServiceTest.java`
- Modify: `backend/src/test/java/com/westflow/processruntime/api/FlowableCountersignRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试**

覆盖：
- 任意一人通过后立即推进
- 剩余未处理任务自动结束
- 轨迹里能看到自动结束原因

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=FlowableCountersignServiceTest,FlowableCountersignRuntimeControllerTest test`

- [ ] **Step 3: 写最小实现**

只实现：
- `OR_SIGN`
- 自动结束剩余成员任务
- 事件原因 `AUTO_FINISHED_BY_OR_SIGN`

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -Dtest=FlowableCountersignServiceTest,FlowableCountersignRuntimeControllerTest test`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/FlowableCountersignService.java backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/test/java/com/westflow/processruntime/service/FlowableCountersignServiceTest.java backend/src/test/java/com/westflow/processruntime/api/FlowableCountersignRuntimeControllerTest.java
git commit -m "feat: 支持或签自动结束剩余任务"
```

### Task 6: 实现票签权重聚合与阈值决议

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableCountersignService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `backend/src/test/java/com/westflow/processruntime/service/FlowableCountersignServiceTest.java`
- Modify: `backend/src/test/java/com/westflow/processruntime/api/FlowableCountersignRuntimeControllerTest.java`

- [ ] **Step 1: 写失败测试**

覆盖：
- 通过票权重累计达到阈值后推进
- 拒绝票权重达到阈值后拒绝
- 剩余未处理任务自动结束
- 票签快照正确记录

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=FlowableCountersignServiceTest,FlowableCountersignRuntimeControllerTest test`

- [ ] **Step 3: 写最小实现**

要求：
- 平台层更新 `wf_task_vote_snapshot`
- 平台层判断通过/拒绝阈值
- 命中阈值后统一结束剩余任务并写轨迹

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -Dtest=FlowableCountersignServiceTest,FlowableCountersignRuntimeControllerTest test`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/FlowableCountersignService.java backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/test/java/com/westflow/processruntime/service/FlowableCountersignServiceTest.java backend/src/test/java/com/westflow/processruntime/api/FlowableCountersignRuntimeControllerTest.java
git commit -m "feat: 支持票签阈值聚合决议"
```

---

## Chunk 4: 详情展示与重审策略

### Task 7: 让任务详情和审批单详情展示会签进度

**Files:**
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workbench/approval-sheet-helpers.ts`
- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `frontend/src/features/workbench/pages.test.tsx`

- [ ] **Step 1: 写失败测试**

覆盖：
- 任务详情展示会签进度卡片
- 审批单详情展示成员级轨迹
- 票签模式显示权重和累计结果

- [ ] **Step 2: 运行测试确认失败**

Run: `pnpm --dir frontend test --run src/features/workbench/pages.test.tsx`

- [ ] **Step 3: 写最小实现**

补：
- 会签进度卡片
- 成员明细时间线
- 权重和阈值摘要

- [ ] **Step 4: 运行测试确认通过**

Run: `pnpm --dir frontend test --run src/features/workbench/pages.test.tsx`

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/workbench/pages.tsx frontend/src/features/workbench/approval-sheet-helpers.ts frontend/src/lib/api/workbench.ts frontend/src/features/workbench/pages.test.tsx
git commit -m "feat: 展示会签进度与轨迹"
```

### Task 8: 驳回/退回后重新进入会签节点策略

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableCountersignService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `backend/src/test/java/com/westflow/processruntime/service/FlowableCountersignServiceTest.java`

- [ ] **Step 1: 写失败测试**

覆盖：
- `RESTART_ALL`
- `CONTINUE_PROGRESS`

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=FlowableCountersignServiceTest test`

- [ ] **Step 3: 写最小实现**

要求：
- 退回/驳回重新进入会签节点时，按配置重建任务组或恢复进度

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -Dtest=FlowableCountersignServiceTest test`

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/processruntime/service/FlowableCountersignService.java backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java backend/src/test/java/com/westflow/processruntime/service/FlowableCountersignServiceTest.java
git commit -m "feat: 支持会签节点重审策略"
```

---

## Chunk 5: 全量回归与收尾

### Task 9: 全量验证与文档同步

**Files:**
- Modify: `docs/contracts/task-actions.md`
- Modify: `docs/contracts/dsl-bpmn-mapping.md`

- [ ] **Step 1: 同步契约文档**

补：
- `SEQUENTIAL`
- `PARALLEL`
- `OR_SIGN`
- `VOTE`
- 会签组与成员轨迹字段

- [ ] **Step 2: 运行后端全量测试**

Run: `mvn -f backend/pom.xml test`
Expected: PASS

- [ ] **Step 3: 运行前端验证**

Run:
- `pnpm --dir frontend typecheck`
- `pnpm --dir frontend test --run`
- `pnpm --dir frontend build`

Expected: PASS

- [ ] **Step 4: 运行契约检查**

Run: `./scripts/validate-contracts.sh`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add docs/contracts/task-actions.md docs/contracts/dsl-bpmn-mapping.md
git commit -m "docs: 同步会签策略契约"
```

### Task 10: Phase 5 完成提交

**Files:**
- Modify: 本阶段所有已改文件

- [ ] **Step 1: 检查暂存区只包含 Phase 5 相关文件**

Run: `git status --short`

- [ ] **Step 2: 创建阶段完成提交**

```bash
git add backend frontend docs
git commit -m "feat: 打通会签策略真实闭环"
```

- [ ] **Step 3: 记录验证结果**

在交付说明中写清：
- 顺序会签
- 并行会签
- 或签
- 票签
- 当前剩余未做的高级能力
