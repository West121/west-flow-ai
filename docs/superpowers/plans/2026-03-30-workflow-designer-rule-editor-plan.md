# Workflow Designer Rule Editor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将流程设计器条件分支统一收敛为 Monaco 弹窗规则编辑器，并以 Aviator DSL 作为唯一规则模型。

**Architecture:** 前端在连线属性中只保留规则摘要和“编辑规则”入口，弹窗使用 Monaco 承载 DSL 编辑、补全和错误标记；后端提供规则元数据和校验接口，并继续用 Aviator 作为唯一执行引擎。旧的 `FIELD / EXPRESSION / FORMULA` 条件会在编辑态映射到统一公式文本，保存时统一写回 `FORMULA`。

**Tech Stack:** React 19, Vite, Monaco Editor, React Hook Form, Spring Boot 3.5, Aviator

---

## Chunk 1: 后端规则元数据与校验

### Task 1: 定义规则元数据响应模型

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/api/ProcessRuleMetadataResponse.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/api/ProcessRuleValidationRequest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/api/ProcessRuleValidationResponse.java`

- [ ] Step 1: 写 DTO，覆盖字段上下文、聚合上下文、流程/节点上下文、函数列表、校验结果。
- [ ] Step 2: `mvn -q -f backend/pom.xml -DskipTests compile`
- [ ] Step 3: 确认编译通过。

### Task 2: 提供规则元数据服务

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessRuleMetadataService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/support/WorkflowFormulaEvaluator.java`

- [ ] Step 1: 从 `WorkflowFormulaEvaluator` 暴露受控函数元数据。
- [ ] Step 2: 在新 service 中组装主表字段、子表聚合、流程/节点上下文和函数信息。
- [ ] Step 3: `mvn -q -f backend/pom.xml -DskipTests compile`

### Task 3: 提供规则校验服务

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessRuleValidationService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/support/WorkflowFormulaEvaluator.java`

- [ ] Step 1: 新增编译校验能力，返回错误消息和尽量准确的位置。
- [ ] Step 2: 对合法表达式给出标准化摘要。
- [ ] Step 3: 为校验异常构造稳定的前端消费结构。
- [ ] Step 4: `mvn -q -f backend/pom.xml -DskipTests compile`

### Task 4: 暴露控制器接口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/api/ProcessDefinitionController.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/api/ProcessDefinitionControllerTest.java`

- [ ] Step 1: 增加规则元数据和规则校验接口。
- [ ] Step 2: 写 controller 测试覆盖正常和异常路径。
- [ ] Step 3: 运行 `mvn -q -f backend/pom.xml -Dtest=ProcessDefinitionControllerTest test`

## Chunk 2: DSL 兼容与统一模型

### Task 5: 统一前端连线规则模型

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/config.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/types.ts`

- [ ] Step 1: 增加旧条件到公式文本的映射函数。
- [ ] Step 2: 新定义连线规则摘要与公式文本结构。
- [ ] Step 3: 写最小单元测试验证 `FIELD / EXPRESSION / FORMULA` 都能映射到统一文本。

### Task 6: 统一后端出 BPMN 的条件读法

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessDslToBpmnServiceTest.java`

- [ ] Step 1: 保持旧 DSL 兼容，但允许统一写回 `FORMULA`。
- [ ] Step 2: 补充测试覆盖旧类型兼容和统一类型输出。
- [ ] Step 3: 运行 `mvn -q -f backend/pom.xml -Dtest=ProcessDslToBpmnServiceTest test`

## Chunk 3: 前端 Monaco 规则编辑器

### Task 7: 引入 Monaco 并创建弹窗壳子

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/package.json`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/rule-editor-dialog.tsx`

- [ ] Step 1: 添加 `@monaco-editor/react` 依赖。
- [ ] Step 2: 新建弹窗组件，先渲染标题、编辑器、上下文区和构件区。
- [ ] Step 3: `pnpm -C frontend typecheck`

### Task 8: 连线属性改成摘要 + 弹窗入口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/expression-tools.tsx`

- [ ] Step 1: 删除当前连线属性中的条件类型和小表单。
- [ ] Step 2: 改成规则摘要 + `编辑规则`。
- [ ] Step 3: 默认分支保持只显示默认分支提示。
- [ ] Step 4: 跑设计器专项测试并修复断言。

### Task 9: 接入 Monaco 补全与上下文

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/rule-editor-dialog.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/rule-editor-monaco.ts`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/process-rule.ts`

- [ ] Step 1: 拉取规则元数据接口。
- [ ] Step 2: 注册补全项、hover 和 snippet。
- [ ] Step 3: 左下展示上下文，右下展示规则构件和后端函数。
- [ ] Step 4: 运行 `pnpm -C frontend typecheck`

### Task 10: 接入校验与错误标记

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/rule-editor-dialog.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/process-rule.ts`

- [ ] Step 1: 对编辑器内容做 debounce 校验。
- [ ] Step 2: 把后端错误映射成 Monaco markers。
- [ ] Step 3: 保存时写回统一 `FORMULA` 条件。
- [ ] Step 4: 运行 `pnpm -C frontend exec vitest run src/features/workflow/designer/node-config-panel.test.tsx --reporter=verbose`

## Chunk 4: 测试与回归

### Task 11: 前端测试补齐

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.test.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/store.test.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.test.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/rule-editor-dialog.test.tsx`

- [ ] Step 1: 覆盖规则摘要、弹窗打开、切换连线、默认分支、错误标记。
- [ ] Step 2: 运行 `pnpm -C frontend exec vitest run src/features/workflow/designer/store.test.ts src/features/workflow/designer/node-config-panel.test.tsx src/features/workflow/designer/rule-editor-dialog.test.tsx src/features/workflow/pages.test.tsx --reporter=verbose`

### Task 12: 后端测试补齐

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessRuleMetadataServiceTest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessRuleValidationServiceTest.java`

- [ ] Step 1: 覆盖函数元数据、上下文变量、成功校验、失败校验。
- [ ] Step 2: 运行 `mvn -q -f backend/pom.xml -Dtest=ProcessRuleMetadataServiceTest,ProcessRuleValidationServiceTest,ProcessDefinitionControllerTest test`

### Task 13: 最终验证

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/docs/plans/2026-03-30-workflow-designer-rule-editor-design.md`

- [ ] Step 1: `pnpm -C frontend typecheck`
- [ ] Step 2: `pnpm -C frontend lint`
- [ ] Step 3: `mvn -q -f backend/pom.xml -DskipTests compile`
- [ ] Step 4: 更新设计文档中的“实现状态”。
- [ ] Step 5: 整理提交。

