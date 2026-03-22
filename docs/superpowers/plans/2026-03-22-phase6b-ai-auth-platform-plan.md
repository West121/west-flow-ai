# Phase 6B AI Auth Platform Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用真实数据库登录替换 fixture，并在 `Spring Boot 3.5.12 + Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.0` 基线上交付完整 AI Copilot 平台，同时预置 OA/PLM 完整流程定义 SQL 与启动同步。

**Architecture:** 先升级技术基线并收口认证主链路，把登录、当前用户上下文、AI 能力权限改成统一从数据库聚合；随后构建统一 AI Gateway，把 Agent、MCP、Skill 和平台工具挂到同一工具注册中心，并基于 Spring AI Alibaba 的 `Supervisor / Routing / Skills` 建立多智能体编排层；最后以 SQL 种子和启动同步器补齐 OA/PLM 完整流程定义，保证本地启动即可测试。

**Tech Stack:** `Spring Boot 3.5.12`, `Sa-Token`, `MyBatis-Plus`, `PostgreSQL`, `Flyway`, `Flowable`, `Spring AI 1.1.2`, `Spring AI Alibaba 1.1.2.0`, `React 19`, `TanStack Query`, `TanStack Router`, `zustand`, `shadcn/ui`

---

## Chunk 0: AI 技术栈升级基线

### Task 0: 升级 Spring Boot / Spring AI / Spring AI Alibaba 版本基线

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Test: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: 先写验证清单**

确认以下内容在升级后必须回归：
- 后端应用可启动
- Flowable 只保留 BPMN 主链路
- 现有 AI Controller 单测可跑
- 现有认证、PLM、流程定义同步测试可跑

- [ ] **Step 2: 升级依赖管理**

在 `backend/pom.xml` 中：
- 将 `spring-boot-starter-parent` 升级到 `3.5.12`
- 将 `spring-ai.version` 升级到 `1.1.2`
- 引入 `spring-ai-alibaba-bom:1.1.2.0`
- 补齐 `spring-ai-alibaba-agent-framework`、按需的 `graph`/`skills` 相关依赖
- 保持 Flowable 仅使用 BPMN/Process Engine 所需 starter

- [ ] **Step 3: 对齐配置键**

检查并修正 `application*.yml` 中的 AI 配置项，确保与 `Spring AI 1.1.2` 和 `Spring AI Alibaba 1.1.2.0` 对齐。

- [ ] **Step 4: 跑技术基线测试**

Run: `mvn -f backend/pom.xml -Dtest=AuthControllerTest,AiCopilotControllerTest,AiCopilotServiceTest,PLMControllerTest,PublishedDefinitionBootstrapServiceTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/resources/application-local.yml backend/src/test/resources/application-test.yml
git commit -m "build: 升级spring boot与spring ai技术基线"
```

## Chunk 1: 真实数据库登录

### Task 1: 扩展用户表认证字段与种子数据

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Test: `backend/src/test/java/com/westflow/identity/api/AuthControllerTest.java`

- [ ] **Step 1: 先写失败测试**

在 `AuthControllerTest` 中新增：
- 数据库存在真实用户且密码正确时登录成功
- 密码错误时返回 401
- 被锁定用户返回锁定错误

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=AuthControllerTest test`
Expected: FAIL，提示数据库登录字段或逻辑缺失

- [ ] **Step 3: 修改 `V1__init.sql`**

给 `wf_user` 增加：
- `password_hash`
- `login_enabled`
- `failed_login_count`
- `locked_until`
- `last_login_at`
- `password_updated_at`

为 `admin`、`zhangsan`、`lisi`、`wangwu` 写入真实哈希密码种子。

- [ ] **Step 4: 运行相关测试**

Run: `mvn -f backend/pom.xml -Dtest=AuthControllerTest test`
Expected: 仍 FAIL，但失败点移动到服务层未实现

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/resources/db/migration/V1__init.sql backend/src/test/java/com/westflow/identity/api/AuthControllerTest.java
git commit -m "test: 补充真实数据库登录表结构与测试"
```

### Task 2: 实现数据库登录服务并移除主链路 fixture

**Files:**
- Create: `backend/src/main/java/com/westflow/identity/mapper/AuthUserMapper.java`
- Create: `backend/src/main/java/com/westflow/identity/service/DatabaseAuthService.java`
- Create: `backend/src/main/java/com/westflow/identity/service/PasswordService.java`
- Modify: `backend/src/main/java/com/westflow/identity/api/AuthController.java`
- Modify: `backend/src/main/java/com/westflow/identity/security/SaTokenConfiguration.java`
- Modify: `backend/src/main/java/com/westflow/identity/service/CurrentUserAccessService.java`
- Modify: `backend/src/main/java/com/westflow/identity/service/FixtureAuthService.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/westflow/identity/api/AuthControllerTest.java`

