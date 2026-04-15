# PLM 项目管理完整功能 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为现有 PLM 变更管理模块补齐完整项目管理层，包括项目台账、项目详情、阶段/里程碑、项目团队、关联变更与对象、项目驾驶舱和 AI 项目摘要。

**Architecture:** 在现有 `PLMController + PlmLaunchService + frontend/src/features/plm/pages.tsx` 体系上新增独立项目域，不拆现有变更域。后端通过 `plm_project*` 系列表建模项目对象及其关联；前端新增 `/plm/projects*` 路由与项目工作区页面，并复用当前 PLM 工作区组件风格。

**Tech Stack:** Spring Boot 3.5、MyBatis 注解 Mapper、PostgreSQL Flyway migration、React 19、TanStack Router、TanStack Query、React Hook Form、Vitest。

---

## Chunk 1: 数据模型与后端接口

### Task 1: 新增项目域数据库结构

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/resources/db/migration/V25__plm_project_management.sql`

- [ ] **Step 1: 编写 migration**

新增表：
- `plm_project`
- `plm_project_member`
- `plm_project_milestone`
- `plm_project_link`
- `plm_project_stage_event`

要求：
- 所有主键使用 `VARCHAR(64)`
- `created_at/updated_at` 默认当前时间
- 对 `project_no`、`project_id`、`milestone_code`、`role_code` 等关键字段加索引

- [ ] **Step 2: 编译验证 migration 可被 Flyway 识别**

Run: `/Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f /Users/west/dev/code/west/west-flow-ai/backend/pom.xml -DskipTests compile`
Expected: PASS

### Task 2: 新增项目域 model 与 mapper

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/model/PlmProjectRecord.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/model/PlmProjectMemberRecord.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/model/PlmProjectMilestoneRecord.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/model/PlmProjectLinkRecord.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/model/PlmProjectStageEventRecord.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/mapper/PlmProjectMapper.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/mapper/PlmProjectMemberMapper.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/mapper/PlmProjectMilestoneMapper.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/mapper/PlmProjectLinkMapper.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/mapper/PlmProjectStageEventMapper.java`

- [ ] **Step 1: 参照现有 PLM 风格实现 record model**

参考：
- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/model/PlmEcrBillRecord.java`

- [ ] **Step 2: 参照现有 Mapper 风格实现 CRUD 与分页查询**

参考：
- `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/mapper/PlmEcrBillMapper.java`

- [ ] **Step 3: 编译验证**

Run: `/Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f /Users/west/dev/code/west/west-flow-ai/backend/pom.xml -DskipTests compile`
Expected: PASS

### Task 3: 新增项目域 API response / request

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/CreatePlmProjectRequest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/UpdatePlmProjectRequest.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/PlmProjectListItemResponse.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/PlmProjectDetailResponse.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/PlmProjectMemberResponse.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/PlmProjectMilestoneResponse.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/PlmProjectLinkResponse.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/PlmProjectDashboardResponse.java`
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/PlmProjectPhaseTransitionRequest.java`

- [ ] **Step 1: 定义项目主对象请求与响应**
- [ ] **Step 2: 定义成员、里程碑、关联、驾驶舱响应**
- [ ] **Step 3: 编译验证**

Run: `/Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f /Users/west/dev/code/west/west-flow-ai/backend/pom.xml -DskipTests compile`
Expected: PASS

### Task 4: 新增项目域 service 并接入控制器

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/service/PlmProjectService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/plm/api/PLMController.java`

- [ ] **Step 1: 实现项目创建、更新、分页、详情**
- [ ] **Step 2: 实现项目成员、里程碑、关联关系读写**
- [ ] **Step 3: 实现阶段流转与阶段事件记录**
- [ ] **Step 4: 实现项目驾驶舱聚合**
- [ ] **Step 5: 在 `PLMController` 暴露 `/projects*` 接口**

- [ ] **Step 6: 编写后端控制器测试**

Test:
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/plm/api/PLMControllerTest.java`

覆盖：
- 创建项目
- 项目分页
- 项目详情
- 成员与里程碑维护
- 关联变更单
- 项目驾驶舱

- [ ] **Step 7: 运行测试**

Run: `/Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f /Users/west/dev/code/west/west-flow-ai/backend/pom.xml -Dtest=PLMControllerTest test`
Expected: PASS

## Chunk 2: 前端项目台账与详情

### Task 5: 扩展前端 API 客户端

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/plm.ts`

- [ ] **Step 1: 新增项目域类型**
- [ ] **Step 2: 新增项目域请求函数**
- [ ] **Step 3: 保持与现有 `plm.ts` 风格一致**

- [ ] **Step 4: 运行前端类型检查**

