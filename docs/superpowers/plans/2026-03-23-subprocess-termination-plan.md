# 主子流程与终止高级策略 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于真实 `Flowable` 打通主子流程与终止高级策略，使设计器、DSL、运行态、审批详情和实例监控形成完整闭环。

**Architecture:** 主子流程统一基于 `callActivity` 实现，平台额外引入 `wf_process_link` 承载父子实例关系；终止高级策略由运行态服务统一执行，并通过平台轨迹和监控视图暴露作用域、原因和级联结果。

**Tech Stack:** Spring Boot 3.5.12、Flowable BPMN、MyBatis、React、TanStack Router、Vitest、Maven

---

## Chunk 1: DSL 与 BPMN 定义层

### Task 1: 为设计器和 DSL 增加 `subprocess` 节点

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/palette.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/types.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/config.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.test.tsx`

- [ ] **Step 1: 写前端 failing test**

覆盖：

- `subprocess` 节点出现在调色板
- 子流程节点配置支持：
  - `calledProcessKey`
  - `calledVersionPolicy`
  - `calledVersion`
  - `businessBindingMode`
  - `terminatePolicy`
  - `childFinishPolicy`

- [ ] **Step 2: 运行前端测试并确认失败**

Run:

```bash
pnpm -C frontend exec vitest run src/features/workflow/designer/node-config-panel.test.tsx --reporter=verbose
```

- [ ] **Step 3: 最小实现设计器配置与回填**

- [ ] **Step 4: 再次运行前端测试并确认通过**

Run:

```bash
pnpm -C frontend exec vitest run src/features/workflow/designer/node-config-panel.test.tsx --reporter=verbose
```

- [ ] **Step 5: 提交这一小步**

```bash
git add frontend/src/features/workflow/designer
git commit -m "feat: 增加子流程节点设计器配置"
```

### Task 2: 扩展 DSL 校验与 BPMN 转换

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessDslValidatorTest.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessDslToBpmnServiceTest.java`

- [ ] **Step 1: 写 DSL failing tests**

覆盖：

- `subprocess` 节点缺少 `calledProcessKey` 时失败
- `FIXED_VERSION` 缺少版本时失败
- BPMN 生成 `callActivity`
- 子流程扩展属性正确写入

- [ ] **Step 2: 运行后端测试并确认失败**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=ProcessDslValidatorTest,ProcessDslToBpmnServiceTest test
```

- [ ] **Step 3: 最小实现 DSL 校验与 BPMN `callActivity` 转换**

- [ ] **Step 4: 再次运行测试并确认通过**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=ProcessDslValidatorTest,ProcessDslToBpmnServiceTest test
```

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/java/com/westflow/processdef backend/src/test/java/com/westflow/processdef
git commit -m "feat: 增加主子流程dsl与bpmn转换"
```

## Chunk 2: 运行态与数据库闭环

### Task 3: 建立父子实例关联模型

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/resources/db/migration/V1__init.sql`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/model/ProcessLinkRecord.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/mapper/ProcessLinkMapper.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/ProcessLinkService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/service/ProcessLinkServiceTest.java`

- [ ] **Step 1: 写父子实例关联 failing test**

覆盖：

- 启动子流程时写入 `wf_process_link`
- 可按父实例查出子实例
- 状态可更新为 `RUNNING / FINISHED / TERMINATED`

- [ ] **Step 2: 运行测试并确认失败**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=ProcessLinkServiceTest test
```

- [ ] **Step 3: 落表并实现 mapper/service**

- [ ] **Step 4: 再次运行测试并确认通过**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=ProcessLinkServiceTest test
```

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/resources/db/migration/V1__init.sql backend/src/main/java/com/westflow/processruntime backend/src/test/java/com/westflow/processruntime/service/ProcessLinkServiceTest.java
git commit -m "feat: 增加主子流程实例关联模型"
```

### Task 4: 启动主子流程并回填实例关系

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableRuntimeStartService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/flowable/FlowableEngineFacade.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/service/FlowableRuntimeStartServiceTest.java`

- [ ] **Step 1: 写主子流程启动 failing test**

覆盖：

- 父流程进入 `subprocess` 节点会拉起子流程实例
- `wf_process_link` 被正确写入
- 子流程完成后父流程按 `childFinishPolicy` 继续或终止

- [ ] **Step 2: 运行测试并确认失败**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=FlowableRuntimeStartServiceTest test
```

