# AI Copilot Agent/Skill/MCP/Tool 重构设计

## 背景

当前 Copilot 已经具备以下基础能力：

- Spring AI Alibaba `SupervisorAgent` / `LlmRoutingAgent`
- 平台内部 `ToolRegistry` 与 `AiToolExecutionService`
- 数据库驱动的 `Agent / Skill / Tool / MCP` 注册表
- 外部 MCP 客户端工厂
- AI 会话、消息、工具调用、审计持久化

但实际编排仍然主要由以下两处规则驱动：

- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/orchestration/AiOrchestrationPlanner.java`

导致的问题：

- 普通问答、统计分析、流程发起、审批动作混在同一服务里
- 关键词规则持续膨胀，无法覆盖自然表达
- Spring AI Alibaba 的 Agent/Skill/MCP 基础设施没有真正成为主编排路径
- 新功能接入成本高，且容易互相回归

## 目标

将 Copilot 从“规则驱动的巨石编排 service”重构为“Agent 负责理解、Tool/MCP 负责执行、Skill 负责语义提示、Validator 负责安全”的分层架构。

这次重构目标是：

1. 统一意图识别，不再依赖大量关键词 if/else
2. 为不同任务分配最合适的实现载体：Agent / Tool / MCP / Skill
3. 保留现有会话、审计、工具执行和注册表资产
4. 支持普通问答、功能推荐、使用说明、统计分析、流程发起、审批动作、外部 MCP 查询
5. 支持多代理并行开发，不互相踩文件边界

## 设计原则

### 1. AI 负责理解，系统负责约束

AI 负责：

- 判断用户意图
- 提取结构化参数
- 选择更适合的展示方式建议

系统负责：

- 流程/表单/动作存在性校验
- SQL 只读校验与白名单约束
- 写操作确认
- 工具执行与审计

### 2. 不把所有能力都做成 MCP

MCP 只用于：

- 外部系统
- 远程知识库
- 第三方工具服务

平台内部确定性能力仍应优先使用本地 Tool。

### 3. Skill 只负责“提示协议”和“上下文增强”

Skill 不直接执行数据库或业务动作，只负责：

- 领域术语
- 页面上下文
- 功能推荐知识
- 解释风格
- 参数抽取提示

### 4. 渐进迁移，不一次推翻

现有 `ToolRegistry`、`McpClientFactory`、会话存储、审计、注册表可继续保留。

## 功能到实现载体的分配

### 普通问答 / 功能推荐 / 怎么用

推荐载体：`Agent + Skill + Tool`

- Agent：判断这是知识型请求还是动作型请求
- Skill：提供页面说明、功能解释、推荐话术、系统术语
- Tool：提供功能目录、页面能力、菜单/入口元数据

适用问题：

- “这个系统能做什么？”
- “请假流程怎么发起？”
- “当前页面适合做什么？”

### 统计分析 / 图表 / 报表

推荐载体：`Agent + Tool`

- Agent：识别统计对象、维度、时间范围、展示意图
- Tool：执行受控 text2sql
- 系统：校验 SQL，只允许只读与白名单表

适用问题：

- “系统有多少个角色？”
- “按部门统计用户，图表展示”
- “最近 7 天请假趋势”

### 流程解释 / 审批轨迹 / 卡点分析

推荐载体：`Agent + Tool + Skill`

- Agent：理解用户要解释什么
- Tool：查询审批单详情、轨迹、动作、候选人
- Skill：提供流程解释语气、轨迹归纳方式、页面上下文

适用问题：

- “当前审批单卡在哪？”
- “谁可以处理这条审批？”
- “为什么不能拿回？”

### 流程发起 / 审批 / 认领 / 转办 / 加签

推荐载体：`Agent + Tool`

- Agent：识别用户要执行的动作、业务类型、参数
- Tool：执行确定性动作
- 系统：强制确认、鉴权、业务校验

适用问题：

- “帮我请 5 天事假”
- “替我认领这个任务”
- “给李四加签”

### 外部系统查询

推荐载体：`Agent + MCP`

- Agent：判断问题是否应调用外部系统
- MCP：负责外部能力接入

适用问题：

- 第三方知识库查询
- 外部业务平台查询
- 外部文档/搜索工具

## 目标架构

### 1. Planner Agent

新增统一规划层：`AiPlanAgentService`

输入：

- 用户自然语言
- 页面路由
- 页面上下文标签
- 当前用户能力

输出结构化 plan：

```json
{
  "intent": "read|write|clarify",
  "domain": "system|workflow|oa|plm|generic",
  "executor": "knowledge|stats|workflow|action|mcp",
  "toolCandidates": ["stats.query.sql"],
  "arguments": {},
  "presentation": "metric|stats|table|bar|line|pie|form-preview|confirm|text",
  "needConfirmation": false,
  "confidence": 0.92
}
```

Planner Agent 只做理解和规划，不直接执行业务。

### 2. Execution Router

新增 `AiExecutionRouter`，按 plan 分发到执行器：

- `AiKnowledgeExecutor`
- `AiStatsExecutor`
- `AiWorkflowExecutor`
- `AiActionExecutor`
- `AiMcpExecutor`

### 3. Executors

#### AiKnowledgeExecutor

负责：

- 普通对话
- 功能推荐
- 页面说明
- 使用指导

执行来源：

- Skill 提示上下文
- 功能目录 Tool
- 页面元数据 Tool

#### AiStatsExecutor

负责：

- 统计问答
- 图表
- 表格
- 指标卡

执行来源：

- Text2SQL Tool
- SQL 校验器
- 统一结果 block 渲染协议

#### AiWorkflowExecutor

负责：

- 审批解释
- 轨迹解读
- 卡点分析
- 候选人说明

执行来源：

- 平台内部审批查询 Tool
- 流程解释 Skill

#### AiActionExecutor

负责：

- 流程发起
- 审批动作
- 认领、转办、委派、加签、减签

执行来源：

- 平台内部写 Tool
- 强校验器
- 二次确认

#### AiMcpExecutor

负责：

- 外部 MCP 工具调用
- 外部知识和第三方系统整合

执行来源：

- `AiMcpClientFactory`
- 数据库 MCP 注册表

### 4. Skills 体系

建议将 Skill 明确分层：

- `planner-skill`
  - 提示如何做意图规划
- `page-context-skill`
  - 当前页面能做什么
- `knowledge-skill`
  - 系统功能推荐与使用说明
- `workflow-analysis-skill`
  - 流程与审批解释
- `process-start-skill`
  - 流程发起参数抽取
- `stats-query-skill`
  - 统计问法与展示建议

### 5. Tool 体系

平台内部确定性能力建议全部保留为 Tool，例如：

- `feature.catalog.query`
- `page.capability.query`
- `stats.query.sql`
- `process.start`
- `task.claim`
- `task.complete`
- `task.reject`
- `approval.detail.query`
- `approval.trace.query`

### 6. MCP 体系

MCP 只做外部能力扩展，不负责替代内部 Tool：

- 外部搜索
- 外部文档
- 第三方业务系统
- 外部知识库

## 当前代码的保留与替换

### 保留

- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/tool/AiToolRegistry.java`
- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/AiToolExecutionService.java`
- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/AiRegistryCatalogService.java`
- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/AiMcpClientFactory.java`
- 会话、消息、审计 mapper 与模型

### 降级职责

- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
  - 只保留：会话、消息、工具调用、审计、调用 planner、调用 router
  - 移除：大部分意图判断和业务分支

### 最终替换

- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/orchestration/AiOrchestrationPlanner.java`
  - 当前关键词分流逻辑最终会被 planner agent 取代

