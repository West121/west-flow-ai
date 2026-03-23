# AI PLM Parallel Lanes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Archive note (2026-03-23):** 本并行计划记录当日分工冻结；若出现 `backend/src/main/java/com/westflow/aimcpdemo/**`，仅表示演示 / 集成测试域写集，不代表当前正式平台契约仍以 demo 域为主。

**Goal:** 在当前真实登录、真实 Flowable、完整 AI Copilot 与 PLM 基线上，并行交付 AI 业务闭环、PLM 业务深化、AI 运维可观测和前端体验收口。

**Architecture:** 先冻结共享契约，再把实现拆成 Lane A / B / C 三条独立写集并行推进，主线程同步做 Lane D1 的前端外壳与生产化优化；待 A / C 契约稳定后，再由主线程完成 D2，把真实 agent/tool/skill/mcp 命中、确认卡和结果卡接到统一 Copilot UI。

**Tech Stack:** `Spring Boot 3.5.12`, `Spring AI 1.1.2`, `Spring AI Alibaba 1.1.2.0`, `Flowable`, `Sa-Token`, `MyBatis-Plus`, `PostgreSQL`, `React 19`, `TanStack Router`, `TanStack Query`, `shadcn/ui`

---

## Chunk 0: 共享契约冻结

### Task 0: 冻结 AI 富响应块协议

**Files:**
- Create: `docs/contracts/ai-rich-response.md`
- Modify: `backend/src/main/java/com/westflow/ai/**`（只在实现阶段）
- Modify: `frontend/src/features/ai/**`（只在实现阶段）

- [ ] **Step 1: 定义块类型**

写明：
- `text`
- `confirmation`
- `result`
- `stats`
- `form-preview`
- `trace`

- [ ] **Step 2: 定义通用字段**

统一：
- `blockType`
- `title`
- `summary`
- `payload`
- `status`
- `actions`

- [ ] **Step 3: 写入契约文档**

保存到 `docs/contracts/ai-rich-response.md`。

- [ ] **Step 4: 提交**

```bash
git add docs/contracts/ai-rich-response.md
git commit -m "docs: 冻结ai富响应块协议"
```

### Task 1: 冻结 AI 工具执行协议

**Files:**
- Create: `docs/contracts/ai-tool-execution.md`

- [ ] **Step 1: 定义工具执行字段**

统一：
- `toolKey`
- `toolSource`
- `actionMode`
- `requiresConfirmation`
- `summary`
- `payload`
- `result`
- `failureReason`
- `contextTags`

- [ ] **Step 2: 定义确认规则**

写清：
- 读操作直执
- 写操作待确认
- 确认通过后执行
- 拒绝后写审计与结果块

- [ ] **Step 3: 写入契约文档**

保存到 `docs/contracts/ai-tool-execution.md`。

- [ ] **Step 4: 提交**

```bash
git add docs/contracts/ai-tool-execution.md
git commit -m "docs: 冻结ai工具执行协议"
```

### Task 2: 冻结 PLM 助手工具协议

**Files:**
- Create: `docs/contracts/plm-assistant-tools.md`

- [ ] **Step 1: 定义 PLM 助手支持的业务域**

只允许：
- `ECR`
- `ECO`
- `MATERIAL_MASTER`

- [ ] **Step 2: 定义可执行读工具**

包含：
- 列表查询
- 详情查询
- 审批状态查询
- 业务单与实例联查

- [ ] **Step 3: 明确限制**

声明：
- AI 不能直接绕过页面修改 PLM 主数据
- 如需写操作，必须走确认与正式业务接口

- [ ] **Step 4: 写入契约文档**

保存到 `docs/contracts/plm-assistant-tools.md`。

- [ ] **Step 5: 提交**

```bash
git add docs/contracts/plm-assistant-tools.md
git commit -m "docs: 冻结plm助手工具协议"
```

## Chunk 1: Lane A - AI 业务闭环

### Task 3: AI 发起流程闭环