- [ ] **Step 3: 实现 callActivity 运行态回填与策略处理**

- [ ] **Step 4: 再次运行测试并确认通过**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=FlowableRuntimeStartServiceTest test
```

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/java/com/westflow/processruntime backend/src/main/java/com/westflow/flowable backend/src/test/java/com/westflow/processruntime/service/FlowableRuntimeStartServiceTest.java
git commit -m "feat: 打通主子流程运行态启动"
```

## Chunk 3: 终止高级策略

### Task 5: 增加实例级终止范围与级联策略

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableTaskActionService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/ProcessRuntimeController.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/TerminateProcessInstanceRequest.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/FlowableProcessRuntimeControllerTest.java`

- [ ] **Step 1: 写终止策略 failing test**

覆盖：

- 终止当前子流程
- 终止当前实例
- 终止根流程并级联终止所有子流程
- 终止原因和终止范围被记录

- [ ] **Step 2: 运行测试并确认失败**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test
```

- [ ] **Step 3: 实现终止接口与运行态策略**

- [ ] **Step 4: 再次运行测试并确认通过**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test
```

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/java/com/westflow/processruntime backend/src/test/java/com/westflow/processruntime/api/FlowableProcessRuntimeControllerTest.java
git commit -m "feat: 增加主子流程终止高级策略"
```

### Task 6: 把终止事件接入轨迹与监控

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeTraceStore.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/workflowadmin/service/WorkflowManagementService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/service/FlowableProcessRuntimeTraceStoreTest.java`

- [ ] **Step 1: 写轨迹与监控 failing test**

覆盖：

- 审批轨迹可见“进入子流程 / 子流程完成 / 子流程终止 / 级联终止”
- 实例监控可查到父子流程树与终止状态

- [ ] **Step 2: 运行测试并确认失败**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeTraceStoreTest test
```

- [ ] **Step 3: 实现轨迹和监控视图补充**

- [ ] **Step 4: 再次运行测试并确认通过**

Run:

```bash
mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeTraceStoreTest test
```

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/java/com/westflow/processruntime backend/src/main/java/com/westflow/workflowadmin backend/src/test/java/com/westflow/processruntime/service/FlowableProcessRuntimeTraceStoreTest.java
git commit -m "feat: 增加主子流程轨迹与监控视图"
```

## Chunk 4: 前端详情与监控闭环

### Task 7: 在审批详情和实例监控中展示主子流程树

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/workbench.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/workflow-management.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/management-pages.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.test.tsx`

- [ ] **Step 1: 写前端 failing test**

覆盖：

- 审批详情显示主流程/子流程关联卡片
- 实例监控显示主子流程树
- 终止后显示终止范围和终止原因

- [ ] **Step 2: 运行前端测试并确认失败**

Run:

```bash
pnpm -C frontend exec vitest run src/features/workbench/pages.test.tsx --reporter=verbose
```

- [ ] **Step 3: 最小实现详情与监控展示**

- [ ] **Step 4: 再次运行测试并确认通过**

Run:

```bash
pnpm -C frontend exec vitest run src/features/workbench/pages.test.tsx --reporter=verbose
```

- [ ] **Step 5: 提交这一小步**

```bash
git add frontend/src/lib/api frontend/src/features/workbench frontend/src/features/workflow
git commit -m "feat: 增加主子流程详情与监控展示"
```

## Chunk 5: 全量验证与文档回写

### Task 8: 完成联调、验证与文档收口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`
- Reference: `/Users/west/dev/code/west/west-flow-ai/docs/superpowers/specs/2026-03-23-advanced-workflow-capabilities-design.md`
- Reference: `/Users/west/dev/code/west/west-flow-ai/docs/superpowers/plans/2026-03-23-subprocess-termination-plan.md`

- [ ] **Step 1: 运行后端全量验证**

Run:

```bash
mvn -q -f backend/pom.xml test
```

- [ ] **Step 2: 运行前端与契约验证**

Run:

```bash
pnpm -C frontend test --run
pnpm -C frontend typecheck
pnpm -C frontend lint
pnpm -C frontend build
./scripts/validate-contracts.sh
```

- [ ] **Step 3: 回写总设计文档中的阶段状态**

- [ ] **Step 4: 提交整阶段实现**

```bash
git add backend frontend docs
git commit -m "feat: 打通主子流程与终止高级策略"
```
