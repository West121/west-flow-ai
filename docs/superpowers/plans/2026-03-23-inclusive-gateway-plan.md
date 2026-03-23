# 包容分支 Implementation Plan

> **Goal:** 基于真实 `Flowable inclusive gateway` 打通包容分支的设计器、DSL、BPMN、轨迹与详情展示闭环。

## Chunk 1: 设计器、DSL、BPMN

### Task 1: 增加 `inclusive` 节点与网关方向配置

- 修改设计器节点类型、调色板、节点配置面板
- 为 `parallel` 和 `inclusive` 都增加 `gatewayDirection`
- 补前端单测

### Task 2: 扩展 DSL 映射

- `inclusive -> inclusive_split / inclusive_join`
- `parallel -> parallel_split / parallel_join`
- 补 DSL round-trip 测试

### Task 3: 扩展 DSL 校验与 BPMN 转换

- `inclusive_split / inclusive_join` 成对校验
- 生成 `InclusiveGateway`
- 补 `ProcessDslValidatorTest` 和 `ProcessDslToBpmnServiceTest`

## Chunk 2: 运行态轨迹与详情

### Task 4: 记录包容分支命中信息

- 在真实运行态中补充分支命中实例事件
- 在审批详情和实例监控中返回命中摘要

### Task 5: 前端展示命中结果

- 审批详情展示包容分支命中列表
- 实例监控展示包容分支命中列表

## 验证

- `mvn -q -f backend/pom.xml -Dtest=ProcessDslValidatorTest,ProcessDslToBpmnServiceTest test`
- `pnpm -C frontend exec vitest run src/features/workflow/designer/node-config-panel.test.tsx src/features/workflow/designer/dsl.test.ts --reporter=verbose`
- 后续第二批再跑全量验证