**Files:**
- Modify: `backend/src/main/java/com/westflow/ai/**`
- Modify: `backend/src/test/java/com/westflow/ai/**`
- Modify: `frontend/src/features/ai/**`
- Modify: `frontend/src/lib/api/ai-copilot.ts`

- [ ] **Step 1: 写失败测试**

后端测试覆盖：
- AI 推荐可发起流程
- AI 生成发起确认卡
- 用户确认后真实发起流程

- [ ] **Step 2: 跑失败测试**

Run: `mvn -f backend/pom.xml -Dtest=AiCopilotControllerTest,AiCopilotServiceTest test`
Expected: FAIL

- [ ] **Step 3: 实现后端工具与确认流**

实现：
- 推荐业务入口
- 生成发起参数
- 生成确认记录
- 确认后调用真实发起接口

- [ ] **Step 4: 接前端确认卡**

前端在 Copilot 面板展示：
- 发起建议
- 参数摘要
- 确认按钮
- 执行结果卡

- [ ] **Step 5: 跑回归**

Run:
- `mvn -f backend/pom.xml -Dtest=AiCopilotControllerTest,AiCopilotServiceTest test`
- `pnpm --dir frontend test --run src/features/ai/index.test.tsx src/lib/api/ai-copilot.test.ts`

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/westflow/ai backend/src/test/java/com/westflow/ai frontend/src/features/ai frontend/src/lib/api/ai-copilot.ts
git commit -m "feat: 打通ai发起流程闭环"
```

### Task 4: AI 处理待办与统计问答

**Files:**
- Modify: `backend/src/main/java/com/westflow/ai/**`
- Modify: `backend/src/test/java/com/westflow/ai/**`
- Modify: `frontend/src/features/ai/**`

- [ ] **Step 1: 写失败测试**

覆盖：
- AI 查询待办
- AI 解释待办上下文
- AI 生成处理建议
- AI 查询统计并返回统计块

- [ ] **Step 2: 运行失败测试**

Run: `mvn -f backend/pom.xml -Dtest=AiCopilotControllerTest,AiGatewayServiceTest test`
Expected: FAIL

- [ ] **Step 3: 实现工具与结果块**

实现：
- 待办读取工具
- 审批动作建议工具
- 统计问答工具
- 统一结果块

- [ ] **Step 4: 接前端显示**

展示：
- 待办建议卡
- 统计卡
- 失败卡

- [ ] **Step 5: 跑回归**

Run:
- `mvn -f backend/pom.xml -Dtest=AiCopilotControllerTest,AiGatewayServiceTest,AiToolExecutionServiceTest test`
- `pnpm --dir frontend test --run src/features/ai/index.test.tsx`

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/westflow/ai backend/src/test/java/com/westflow/ai frontend/src/features/ai
git commit -m "feat: 打通ai待办处理与统计问答"
```

## Chunk 2: Lane B - PLM 业务深化

### Task 5: 完成 PLM 三业务列表与详情页

**Files:**
- Modify: `backend/src/main/java/com/westflow/plm/**`
- Modify: `backend/src/test/java/com/westflow/plm/**`
- Modify: `frontend/src/features/plm/**`
- Modify: `frontend/src/lib/api/plm.ts`

- [ ] **Step 1: 写失败测试**

覆盖：
- `ECR` 列表、详情
- `ECO` 列表、详情
- `物料主数据变更` 列表、详情

- [ ] **Step 2: 跑失败测试**

Run:
- `mvn -f backend/pom.xml -Dtest=PLMControllerTest test`
- `pnpm --dir frontend test --run src/features/plm/pages.test.tsx src/lib/api/plm.test.ts`
Expected: FAIL

- [ ] **Step 3: 实现后端分页与详情**

要求：
- 独立列表接口
- 独立详情接口
- 支持模糊查询、分页、筛选、排序

- [ ] **Step 4: 实现前端页面**

每个业务至少：
- 列表页
- 详情页
- 发起页与详情跳转打通

- [ ] **Step 5: 跑回归**

Run:
- `mvn -f backend/pom.xml -Dtest=PLMControllerTest,ApprovalSheetQueryServiceTest test`
- `pnpm --dir frontend test --run src/features/plm/pages.test.tsx src/lib/api/plm.test.ts`
- `pnpm --dir frontend typecheck`

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/westflow/plm backend/src/test/java/com/westflow/plm frontend/src/features/plm frontend/src/lib/api/plm.ts
git commit -m "feat: 补齐plm三业务列表与详情闭环"
```

### Task 6: 打通审批单与 PLM 双向联查

**Files:**
- Modify: `backend/src/main/java/com/westflow/approval/**`
- Modify: `backend/src/main/java/com/westflow/plm/**`
- Modify: `frontend/src/features/plm/**`
- Modify: `frontend/src/features/workbench/**`

- [ ] **Step 1: 写失败测试**

覆盖：
- 业务详情跳审批单
- 审批单跳业务详情

- [ ] **Step 2: 实现联查协议**

后端补：
- 业务单关联实例信息
- 审批单关联业务详情路径

- [ ] **Step 3: 接前端跳转**

在 PLM 详情与审批单详情互相增加入口。

- [ ] **Step 4: 跑回归**

Run:
- `mvn -f backend/pom.xml -Dtest=ApprovalSheetQueryServiceTest,PLMControllerTest test`
- `pnpm --dir frontend test --run src/features/plm/pages.test.tsx src/features/workbench/pages.test.tsx`

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/westflow/approval backend/src/main/java/com/westflow/plm frontend/src/features/plm frontend/src/features/workbench
git commit -m "feat: 打通审批单与plm业务双向联查"
```

## Chunk 3: Lane C - AI 运维与可观测

### Task 7: 增强 AI 管理后台与调用观测

**Files:**
- Modify: `backend/src/main/java/com/westflow/aiadmin/**`
- Modify: `backend/src/test/java/com/westflow/aiadmin/**`
- Modify: `frontend/src/features/ai-admin/**`
- Modify: `frontend/src/lib/api/ai-admin.ts`

- [ ] **Step 1: 写失败测试**

覆盖：
- 调用日志
- 确认记录
- 失败记录
- 条件筛选

- [ ] **Step 2: 实现后端查询增强**

补齐：
- 失败原因
- 命中来源
- 执行耗时
- 过滤条件

- [ ] **Step 3: 实现前端状态页**

补页面：
- 调用诊断
- 失败详情
- 确认记录查看

- [ ] **Step 4: 跑回归**

Run:
- `mvn -f backend/pom.xml -Dtest=AiRegistryControllerTest,AiAuditControllerTest test`
- `pnpm --dir frontend test --run src/lib/api/ai-admin.test.ts`
- `pnpm --dir frontend typecheck`

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/westflow/aiadmin backend/src/test/java/com/westflow/aiadmin frontend/src/features/ai-admin frontend/src/lib/api/ai-admin.ts
git commit -m "feat: 增强ai管理后台与调用观测"
```

### Task 8: MCP 连通性检测与外部诊断

**Files:**
- Modify: `backend/src/main/java/com/westflow/aimcpdemo/**`
- Modify: `backend/src/main/java/com/westflow/ai/**`
- Modify: `backend/src/test/java/com/westflow/aimcpdemo/**`
- Modify: `frontend/src/features/ai-admin/**`

- [ ] **Step 1: 写失败测试**

覆盖：
- MCP 连通性检测成功
- MCP 连通性检测失败
- 失败原因可展示

- [ ] **Step 2: 实现后端诊断接口**

至少支持：
- 检测外部 MCP 连接
- 返回失败原因
- 返回工具清单摘要

- [ ] **Step 3: 接前端诊断页**

支持：
- 手动检测
- 状态展示
- 失败信息查看

- [ ] **Step 4: 跑回归**

Run:
- `mvn -f backend/pom.xml -Dtest=AiMcpDemoEndpointTest,AiMcpClientFactoryTest,AiRuntimeToolCallbackProviderTest test`
- `pnpm --dir frontend test --run src/lib/api/ai-admin.test.ts`

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/westflow/aimcpdemo backend/src/main/java/com/westflow/ai backend/src/test/java/com/westflow/aimcpdemo frontend/src/features/ai-admin
git commit -m "feat: 打通mcp连通性检测与外部诊断"
```

## Chunk 4: Lane D - 前端体验与生产化

### Task 9: Copilot 面板外壳与上下文展示

**Files:**
- Modify: `frontend/src/features/ai/**`
- Modify: `frontend/src/components/layout/ai-copilot-launcher.tsx`
- Modify: `frontend/src/routes/_authenticated/ai.tsx`

- [ ] **Step 1: 写前端测试**

覆盖：
- 上下文摘要展示
- 会话列表切换
- 富卡片容器槽位

- [ ] **Step 2: 实现 D1**

先只做：
- 布局
- 历史结构
- 摘要区
- 卡片容器

- [ ] **Step 3: 跑回归**

Run:
- `pnpm --dir frontend test --run src/features/ai/index.test.tsx`
- `pnpm --dir frontend typecheck`

- [ ] **Step 4: 提交**

```bash
git add frontend/src/features/ai frontend/src/components/layout/ai-copilot-launcher.tsx frontend/src/routes/_authenticated/ai.tsx
git commit -m "feat: 优化ai copilot面板信息架构"
```

### Task 10: 生产化收尾与 D2 接线

**Files:**
- Modify: `frontend/src/features/ai/**`
- Modify: `frontend/src/lib/api/ai-copilot.ts`
- Modify: `frontend/vite.config.*`（如需要）

- [ ] **Step 1: 等待 A / C 契约稳定**

确认：
- 富响应块协议已被实现
- 工具执行协议已稳定

- [ ] **Step 2: 接真实命中与结果展示**

展示：
- 命中的 agent / tool / skill / mcp
- 确认卡
- 结果卡
- 失败卡

- [ ] **Step 3: 收尾前端构建问题**

必要时优化：
- chunk 体积
- 历史 warning

- [ ] **Step 4: 跑整体验证**

Run:
- `pnpm --dir frontend test --run`
- `pnpm --dir frontend typecheck`
- `pnpm --dir frontend lint`
- `pnpm --dir frontend build`

- [ ] **Step 5: 提交**

```bash
git add frontend/src/features/ai frontend/src/lib/api/ai-copilot.ts frontend/vite.config.*
git commit -m "feat: 完成ai copilot前端体验与生产化收尾"
```

## Chunk 5: 最终联调

### Task 11: 全量联调与阶段提交

**Files:**
- Modify: 按实际联调问题最小化修复

- [ ] **Step 1: 跑后端全量关键回归**

Run:
- `mvn -f backend/pom.xml -Dtest=AuthControllerTest,AiCopilotControllerTest,AiCopilotServiceTest,AiGatewayServiceTest,AiToolExecutionServiceTest,AiRegistryControllerTest,AiAuditControllerTest,AiMcpDemoEndpointTest,PLMControllerTest,ApprovalSheetQueryServiceTest,PublishedDefinitionBootstrapServiceTest test`

- [ ] **Step 2: 跑前端全量关键回归**

Run:
- `pnpm --dir frontend test --run`
- `pnpm --dir frontend typecheck`
- `pnpm --dir frontend lint`
- `pnpm --dir frontend build`

- [ ] **Step 3: 跑契约检查**

Run: `./scripts/validate-contracts.sh`
Expected: PASS

- [ ] **Step 4: 提交最终联调修复**

```bash
git add .
git commit -m "feat: 收口phase6c ai与plm并行闭环"
```

Plan complete and saved to `docs/superpowers/plans/2026-03-23-ai-plm-parallel-lanes-plan.md`. Ready to execute?
