# 追加与动态构建 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于真实 `Flowable` 和平台附属结构模型，打通 `追加 + 动态构建` 的设计器、运行态、轨迹、详情和监控闭环。

**Architecture:** 主流程仍由 `Flowable` 驱动，追加和动态构建产生的附属人工任务/附属子流程统一落入 `wf_runtime_append_link`；审批详情、流程监控和轨迹统一消费附属结构树，不再各自拼接。

**Tech Stack:** Spring Boot 3.5.12、Flowable BPMN、MyBatis、React、TanStack Query/Router、Vitest、Maven

---

## Chunk 1: DSL 与设计器

### Task 1: 增加 `dynamic-builder` 节点与配置面板

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/types.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/config.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/palette.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.test.tsx`

- [ ] 写 failing test，覆盖节点模板、字段回填、校验和序列化
- [ ] 运行 `pnpm -C frontend exec vitest run src/features/workflow/designer/node-config-panel.test.tsx --reporter=verbose`
- [ ] 最小实现 `dynamic-builder` 节点和配置面板
- [ ] 再次运行测试确认通过
- [ ] 提交：`feat: 增加动态构建节点设计器配置`

### Task 2: 扩展 DSL 校验与 BPMN 映射

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessDslValidatorTest.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessDslToBpmnServiceTest.java`

- [ ] 写 failing test，覆盖 `dynamic-builder` 必填字段和 BPMN 占位节点映射
- [ ] 运行 `mvn -q -f backend/pom.xml -Dtest=ProcessDslValidatorTest,ProcessDslToBpmnServiceTest test`
- [ ] 最小实现校验与 BPMN 映射
- [ ] 再次运行测试确认通过
- [ ] 提交：`feat: 增加动态构建dsl与bpmn映射`

## Chunk 2: 运行态附属结构模型

### Task 3: 新增 `wf_runtime_append_link` 与服务层

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/resources/db/migration/V1__init.sql`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/model/RuntimeAppendLinkRecord.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/mapper/RuntimeAppendLinkMapper.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/RuntimeAppendLinkService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/service/RuntimeAppendLinkServiceTest.java`

- [ ] 写 failing test，覆盖新增、按根流程查询、状态更新
- [ ] 运行 `mvn -q -f backend/pom.xml -Dtest=RuntimeAppendLinkServiceTest test`
- [ ] 最小落表与实现 mapper/service
- [ ] 再次运行测试确认通过
- [ ] 提交：`feat: 增加追加与动态构建附属结构模型`

### Task 4: 实现人工任务追加

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableTaskActionService.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/AppendTaskRequest.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/FlowableProcessRuntimeControllerTest.java`

- [ ] 写 failing test，覆盖追加人工任务、详情可见、轨迹可见
- [ ] 运行 `mvn -q -f backend/pom.xml -Dtest=FlowableProcessRuntimeControllerTest test`
- [ ] 实现追加人工任务运行态
- [ ] 再次运行测试确认通过
- [ ] 提交：`feat: 打通人工任务追加运行态`

### Task 5: 实现附属子流程追加

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableRuntimeStartService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/service/FlowableRuntimeStartServiceTest.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/FlowableProcessRuntimeControllerTest.java`

- [ ] 写 failing test，覆盖追加子流程、父子/附属关系写入、完成与终止状态同步
- [ ] 运行相应 Maven 测试
- [ ] 最小实现附属子流程追加
- [ ] 再次运行测试确认通过
- [ ] 提交：`feat: 打通附属子流程追加运行态`

## Chunk 3: 动态构建运行态

### Task 6: 动态构建生成附属任务

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/orchestrator/service/FlowableOrchestratorRuntimeBridge.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/orchestrator/service/OrchestratorServiceTest.java`

- [ ] 写 failing test，覆盖命中 `dynamic-builder` 规则后生成附属任务
- [ ] 运行 `mvn -q -f backend/pom.xml -Dtest=OrchestratorServiceTest test`
- [ ] 实现动态构建生成附属任务
- [ ] 再次运行测试确认通过
- [ ] 提交：`feat: 打通动态构建附属任务生成`

### Task 7: 动态构建生成附属子流程

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/orchestrator/service/FlowableOrchestratorRuntimeBridge.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableRuntimeStartService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/orchestrator/service/OrchestratorServiceTest.java`

- [ ] 写 failing test，覆盖规则命中后自动拉起附属子流程
- [ ] 运行对应 Maven 测试
- [ ] 实现动态构建附属子流程
- [ ] 再次运行测试确认通过
- [ ] 提交：`feat: 打通动态构建附属子流程生成`

## Chunk 4: 详情、监控、轨迹与前端

### Task 8: 审批详情与实例监控展示附属结构树

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/api/ProcessTaskDetailResponse.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/workflowadmin/api/WorkflowInstanceDetailResponse.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/workbench.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/workflow-management.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/management-pages.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workbench/pages.test.tsx`

- [ ] 写 failing test，覆盖审批详情和监控页展示追加/动态构建结构
- [ ] 运行前端测试
- [ ] 最小实现前后端协议与展示
- [ ] 再次运行测试确认通过
- [ ] 提交：`feat: 展示追加与动态构建结构树`

### Task 9: 轨迹、终止与权限收口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeTraceStore.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/identity/service/IdentityAuthService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/identity/service/FixtureAuthService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/resources/db/migration/V1__init.sql`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/service/FlowableProcessRuntimeTraceStoreTest.java`

- [ ] 写 failing test，覆盖追加/动态构建事件、终止事件、权限校验
- [ ] 运行后端测试
- [ ] 实现轨迹和权限收口
- [ ] 再次运行测试确认通过
- [ ] 提交：`feat: 收口追加与动态构建轨迹和权限`

## Chunk 5: 全量验证与收口

### Task 10: 全量验证并整理提交

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/docs/superpowers/specs/2026-03-23-advanced-workflow-capabilities-design.md`
- Modify: `/Users/west/dev/code/west/west-flow-ai/docs/contracts/task-actions.md`

- [ ] 运行：

```bash
mvn -q -f backend/pom.xml test
pnpm -C frontend test --run
pnpm -C frontend typecheck
pnpm -C frontend lint
pnpm -C frontend build
./scripts/validate-contracts.sh
```

- [ ] 同步文档与契约
- [ ] 提交：`feat: 完成追加与动态构建真实闭环`