- [ ] **Step 1: 写更细的失败测试**

补充：
- `current-user` 返回数据库角色、菜单、数据权限、AI 能力
- `switch-context` 校验岗位来自数据库

- [ ] **Step 2: 运行单测确认失败**

Run: `mvn -f backend/pom.xml -Dtest=AuthControllerTest test`
Expected: FAIL

- [ ] **Step 3: 实现最小数据库登录**

实现：
- 根据用户名查 `wf_user`
- `BCrypt` 校验密码
- 失败计数与锁定处理
- 登录成功后重置失败计数并写入 `last_login_at`
- `current-user` 从数据库聚合

- [ ] **Step 4: 将 fixture 降为可选 dev profile**

规则：
- 默认 profile 不加载 `FixtureAuthService`
- 仅 `local-fixture` profile 下可启用

- [ ] **Step 5: 跑后端测试**

Run: `mvn -f backend/pom.xml -Dtest=AuthControllerTest test`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/westflow/identity backend/src/main/resources/application.yml
git commit -m "feat: 使用真实数据库登录替换fixture认证"
```

## Chunk 2: OA / PLM 完整流程定义 SQL 与启动同步

### Task 3: 预置 OA / PLM 完整流程定义记录

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Test: `backend/src/test/java/com/westflow/processdef/service/ProcessDefinitionServiceTest.java`

- [ ] **Step 1: 写失败测试**

新增测试断言：
- OA 3 条定义存在
- PLM 3 条定义存在
- 已发布状态、版本、业务绑定完整

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=ProcessDefinitionServiceTest test`
Expected: FAIL

- [ ] **Step 3: 修改 SQL 种子**

种入：
- 完整流程定义主记录
- `dsl_json`
- `bpmn_xml`
- 版本记录
- 发布记录
- 业务绑定

- [ ] **Step 4: 运行测试**

Run: `mvn -f backend/pom.xml -Dtest=ProcessDefinitionServiceTest test`
Expected: 仍 FAIL，但失败点移动到引擎同步缺失

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/resources/db/migration/V1__init.sql backend/src/test/java/com/westflow/processdef/service/ProcessDefinitionServiceTest.java
git commit -m "test: 预置oa与plm完整流程定义种子"
```

### Task 4: 实现已发布定义启动同步器

**Files:**
- Create: `backend/src/main/java/com/westflow/processdef/service/PublishedDefinitionBootstrapService.java`
- Modify: `backend/src/main/java/com/westflow/processdef/service/ProcessDefinitionService.java`
- Modify: `backend/src/main/java/com/westflow/flowable/FlowableEngineFacade.java`
- Test: `backend/src/test/java/com/westflow/processdef/service/PublishedDefinitionBootstrapServiceTest.java`

- [ ] **Step 1: 写失败测试**

覆盖：
- 已发布但未部署定义会自动部署
- 已部署定义不会重复部署
- 部署后回写 `flowable_definition_id`

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=PublishedDefinitionBootstrapServiceTest test`
Expected: FAIL

- [ ] **Step 3: 实现同步器**

在应用启动后：
- 扫描已发布定义
- 校验引擎定义是否存在
- 缺失则自动部署并回写

- [ ] **Step 4: 跑测试**

Run: `mvn -f backend/pom.xml -Dtest=PublishedDefinitionBootstrapServiceTest,ProcessDefinitionServiceTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/westflow/processdef backend/src/test/java/com/westflow/processdef/service
git commit -m "feat: 启动时同步已发布流程定义到flowable"
```

## Chunk 3: AI Copilot 后端平台

### Task 5: 建立 AI 会话、消息、工具调用与确认模型

**Files:**
- Create: `backend/src/main/java/com/westflow/ai/**`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Test: `backend/src/test/java/com/westflow/ai/api/AiCopilotControllerTest.java`

- [ ] **Step 1: 写失败测试**

