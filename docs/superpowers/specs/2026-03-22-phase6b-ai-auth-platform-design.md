# Phase 6B：真实登录与完整 AI Copilot 平台设计

## 1. 目标

本阶段一次性收口三条主线：

- 去掉 `FixtureAuthService` 主链路，切换到真实组织库登录校验
- 构建完整 `AI Copilot` 平台，而不是最小聊天壳
- 在数据库中预置 `OA + PLM` 的完整流程定义记录，并在启动后同步部署到真实 `Flowable`

本阶段完成后，平台应具备：

- 真实用户库登录
- 真实权限、菜单、数据权限、AI 能力联动
- 全局唯一 `AI Copilot` 入口
- `Agent / MCP / Skill / 平台工具` 统一编排
- `OA / PLM` 关键流程定义可直接测试

## 2. 范围边界

### 2.1 本阶段必须完成

- 真实数据库登录替换 `FixtureAuthService`
- 密码哈希、登录启停、失败计数、锁定字段补齐
- `AI Copilot` 前端完整入口与历史会话
- `AI Gateway`、会话持久化、工具调用审计
- `Agent Registry`
- `MCP Adapter Registry`
- `Skill Adapter Registry`
- `Tool Registry`
- 读操作直执、写操作强制确认
- `OA + PLM` 完整流程定义 SQL 种子
- 启动后自动同步发布定义到 `Flowable`

### 2.2 本阶段明确不做

- `OA / PLM` 新业务页面的大规模扩建
- 新增第二套 AI 前端入口
- 引入可视化 AI 工作流编辑器
- 完整外部身份源接入（LDAP / OAuth / SSO）
- 真实 MCP 外部市场安装中心

说明：

- `OA / PLM` 业务页面和更深业务闭环后置，但流程定义和绑定必须先可测
- 认证改造和 AI 平台必须共享同一套权限与上下文模型

## 3. 真实数据库登录设计

### 3.1 当前问题

当前登录由 `FixtureAuthService` 在内存中校验用户名和密码，再从数据库读取角色、菜单和数据权限。  
这种实现能支撑联调，但不适合作为 Phase 6 的平台基线。

### 3.2 目标形态

登录必须改为：

- 用户名、状态、密码信息来自 `wf_user`
- 登录成功后仍通过 `Sa-Token` 发放会话
- 当前用户上下文仍通过 `GET /api/v1/auth/current-user` 返回
- 权限、角色、菜单、数据权限、AI 能力继续从数据库聚合

### 3.3 数据库字段

在 `wf_user` 上补齐以下字段：

- `password_hash`
- `login_enabled`
- `failed_login_count`
- `locked_until`
- `last_login_at`
- `password_updated_at`

密码算法统一使用 `BCrypt`，不允许明文或自定义弱摘要。

### 3.4 服务分层

新增：

- `DatabaseAuthService`
- `PasswordService`
- `AuthUserMapper`

职责边界：

- `DatabaseAuthService`：登录、锁定、失败计数、当前用户上下文聚合
- `PasswordService`：哈希与校验
- `AuthUserMapper`：登录专用用户读写

### 3.5 Fixture 处置策略

`FixtureAuthService` 不再参与主链路。

保留策略：

- 默认 profile 下不加载
- 如确有需要，仅在显式 `local-fixture` profile 下启用

主流程中所有登录、当前用户、切岗、AI 权限计算统一走数据库实现。

## 4. 完整 AI Copilot 平台设计

## 4.1 产品目标

前端只保留一个统一 `AI Copilot` 按钮，但后端支持完整平台化能力：

- 连续对话
- 持久化历史
- 上下文感知
- 工具编排
- 审计可追踪
- 动作可确认
- Agent / MCP / Skill 统一挂载

### 4.2 前端形态

前端统一入口包含：

- 全局 `AI Copilot` 按钮
- 毛玻璃对话面板
- 会话列表
- 消息流
- 上下文摘要区
- 富消息卡片

富消息至少支持：

- 文本回答
- 表单预览
- 操作确认卡
- 统计图卡
- 流程图预览卡
- 待办建议动作卡

### 4.3 后端平台结构

后端新增统一 AI 平台层：

- `ai/gateway`
- `ai/conversation`
- `ai/agent`
- `ai/tool`
- `ai/mcp`
- `ai/skill`
- `ai/audit`

核心服务：

