# PLM v3 Enterprise Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 PLM 从审批驱动的变更模块继续补齐到企业级变更工作区 v1，新增结构化受影响对象、实施/验证/关闭生命周期和详情执行面板。

**Architecture:** 保持三类业务域 `ECR / ECO / 物料主数据变更` 不变，在现有主表上补生命周期字段，同时新增统一的受影响对象从表。前端在创建与详情页引入结构化对象编辑和生命周期面板，AI 继续使用现有工具键但增强返回结构。

**Tech Stack:** Spring Boot, MyBatis, Flowable, React, TanStack Query, react-hook-form, zod

---

## Chunk 1: 后端数据模型与生命周期

### Task 1: 新增受影响对象表与 Mapper

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/model/PlmAffectedItemRecord.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/mapper/PlmAffectedItemMapper.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/resources/db/migration/*.sql`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/plm/api/PLMControllerTest.java`

- [ ] 增加 `plm_bill_affected_item` 表
- [ ] 增加按 `business_type + bill_id` 查询、删除、批量插入
- [ ] 增加测试覆盖结构化对象写入与读取

### Task 2: 扩展主表生命周期字段与动作

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/resources/db/migration/*.sql`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/model/*.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/mapper/*.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/service/PlmLaunchService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/PLMController.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/plm/api/PLMControllerTest.java`

- [ ] 新增 `IMPLEMENTING / VALIDATING / CLOSED`
- [ ] 新增开始实施接口
- [ ] 新增提交验证接口
- [ ] 新增关闭接口
- [ ] 覆盖状态流转测试

### Task 3: 详情聚合受影响对象与执行信息

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/*.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/service/PlmLaunchService.java`
- Test: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/plm/api/PLMControllerTest.java`

- [ ] 详情响应增加 `affectedItems`
- [ ] 详情响应增加实施、验证、关闭信息
- [ ] 详情测试覆盖新字段

## Chunk 2: 前端创建与详情工作区

### Task 4: 三类创建页增加受影响对象编辑器

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/plm.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/pages.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/pages.test.tsx`

- [ ] 扩展 payload 类型支持 `affectedItems`
- [ ] 新增可增删行的受影响对象编辑区
- [ ] 保证草稿保存和提交都能带结构化对象

### Task 5: 详情页增加受影响对象表与执行面板

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/plm.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/pages.tsx`
- Test: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/pages.test.tsx`

- [ ] 新增受影响对象展示表
- [ ] 新增实施、验证、关闭卡片
- [ ] 新增生命周期动作按钮
- [ ] 按状态控制按钮显隐

## Chunk 3: AI 与查询增强

### Task 6: 增强 PLM AI 结果结构

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/config/AiCopilotConfiguration.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java`

- [ ] 返回受影响对象数量与摘要
- [ ] 返回当前生命周期阶段
- [ ] 返回实施/验证/关闭摘要
- [ ] 增加“已审批完成但未关闭”类查询覆盖

## Chunk 4: 集成验证

### Task 7: 回归验证与文档更新

**Files:**
- Modify as needed based on integration

- [ ] 跑后端 PLM 与 AI 测试
- [ ] 跑前端 PLM 测试
- [ ] 跑前端 typecheck
- [ ] 手工验证三类单据的草稿、提交、实施、验证、关闭链路