测试：
- 创建会话
- 拉取历史
- 记录消息
- 记录工具调用
- 写操作进入待确认状态

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=AiCopilotControllerTest test`
Expected: FAIL

- [ ] **Step 3: 实现数据模型与 API**

至少包含：
- conversation
- message
- tool_call
- confirmation
- audit

- [ ] **Step 4: 跑测试**

Run: `mvn -f backend/pom.xml -Dtest=AiCopilotControllerTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/westflow/ai backend/src/main/resources/db/migration/V1__init.sql backend/src/test/java/com/westflow/ai/api/AiCopilotControllerTest.java
git commit -m "feat: 建立ai copilot会话与工具调用模型"
```

### Task 6: 实现 Agent / MCP / Skill / Tool 统一注册

**Files:**
- Create: `backend/src/main/java/com/westflow/ai/agent/**`
- Create: `backend/src/main/java/com/westflow/ai/tool/**`
- Create: `backend/src/main/java/com/westflow/ai/mcp/**`
- Create: `backend/src/main/java/com/westflow/ai/skill/**`
- Test: `backend/src/test/java/com/westflow/ai/service/ToolExecutionServiceTest.java`

- [ ] **Step 1: 写失败测试**

测试：
- 读工具可直接执行
- 写工具必须返回确认需求
- 无权限工具调用被拒绝

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=ToolExecutionServiceTest test`
Expected: FAIL

- [ ] **Step 3: 实现统一注册中心**

实现：
- Agent Registry
- Tool Registry
- MCP Adapter Registry
- Skill Adapter Registry
- Confirmation Gate

- [ ] **Step 4: 跑测试**

Run: `mvn -f backend/pom.xml -Dtest=ToolExecutionServiceTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/westflow/ai backend/src/test/java/com/westflow/ai/service/ToolExecutionServiceTest.java
git commit -m "feat: 打通ai工具注册与确认机制"
```

## Chunk 4: AI Copilot 前端完整入口

### Task 7: 搭建统一 Copilot 面板与富消息卡

**Files:**
- Create: `frontend/src/features/ai/**`
- Modify: `frontend/src/components/layout/**`
- Modify: `frontend/src/stores/**`
- Test: `frontend/src/features/ai/**/*.test.tsx`

- [ ] **Step 1: 写失败测试**

覆盖：
- 打开 Copilot 面板
- 历史会话展示
- 富消息卡渲染
- 写操作确认卡展示

- [ ] **Step 2: 运行前端测试确认失败**

Run: `pnpm --dir frontend test --run src/features/ai`
Expected: FAIL

- [ ] **Step 3: 实现面板与卡片**

实现：
- 全局按钮
- 毛玻璃聊天面板
- 会话侧栏
- 富消息卡
- 确认卡

- [ ] **Step 4: 跑前端测试**

Run: `pnpm --dir frontend test --run src/features/ai && pnpm --dir frontend typecheck`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/features/ai frontend/src/components/layout frontend/src/stores
git commit -m "feat: 增加统一ai copilot前端入口"
```

### Task 8: 接入 AI 权限、上下文与工具调用流

**Files:**
- Modify: `frontend/src/lib/api/auth.ts`
- Modify: `frontend/src/lib/api/**`
- Modify: `frontend/src/features/workbench/pages.tsx`
- Modify: `frontend/src/features/workflow/pages.tsx`
- Modify: `frontend/src/features/oa/pages.tsx`
- Test: `frontend/src/features/ai/**/*.test.tsx`

- [ ] **Step 1: 写失败测试**

覆盖：
- 无 `ai:copilot:open` 不展示入口
- 当前任务页面自动带任务上下文
- 读操作直接执行
- 写操作必须确认

- [ ] **Step 2: 运行测试确认失败**

Run: `pnpm --dir frontend test --run src/features/ai`
Expected: FAIL

- [ ] **Step 3: 实现上下文与权限接线**

实现：
- 从 current-user 读取 AI capability
- 页面上下文注入 Copilot
- 工具调用与确认回传

- [ ] **Step 4: 跑测试**

Run: `pnpm --dir frontend test --run src/features/ai src/features/workbench/pages.test.tsx && pnpm --dir frontend typecheck`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/src/lib/api frontend/src/features/workbench/pages.tsx frontend/src/features/workflow/pages.tsx frontend/src/features/oa/pages.tsx
git commit -m "feat: 接入ai copilot权限与页面上下文"
```

## Chunk 5: 全量回归与清理

### Task 9: 删除主链路 fixture 依赖并完成回归

**Files:**
- Modify: `backend/src/main/java/com/westflow/identity/**`
- Modify: `backend/src/test/java/**`
- Modify: `docs/contracts/ai-tools.md`
- Modify: `docs/contracts/auth.md`

- [ ] **Step 1: 清理主链路 fixture 引用**

确保默认 profile 下：
- 不再依赖 `FixtureAuthService`
- 不再通过内存用户列表登录

- [ ] **Step 2: 更新契约文档**

更新：
- 真实数据库登录
- AI 读直执、写必确认
- Agent / MCP / Skill 注册模型

- [ ] **Step 3: 跑全量验证**

Run:
```bash
mvn -f backend/pom.xml test
pnpm --dir frontend test --run
pnpm --dir frontend typecheck
pnpm --dir frontend lint
pnpm --dir frontend build
./scripts/validate-contracts.sh
```

Expected: 全部通过

- [ ] **Step 4: 最终提交**

```bash
git add backend frontend docs
git commit -m "feat: 完成phase6b真实登录与完整ai copilot平台"
```
