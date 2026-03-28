# AI Copilot Agent/Skill/MCP 重构 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Copilot 从规则驱动的巨石编排改造为基于 Planner Agent、Executors、Tools、Skills、MCP 的分层架构，并保留现有会话、审计、确认链和工具注册体系。

**Architecture:** 新增 `plan -> route -> execute -> render` 主链。`DbAiCopilotService` 保留会话持久化与审计职责，新增 `AiPlanAgentService` 负责意图理解，新增 `AiExecutionRouter` 负责分发到知识、统计、流程、动作、MCP 执行器。平台内部确定性能力继续走 Tool，外部能力走 MCP，Skill 只做语义上下文增强。

**Tech Stack:** Spring Boot, Spring AI Alibaba, ChatClient, MCP Java client, MyBatis, PostgreSQL, React frontend, existing AI block renderer.

---

## Chunk 1: Planner 与结构化 Plan 主链

### Task 1: 定义 Plan 模型与 Planner 服务边界

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/planner/AiCopilotPlan.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/planner/AiPlanAgentService.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/planner/AiPlanAgentServiceTest.java`

- [ ] **Step 1: 写失败测试，覆盖读/写/澄清三类 plan**

```java
@Test
void shouldPlanWriteIntentForLeaveRequest() {}

@Test
void shouldPlanStatsIntentForCountQuery() {}

@Test
void shouldPlanKnowledgeIntentForHowToQuestion() {}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -f backend/pom.xml -Dtest=AiPlanAgentServiceTest test`
Expected: FAIL，类或方法不存在。

- [ ] **Step 3: 实现最小 Plan 模型和 Planner 接口**

实现：
- `intent`
- `domain`
- `executor`
- `toolCandidates`
- `arguments`
- `presentation`
- `needConfirmation`
- `confidence`

- [ ] **Step 4: 再次运行测试确认通过**

Run: `mvn -q -f backend/pom.xml -Dtest=AiPlanAgentServiceTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/ai/planner backend/src/test/java/com/westflow/ai/planner
git commit -m "feat: add ai copilot planner plan model"
```

### Task 2: 用 Planner 替换旧编排入口的第一跳

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/config/AiCopilotConfiguration.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java`

- [ ] **Step 1: 写失败测试，验证 appendMessage 先产出结构化 plan**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 注入 `AiPlanAgentService`，让旧 service 不再直接做意图判断**
- [ ] **Step 4: 运行相关测试**

Run: `mvn -q -f backend/pom.xml -Dtest=AiCopilotServiceTest,AiPlanAgentServiceTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java backend/src/main/java/com/westflow/ai/config/AiCopilotConfiguration.java backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java
git commit -m "feat: route copilot through planner service"
```

## Chunk 2: Execution Router 与 Knowledge / Workflow Executors

### Task 3: 新增执行路由器与知识执行器

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/executor/AiExecutionRouter.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/executor/AiKnowledgeExecutor.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/executor/AiKnowledgeExecutorTest.java`

- [ ] **Step 1: 写失败测试，覆盖“系统功能怎么用”“当前页面能做什么”**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现基于 Skill + feature catalog tool 的知识执行器**
- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -f backend/pom.xml -Dtest=AiKnowledgeExecutorTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/ai/executor backend/src/test/java/com/westflow/ai/executor/AiKnowledgeExecutorTest.java
git commit -m "feat: add ai knowledge executor"
```

### Task 4: 新增流程解释执行器

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/executor/AiWorkflowExecutor.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/executor/AiWorkflowExecutorTest.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`

- [ ] **Step 1: 写失败测试，覆盖“当前审批单卡在哪”“谁可以处理”**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现基于审批查询 tool + workflow-analysis skill 的执行器**
- [ ] **Step 4: 接到 execution router**
- [ ] **Step 5: 运行测试**

Run: `mvn -q -f backend/pom.xml -Dtest=AiWorkflowExecutorTest,AiCopilotServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/ai/executor/AiWorkflowExecutor.java backend/src/test/java/com/westflow/ai/executor/AiWorkflowExecutorTest.java backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java
git commit -m "feat: add ai workflow executor"
```

## Chunk 3: Stats Executor 与受控 text2sql 收口

### Task 5: 把现有 stats 链收进 `AiStatsExecutor`

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/executor/AiStatsExecutor.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/stats/AiStatsText2SqlService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/stats/ChatClientAiStatsSqlGenerator.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/executor/AiStatsExecutorTest.java`

- [ ] **Step 1: 写失败测试，覆盖 metric / stats / table / bar**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 stats executor，统一输出 block**
- [ ] **Step 4: 清理旧 `DbAiCopilotService` 里的统计特殊分支**
- [ ] **Step 5: 运行测试**

Run: `mvn -q -f backend/pom.xml -Dtest=AiStatsExecutorTest,AiCopilotServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/ai/executor/AiStatsExecutor.java backend/src/main/java/com/westflow/ai/stats backend/src/test/java/com/westflow/ai/executor/AiStatsExecutorTest.java
git commit -m "feat: add ai stats executor"
```