## 迁移顺序

### Phase 1：独立 Planner

先新增：

- `AiPlanAgentService`
- `AiCopilotPlan`

让系统先统一输出结构化 plan，但暂时仍复用现有执行链。

### Phase 2：拆 Execution Router 和 5 个 Executor

让 `DbAiCopilotService` 不再直接处理业务。

### Phase 3：Skill 正规化

把当前技能内容整理为 Planner / Executor 两层语义资产。

### Phase 4：删除旧规则

清理：

- `isExplicitProcessStartIntent`
- `hasExplicitTaskHandleIntent`
- 旧统计/查询/写操作 fallback 规则

## 安全边界

### 写操作

- 必须经过 plan
- 必须命中合法 Tool
- 必须校验流程/表单/动作存在
- 必须有确认链

### Text2SQL

- 只允许 `SELECT`
- 白名单表/字段
- 白名单 join
- 限制 `LIMIT`
- 限制超时
- 审计生成 SQL 与执行结果

### MCP

- 只允许当前用户有权限的 MCP
- 记录调用审计
- 失败时降级，不影响主对话链

## 多代理并行开发拆分

为了支持多代理同时开发，建议按以下边界拆分，避免共享写集：

### 代理 A：Planner 与 Plan 模型

负责文件：

- `backend/src/main/java/com/westflow/ai/planner/**`
- `backend/src/main/java/com/westflow/ai/model/**` 中新增 plan 相关模型
- 对 `DbAiCopilotService` 的最小接线

### 代理 B：Execution Router 与 Knowledge/Workflow Executor

负责文件：

- `backend/src/main/java/com/westflow/ai/executor/**`
- `backend/src/main/java/com/westflow/ai/runtime/**` 的最小适配

### 代理 C：Stats Executor 与 Text2SQL

负责文件：

- `backend/src/main/java/com/westflow/ai/stats/**`
- 统计类测试

### 代理 D：Action Executor 与写操作确认链

负责文件：

- `backend/src/main/java/com/westflow/ai/action/**`
- `DbAiCopilotService` 写链切换部分

### 代理 E：Skill / MCP 注册与提示资产整理

负责文件：

- `backend/src/main/java/com/westflow/ai/service/AiRegistryCatalogService.java`
- `backend/src/main/java/com/westflow/ai/service/AiMcpClientFactory.java`
- 数据库注册元数据与技能内容加载

### 共享文件控制

以下文件为高冲突文件，必须最后由主线程收口：

- `AiCopilotConfiguration.java`
- `DbAiCopilotService.java`
- `SpringAiAlibabaCopilotRuntimeService.java`

原则：

- 并行代理不要同时大改上述 3 个文件
- 通过新增包与新增类承接变化
- 主线程最后统一接线

## 成功标准

重构完成后，应满足：

1. 普通问答、功能推荐、使用说明不再依赖大量关键词规则
2. 统计问答、图表和表格通过统一执行器完成
3. 写操作通过统一 action planner + validator + confirmation 完成
4. MCP 只承担外部能力，不再与内部 Tool 混用职责
5. 新增系统能力时，只需接入：
   - skill 提示
   - tool 或 mcp
   - executor 映射  
   而不是再修改巨石 service

