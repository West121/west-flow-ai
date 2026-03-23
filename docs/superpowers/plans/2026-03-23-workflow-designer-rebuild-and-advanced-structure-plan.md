# 流程设计器重构与高级结构能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成流程设计器一屏三栏重构、统一选人弹窗、表单字段与自定义公式能力，并用请假流程案例驱动更深高级结构能力第一批落地。

**Architecture:** 前端把设计器改成稳定三栏布局，画布居中、左右面板独立滚动，属性区采用流程/节点双 tab；统一选人弹窗与字段/公式输入收敛成可复用组件。后端继续在现有 DSL、BPMN 转换与运行态结构能力上扩展，不引入第二套设计器协议或执行引擎。

**Tech Stack:** React, TanStack Query, React Flow, react-hook-form, Zod, Spring Boot, Flowable BPMN, MyBatis, Vitest, JUnit

---

## Task 1: 设计器三栏布局与工具栏收口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/designer-layout.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/pages.test.tsx`

- [ ] Step 1: 写设计器三栏布局 failing test，覆盖左右栏独立滚动、React Flow controls 可见、独立缩放工具栏隐藏
- [ ] Step 2: 运行 `pnpm -C frontend exec vitest run src/features/workflow/pages.test.tsx --reporter=verbose`，确认失败
- [ ] Step 3: 抽离 `designer-layout.tsx`，实现左栏节点面板、中栏画布、右栏属性面板的一屏布局
- [ ] Step 4: 把缩放/适配操作收口到 React Flow `Controls`，删除重复工具栏按钮
- [ ] Step 5: 再跑同一测试并修到通过
- [ ] Step 6: 提交 `feat: 重构流程设计器三栏布局`

## Task 2: 统一选人弹窗

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/participant-picker-dialog.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/participant-options.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.test.tsx`

- [ ] Step 1: 写 failing test，覆盖按人员/角色/部门打开弹窗、选择后回填审批节点配置
- [ ] Step 2: 实现弹窗 UI，支持搜索、标签预览、单选/多选
- [ ] Step 3: 把审批节点、抄送节点中的手输编码入口替换成弹窗入口
- [ ] Step 4: 跑 `pnpm -C frontend exec vitest run src/features/workflow/designer/node-config-panel.test.tsx --reporter=verbose`
- [ ] Step 5: 提交 `feat: 增加统一选人弹窗`

## Task 3: 表单字段与自定义公式

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/types.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/config.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/dsl.ts`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/dsl.test.ts`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/node-config-panel.test.tsx`

- [ ] Step 1: 写 failing test，覆盖 `FORM_FIELD` 与 `FORMULA` 两种选人/条件配置的 DSL 映射
- [ ] Step 2: 扩展类型，新增公式来源、表达式数据结构与字段选择元数据
- [ ] Step 3: 在节点属性面板增加字段选择器和受控公式编辑区
- [ ] Step 4: 更新 DSL 映射与 hydration
- [ ] Step 5: 跑 `pnpm -C frontend exec vitest run src/features/workflow/designer/dsl.test.ts src/features/workflow/designer/node-config-panel.test.tsx --reporter=verbose`
- [ ] Step 6: 提交 `feat: 增加表单字段与公式配置`

## Task 4: 请假流程案例升级

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/resources/db/migration/V1__init.sql`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/designer/dsl.test.ts`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/SeededProcessDefinitionCatalogTest.java`

- [ ] Step 1: 写 failing test，断言 `oa_leave` 种子流程包含排他网关、多审批节点、字段/公式相关配置
- [ ] Step 2: 升级 `oa_leave` DSL 与 BPMN 种子
- [ ] Step 3: 确认绑定与表单键仍然可发布可发起
- [ ] Step 4: 跑 `mvn -q -f backend/pom.xml -Dtest=SeededProcessDefinitionCatalogTest test`
- [ ] Step 5: 提交 `feat: 升级请假流程案例`

## Task 5: 后端 DSL 校验与 BPMN 映射扩展

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslValidator.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processdef/service/ProcessDslToBpmnService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessDslValidatorTest.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processdef/service/ProcessDslToBpmnServiceTest.java`

- [ ] Step 1: 写 failing test，覆盖表单字段、公式、排他网关增强配置的校验与 BPMN 输出
- [ ] Step 2: 扩展 DSL 校验规则
- [ ] Step 3: 扩展 BPMN 映射所需扩展属性
- [ ] Step 4: 跑 `mvn -q -f backend/pom.xml -Dtest=ProcessDslValidatorTest,ProcessDslToBpmnServiceTest test`
- [ ] Step 5: 提交 `feat: 增加字段公式dsl与bpmn映射`

## Task 6: 更深主子流程编排第一批

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/ProcessLinkService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/workflowadmin/service/WorkflowManagementService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/FlowableProcessRuntimeControllerTest.java`

- [ ] Step 1: 写 failing test，覆盖更深父子层级与 resume/join 策略预留字段输出
- [ ] Step 2: 扩展主子流程结构输出与监控详情聚合
- [ ] Step 3: 保持现有运行态兼容，不改已有已通过能力
- [ ] Step 4: 跑相关 controller/service 测试
- [ ] Step 5: 提交 `feat: 增加更深主子流程编排支撑`

## Task 7: 更复杂动态构建规则第一批

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/append/DynamicBuildAppendRuntimeService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableDynamicBuilderDelegate.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/service/append/DynamicBuildAppendRuntimeServiceTest.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/service/FlowableDynamicBuilderDelegateTest.java`

- [ ] Step 1: 写 failing test，覆盖规则驱动/模板驱动两种 dynamic-builder 模式的结构输出
- [ ] Step 2: 扩展动态构建规则模型
- [ ] Step 3: 保持附属任务与附属子流程仍复用现有运行态结构
- [ ] Step 4: 跑动态构建专项测试
- [ ] Step 5: 提交 `feat: 增加复杂动态构建规则`

## Task 8: 更复杂包容分支策略第一批

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/inclusive-gateway-section.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/processruntime/api/FlowableProcessRuntimeControllerTest.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/workflow/management-pages.test.tsx`

- [ ] Step 1: 写 failing test，覆盖分支优先级、默认分支、最少命中数量等第一批策略展示
- [ ] Step 2: 在运行态命中结果里补策略字段
- [ ] Step 3: 在详情与监控展示策略信息
- [ ] Step 4: 跑前后端相关测试
- [ ] Step 5: 提交 `feat: 增加复杂包容分支策略`

## Task 9: 总体验证与收口

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/docs/superpowers/specs/2026-03-23-workflow-designer-rebuild-and-advanced-structure-design.md`

- [ ] Step 1: 跑后端专项回归
- [ ] Step 2: 跑前端设计器与页面回归
- [ ] Step 3: 跑 `pnpm -C frontend typecheck`
- [ ] Step 4: 跑 `pnpm -C frontend lint`
- [ ] Step 5: 跑 `pnpm -C frontend build`
- [ ] Step 6: 跑 `./scripts/validate-contracts.sh`
- [ ] Step 7: 提交 `feat: 完成设计器重构与高级结构能力第一批`

