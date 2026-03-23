# Platform Consolidation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收口 AI + PLM 之后的平台历史债、真实轨迹和通知生产化缺口，并为下一批 AI / PLM 深化建立稳定基线。

**Architecture:** 先做第一批并行线：运行态历史债清理、通知渠道生产化、文档契约重整。三条线独立推进，由主线程负责共享命名、最终集成和全量验证。第二批 AI / PLM 深化和前端工程化，必须建立在第一批稳定完成之后。

**Tech Stack:** Spring Boot 3.5.12, Flowable BPMN, Spring AI 1.1.2, Spring AI Alibaba 1.1.2.0, React, TanStack Router, Vitest, Maven

---

## 文件结构与责任

- `backend/src/main/java/com/westflow/processruntime/**`
  - 运行态查询、动作、轨迹和响应模型
- `backend/src/main/java/com/westflow/oa/**`
  - OA 业务发起响应与审批单联动
- `backend/src/main/java/com/westflow/plm/**`
  - PLM 业务发起响应与审批单联动
- `backend/src/main/java/com/westflow/notification/**`
  - 通知渠道、provider、发送记录
- `backend/src/main/java/com/westflow/aiadmin/**`
  - 诊断与运维页面后端接口
- `frontend/src/features/workbench/**`
  - 审批单详情、轨迹、流程中心
- `frontend/src/features/oa/**`
  - OA 查询与发起页
- `frontend/src/lib/api/workbench.ts`
  - 运行态 API 客户端
- `docs/contracts/**`
  - 契约文档
- `docs/superpowers/specs/**`
  - 上层设计说明

## Chunk 1：Lane A 运行态历史债清理与轨迹落地