Run: `pnpm -C /Users/west/dev/code/west/west-flow-ai/frontend typecheck`
Expected: PASS

### Task 6: 新增项目管理路由

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/routes/_authenticated/plm/projects/index.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/routes/_authenticated/plm/projects/create.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/routes/_authenticated/plm/projects/$projectId.tsx`

- [ ] **Step 1: 按现有 PLM 路由风格创建路由文件**
- [ ] **Step 2: 挂接列表、创建、详情页面**

参考：
- `/Users/west/dev/code/west/west-flow-ai/frontend/src/routes/_authenticated/plm/ecr/index.tsx`
- `/Users/west/dev/code/west/west-flow-ai/frontend/src/routes/_authenticated/plm/ecr/create.tsx`
- `/Users/west/dev/code/west/west-flow-ai/frontend/src/routes/_authenticated/plm/ecr/$billId.tsx`

### Task 7: 新增项目页面与组件

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/pages.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/components/plm-project-dashboard-panel.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/components/plm-project-member-panel.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/components/plm-project-milestone-panel.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/components/plm-project-link-panel.tsx`
- Create: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/components/plm-project-overview-panel.tsx`

- [ ] **Step 1: 在 `pages.tsx` 中新增项目台账页**
- [ ] **Step 2: 在 `pages.tsx` 中新增项目创建页**
- [ ] **Step 3: 在 `pages.tsx` 中新增项目详情页**
- [ ] **Step 4: 用独立组件拆出概览、成员、里程碑、关联、驾驶舱**

- [ ] **Step 5: 编写前端页面测试**

Test:
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/plm/pages.test.tsx`

覆盖：
- 项目台账
- 项目详情
- 成员面板
- 里程碑面板
- 关联变更与驾驶舱

- [ ] **Step 6: 运行测试**

Run:
- `pnpm -C /Users/west/dev/code/west/west-flow-ai/frontend typecheck`
- `pnpm -C /Users/west/dev/code/west/west-flow-ai/frontend exec vitest run src/features/plm/pages.test.tsx --reporter=verbose`

Expected: PASS

## Chunk 3: AI 项目助手与集成收尾

### Task 8: 扩展 AI 项目摘要

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/config/AiCopilotConfiguration.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/ai/service/DbAiCopilotService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java`

- [ ] **Step 1: 新增 `plm.project.query / plm.project.summary` 输出**
- [ ] **Step 2: 把项目详情、里程碑、关联、驾驶舱摘要接入 AI**
- [ ] **Step 3: 增加 AI 侧测试**

- [ ] **Step 4: 运行测试**

Run:
- `/Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f /Users/west/dev/code/west/west-flow-ai/backend/pom.xml -Dtest=AiCopilotServiceTest test`

Expected: PASS

### Task 9: 文档、验证与发布收尾

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/docs/plans/2026-04-15-plm-project-management-design.md`
- Create: `/Users/west/dev/code/west/west-flow-ai/docs/plans/2026-04-15-plm-project-management-release-notes.md`

- [ ] **Step 1: 根据实际实现回填设计文档**
- [ ] **Step 2: 写发布说明**
- [ ] **Step 3: 跑完整验证**

Run:
- `pnpm -C /Users/west/dev/code/west/west-flow-ai/frontend typecheck`
- `pnpm -C /Users/west/dev/code/west/west-flow-ai/frontend exec vitest run src/features/plm/pages.test.tsx --reporter=verbose`
- `/Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f /Users/west/dev/code/west/west-flow-ai/backend/pom.xml -Dtest=PLMControllerTest,AiCopilotServiceTest test`
- `/Users/west/dev/env/maven/apache-maven-3.9.11/bin/mvn -q -f /Users/west/dev/code/west/west-flow-ai/backend/pom.xml -DskipTests compile`

Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/resources/db/migration/V25__plm_project_management.sql \
  backend/src/main/java/com/westflow/plm \
  backend/src/test/java/com/westflow/plm/api/PLMControllerTest.java \
  backend/src/main/java/com/westflow/ai \
  backend/src/test/java/com/westflow/ai/service/AiCopilotServiceTest.java \
  frontend/src/lib/api/plm.ts \
  frontend/src/features/plm \
  frontend/src/routes/_authenticated/plm/projects \
  docs/plans/2026-04-15-plm-project-management-design.md \
  docs/plans/2026-04-15-plm-project-management-release-notes.md \
  docs/superpowers/plans/2026-04-15-plm-project-management-plan.md
git commit -m "feat: 增强PLM项目管理完整能力"
```

Plan complete and saved to `docs/superpowers/plans/2026-04-15-plm-project-management-plan.md`. Ready to execute.