## Chunk 4: Action Executor 与统一写确认链

### Task 6: 新增动作执行器

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/executor/AiActionExecutor.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/executor/AiActionExecutorTest.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`

- [ ] **Step 1: 写失败测试，覆盖请假发起、认领、审批三类写动作**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 让写动作统一经过 action executor + validator + confirmation**
- [ ] **Step 4: 移除旧显式写关键词判断的主责任**
- [ ] **Step 5: 运行测试**

Run: `mvn -q -f backend/pom.xml -Dtest=AiActionExecutorTest,AiCopilotServiceTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/westflow/ai/executor/AiActionExecutor.java backend/src/test/java/com/westflow/ai/executor/AiActionExecutorTest.java backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java
git commit -m "feat: add ai action executor"
```

## Chunk 5: MCP Executor 与注册表语义整理

### Task 7: 新增 MCP 执行器

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/executor/AiMcpExecutor.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/AiMcpClientFactory.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/AiRegistryCatalogService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/executor/AiMcpExecutorTest.java`

- [ ] **Step 1: 写失败测试，覆盖“planner 选中 mcp executor 后调用外部 MCP”**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 mcp executor，并整理 mcp/tool/skill 元数据用途**
- [ ] **Step 4: 运行测试**

Run: `mvn -q -f backend/pom.xml -Dtest=AiMcpExecutorTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/ai/executor/AiMcpExecutor.java backend/src/main/java/com/westflow/ai/service/AiMcpClientFactory.java backend/src/main/java/com/westflow/ai/service/AiRegistryCatalogService.java backend/src/test/java/com/westflow/ai/executor/AiMcpExecutorTest.java
git commit -m "feat: add ai mcp executor"
```

## Chunk 6: 前端 block 与普通问答/推荐体验对齐

### Task 8: 收口前端 block 协议，确保知识/统计/动作都能统一展示

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/ai/index.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/ai/index.test.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/ai-copilot.ts`

- [ ] **Step 1: 写失败测试，覆盖 text / metric / stats / table / chart / form-preview / confirm**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 调整 block renderer 兼容新的 execution plan 输出**
- [ ] **Step 4: 运行测试**

Run: `pnpm -C frontend exec vitest run src/features/ai/index.test.tsx --reporter=verbose`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/ai/index.tsx frontend/src/features/ai/index.test.tsx frontend/src/lib/api/ai-copilot.ts
git commit -m "feat: align ai copilot blocks with planner architecture"
```

## Chunk 7: 删除旧规则与最终回归

### Task 9: 下线旧关键词规则并做最终回归

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/orchestration/AiOrchestrationPlanner.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java`

- [ ] **Step 1: 删除旧关键词主路由逻辑，仅保留兼容兜底**
- [ ] **Step 2: 跑后端 AI 专项测试**

Run: `mvn -q -f backend/pom.xml -Dtest=AiPlanAgentServiceTest,AiKnowledgeExecutorTest,AiWorkflowExecutorTest,AiStatsExecutorTest,AiActionExecutorTest,AiMcpExecutorTest,AiCopilotServiceTest test`
Expected: PASS

- [ ] **Step 3: 跑前端 AI 专项测试**

Run: `pnpm -C frontend exec vitest run src/features/ai/index.test.tsx --reporter=verbose`
Expected: PASS

- [ ] **Step 4: 跑类型检查与构建**

Run: `pnpm -C frontend typecheck && pnpm -C frontend build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/westflow/ai/orchestration/AiOrchestrationPlanner.java backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java frontend/src/features/ai/index.tsx frontend/src/features/ai/index.test.tsx frontend/src/lib/api/ai-copilot.ts
git commit -m "refactor: rebuild ai copilot orchestration with planner executors"
```

## 并行开发建议

建议拆成 5 个并行 worker，写集尽量不重叠：

1. `planner-worker`
- `backend/src/main/java/com/westflow/ai/planner/**`
- `AiCopilotConfiguration.java` 的 planner bean 由主线程最后接

2. `knowledge-workflow-worker`
- `backend/src/main/java/com/westflow/ai/executor/AiKnowledgeExecutor.java`
- `backend/src/main/java/com/westflow/ai/executor/AiWorkflowExecutor.java`

3. `stats-worker`
- `backend/src/main/java/com/westflow/ai/stats/**`
- `backend/src/main/java/com/westflow/ai/executor/AiStatsExecutor.java`

4. `action-worker`
- `backend/src/main/java/com/westflow/ai/executor/AiActionExecutor.java`
- 写确认链相关测试

5. `mcp-ui-worker`
- `backend/src/main/java/com/westflow/ai/executor/AiMcpExecutor.java`
- `AiRegistryCatalogService.java`
- `AiMcpClientFactory.java`
- `frontend/src/features/ai/**`

主线程只收口这些高冲突文件：
- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/config/AiCopilotConfiguration.java`
- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/runtime/SpringAiAlibabaCopilotRuntimeService.java`