### Task 1: 收口任务视图命名

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/api/ProcessTaskSnapshot.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableRuntimeStartService.java`
- Modify: `backend/src/main/java/com/westflow/oa/api/OALaunchResponse.java`
- Modify: `backend/src/main/java/com/westflow/plm/api/PlmLaunchResponse.java`
- Test: `backend/src/test/java/com/westflow/processruntime/**`

- [ ] Step 1: 写出命名替换清单并确定收口名称为 `ProcessTaskSnapshot`
- [ ] Step 2: 修改响应模型与引用点，保持 JSON 字段不变
- [ ] Step 3: 跑运行态相关测试，确认无接口回归
- [ ] Step 4: 提交本任务改动

### Task 2: 清理 demo 路径残留

**Files:**
- Modify: `frontend/src/features/oa/pages.tsx`
- Modify: `frontend/src/lib/api/workbench.ts`
- Modify: `docs/contracts/task-actions.md`
- Test: `frontend/src/features/oa/pages.test.tsx`

- [ ] Step 1: 搜索旧 demo 运行态路径的代码与契约残留
- [ ] Step 2: 改为正式路径或标记废弃说明
- [ ] Step 3: 跑对应前端和契约验证
- [ ] Step 4: 提交本任务改动

### Task 3: 实现真实轨迹查询

**Files:**
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeTraceStore.java`
- Modify: `backend/src/main/java/com/westflow/processruntime/service/FlowableProcessRuntimeService.java`
- Test: `backend/src/test/java/com/westflow/processruntime/service/FlowableProcessRuntimeTraceStoreTest.java`

- [ ] Step 1: 写失败测试，覆盖实例事件/自动化轨迹/通知轨迹查询
- [ ] Step 2: 实现最小真实查询逻辑，先基于现有事件表、自动化记录和通知记录聚合
- [ ] Step 3: 跑测试并补审批单详情回归
- [ ] Step 4: 提交本任务改动

## Chunk 2：Lane B 通知渠道生产化

### Task 4: 设计真实 provider 配置模型

**Files:**
- Modify: `backend/src/main/java/com/westflow/notification/model/**`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Test: `backend/src/test/java/com/westflow/notification/**`

- [ ] Step 1: 明确短信、企业微信、钉钉所需配置字段
- [ ] Step 2: 更新通道元数据与种子示例
- [ ] Step 3: 写配置解析测试
- [ ] Step 4: 提交本任务改动

### Task 5: 替换 mock provider

**Files:**
- Modify: `backend/src/main/java/com/westflow/notification/provider/WechatMockNotificationProvider.java`
- Modify: `backend/src/main/java/com/westflow/notification/provider/DingTalkMockNotificationProvider.java`
- Modify: `backend/src/main/java/com/westflow/notification/provider/SmsMockNotificationProvider.java`
- Create: `backend/src/main/java/com/westflow/notification/provider/*Real*.java`
- Test: `backend/src/test/java/com/westflow/notification/provider/**`

- [ ] Step 1: 写失败测试，覆盖真实请求构造与错误回写
- [ ] Step 2: 实现最小真实 provider，并保留可配置 mock 开关
- [ ] Step 3: 跑通知相关测试
- [ ] Step 4: 提交本任务改动

### Task 6: 诊断页联动真实状态

**Files:**
- Modify: `backend/src/main/java/com/westflow/aiadmin/mcp/**`
- Modify: `frontend/src/features/ai-admin/**`
- Test: `backend/src/test/java/com/westflow/aiadmin/**`
- Test: `frontend/src/lib/api/ai-admin.test.ts`

- [ ] Step 1: 将通知渠道诊断与真实 provider 结果关联
- [ ] Step 2: 前端显示真实失败原因和状态
- [ ] Step 3: 跑前后端测试
- [ ] Step 4: 提交本任务改动

## Chunk 3：Lane C 文档与契约重整

### Task 7: 刷新总设计文档

**Files:**
- Modify: `docs/superpowers/specs/2026-03-21-aibpmn-platform-design.md`
- Modify: `docs/superpowers/specs/2026-03-22-remaining-roadmap-design.md`

- [ ] Step 1: 标注已完成阶段
- [ ] Step 2: 删除或废弃 demo 时代说明
- [ ] Step 3: 写清当前剩余任务顺序
- [ ] Step 4: 提交本任务改动

### Task 8: 刷新主要契约

**Files:**
- Modify: `docs/contracts/task-actions.md`
- Modify: `docs/contracts/auth.md`
- Modify: `docs/contracts/ai-rich-response.md`
- Modify: `docs/contracts/plm-assistant-tools.md`

- [ ] Step 1: 对齐当前真实接口和响应块
- [ ] Step 2: 删除过时 demo 路径与伪语义
- [ ] Step 3: 跑 `./scripts/validate-contracts.sh`
- [ ] Step 4: 提交本任务改动

## Chunk 4：主线程集成与全量验证

### Task 9: 集成三条并行线

**Files:**
- Modify: 仅按集成需要调整冲突文件

- [ ] Step 1: 合并 Lane A/B/C 改动
- [ ] Step 2: 解决共享命名和响应类型冲突
- [ ] Step 3: 回归 OA / PLM / AI / 工作台主链路
- [ ] Step 4: 提交集成改动

### Task 10: 全量验证

- [ ] Step 1: 运行 `mvn -f backend/pom.xml test`
- [ ] Step 2: 运行 `pnpm --dir frontend test --run`
- [ ] Step 3: 运行 `pnpm --dir frontend typecheck`
- [ ] Step 4: 运行 `pnpm --dir frontend lint`
- [ ] Step 5: 运行 `pnpm --dir frontend build`
- [ ] Step 6: 运行 `./scripts/validate-contracts.sh`
- [ ] Step 7: 提交最终闭环结果

## 执行建议

- Agent 1：执行 Chunk 1
- Agent 2：执行 Chunk 2
- Agent 3：执行 Chunk 3
- 主线程：盯共享命名、解决冲突、执行 Chunk 4

## 完成定义

- 运行态主链路不再暴露 demo 历史残留
- 轨迹和通知记录有真实查询来源
- 三个通知渠道具备真实 provider 能力
- 文档和契约与当前代码对齐
- 全量验证通过

Plan complete and saved to `docs/superpowers/plans/2026-03-23-post-phase6-platform-consolidation-plan.md`. Ready to execute?