- `AiCopilotGatewayService`
- `ConversationService`
- `AiContextBuilder`
- `ToolExecutionService`
- `ConfirmationService`
- `AgentRegistry`
- `McpAdapterRegistry`
- `SkillAdapterRegistry`
- `AiAuditService`

### 4.4 Agent 目录

首批完整智能体：

- 流程设计智能体
- 智能填报智能体
- 流程发起智能体
- 待办处理智能体
- 统计问答智能体
- PLM 助手

这些 Agent 不单独做前端入口，统一由 Copilot 调度。

### 4.5 Tool 分类

工具分为四类：

- 平台内置工具
- Agent 专属工具
- MCP 工具
- Skill 工具

平台内置工具优先覆盖：

- 流程定义查询
- 流程发起
- 待办查询
- 待办处理
- 审批轨迹查询
- 流程统计查询
- OA 业务单查询
- PLM 业务单查询

### 4.6 权限与确认

统一规则：

- 读操作：默认可直接执行
- 写操作：必须确认

读操作示例：

- 查询待办
- 查询流程详情
- 查询业务单
- 查询统计
- 解释流程与节点状态

写操作示例：

- 发起流程
- 处理待办
- 退回 / 驳回 / 跳转 / 加签 / 减签
- 修改流程定义
- 发布流程
- 修改业务数据

确认交互规则：

- AI 先生成操作卡
- 展示影响范围、目标对象、关键参数
- 用户点击确认后才执行
- 每次确认必须进入审计日志

### 4.7 上下文模型

每次对话自动附带：

- 当前用户
- 当前菜单
- 当前页面路由
- 当前业务单 / 任务 / 实例
- 当前组织上下文
- 当前权限与数据范围
- 最近会话摘要
- 最近工具调用结果

AI 不允许绕过现有菜单权限、数据权限和流程动作权限。

## 5. OA + PLM 完整流程定义 SQL 种子

### 5.1 原则

虽然 `OA / PLM` 业务页面后置，但数据库中必须具备可直接测试的完整流程定义记录。

不允许只种：

- 流程 key
- 空壳元数据
- 未发布定义

必须同时具备：

- 流程定义主记录
- `DSL JSON`
- `BPMN XML`
- 版本记录
- 发布记录
- 业务绑定
- 默认表单 key / 节点表单关联信息

### 5.2 首批预置业务

`OA`：

- 请假申请
- 报销申请
- 通用申请

`PLM`：

- ECR 变更申请
- ECO 变更执行
- 物料主数据变更申请

### 5.3 启动同步到 Flowable

为了避免直接往 `ACT_*` 表写 SQL，本阶段增加启动同步器：

- 查询平台表中所有已发布定义
- 校验 `flowable_definition_id` 是否存在
- 若缺失或引擎内不存在，则自动部署 `bpmn_xml`
- 回写新的 `flowable_definition_id`

这样可同时满足：

- SQL 中有完整定义记录
- Flowable 引擎表由引擎自己维护
- 本地启动后即可测试发起和待办

## 6. 测试与验收

### 6.1 认证验收

- `admin` 等真实用户从数据库登录成功
- 错误密码失败计数增加
- 被锁定用户不可登录
- 当前用户菜单、角色、数据权限、AI 能力正确返回
- 不再依赖 `FixtureAuthService` 主链路

### 6.2 AI 验收

- 打开 Copilot 能看到历史会话
- 同一业务上下文中连续追问不丢失上下文
- 读操作工具可直接执行
- 写操作必须出现确认卡
- 确认后才真正落库或触发流程动作
- 每次工具调用都有审计记录

### 6.3 SQL 与流程定义验收

- `OA / PLM` 定义在平台表中可查询
- 启动后可自动同步到 `Flowable`
- 无需手工新建定义即可测试发起

## 7. 风险

- 单一 `V1__init.sql` 持续增长，维护成本高
- 真实登录替换会影响前端登录页和测试基线
- AI 平台一次性范围较大，必须通过工具注册与权限层收口
- 启动同步器若设计不好，容易造成重复部署或版本错乱

## 8. 推荐实施顺序

1. 真实数据库登录替换 fixture
2. `OA / PLM` 完整流程定义 SQL + 启动同步器
3. AI 会话与上下文平台
4. Agent / MCP / Skill 注册与权限控制
5. 富消息与写操作确认闭环
