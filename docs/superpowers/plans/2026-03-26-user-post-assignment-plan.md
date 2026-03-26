# 用户任职与兼职模型 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把兼职统一建模为“任职记录”，补齐用户管理中的主职/兼职维护，并接通登录后的任职上下文切换。

**Architecture:** 以后端 `wf_user_post + wf_user_post_role` 为核心，保留 `wf_user.active_post_id` 作为兼容摘要字段。前端用户管理页维护任职列表，认证与菜单/权限继续围绕 `activePostId` 运行。

**Tech Stack:** Spring Boot, MyBatis-Plus/MyBatis Mapper, PostgreSQL Flyway, React, TanStack Query, react-hook-form, zod

---

## Chunk 1: 后端数据模型与迁移

### Task 1: 补任职角色关系表

**Files:**
- Create: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/resources/db/migration/V12__user_post_role.sql`

- [ ] **Step 1: 写迁移**

创建：
- `wf_user_post_role`
  - `id`
  - `user_post_id`
  - `role_id`
  - `created_at`

并补必要索引与唯一约束。

- [ ] **Step 2: 为现有主职数据做兼容初始化**

用当前 `wf_user_role` 和主职 `wf_user_post.is_primary = true` 初始化 `wf_user_post_role`。

- [ ] **Step 3: 启动迁移验证**

Run: `mvn -q -f backend/pom.xml -DskipTests spring-boot:run -Dspring-boot.run.profiles=local`

Expected:
- Flyway 迁移通过
- 无建表错误

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V12__user_post_role.sql
git commit -m "feat: 新增用户任职角色关系表"
```

## Chunk 2: 后端用户模型与接口

### Task 2: 扩展用户保存请求与详情响应

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/system/user/request/SaveSystemUserRequest.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/system/user/response/SystemUserDetailResponse.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/system/user/response/SystemUserFormOptionsResponse.java`

- [ ] **Step 1: 扩展请求结构**

在 `SaveSystemUserRequest` 中增加：
- `primaryAssignment`
- `partTimeAssignments`

Assignment 包含：
- `companyId`
- `postId`
- `roleIds`
- `enabled`

- [ ] **Step 2: 扩展详情结构**

详情返回：
- `primaryAssignment`
- `partTimeAssignments`

同时保留现有：
- `departmentId`
- `departmentName`
- `postId`
- `postName`

用于兼容现有列表和详情页。

- [ ] **Step 3: 表单选项保持兼容**

继续返回：
- 公司选项
- 岗位选项
- 角色选项

不新增复杂级联接口，避免第一轮过度设计。

- [ ] **Step 4: Run compile**

Run: `mvn -q -f backend/pom.xml -DskipTests compile`

Expected: PASS

### Task 3: 扩展 Mapper 与 Service 保存任职

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/system/user/mapper/SystemUserMapper.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/system/user/service/SystemUserService.java`

- [ ] **Step 1: 新增任职与任职角色读写 SQL**

补：
- 查询用户任职列表
- 删除用户任职角色
- 删除用户任职
- 插入用户任职角色

- [ ] **Step 2: 新建用户时写主职 + 兼职**

创建用户时：
- 主职写入 `wf_user_post`
- 兼职写入 `wf_user_post`
- 每条任职写入 `wf_user_post_role`
- 主职同步回写 `wf_user.active_post_id` 和 `active_department_id`

- [ ] **Step 3: 更新用户时全量重建任职**

更新用户时：
- 先删旧任职角色
- 再删旧任职
- 重新插入主职和兼职

- [ ] **Step 4: 保持旧 `wf_user_role` 兼容**

短期策略：
- 用主职角色回填 `wf_user_role`
或
- 聚合所有任职角色写回 `wf_user_role`

实现时选一种并保持认证口径一致。

- [ ] **Step 5: 写后端测试**

Test:
- 新建带兼职用户
- 编辑带兼职用户
- 查询详情能返回主职和兼职任职

Files:
- `/Users/west/dev/code/west/west-flow-ai/backend/src/test/java/com/westflow/system/user/controller/SystemUserControllerTest.java`

- [ ] **Step 6: Run tests**

Run: `mvn -q -f backend/pom.xml -Dtest=SystemUserControllerTest test`

Expected: PASS

## Chunk 3: 认证与上下文切换

### Task 4: 让当前用户返回完整任职摘要

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/identity/mapper/AuthUserMapper.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/identity/service/DatabaseAuthService.java`
- Modify: `/Users/west/dev/code/west/west-flow-ai/backend/src/main/java/com/westflow/identity/dto/CurrentUserResponse.java`

- [ ] **Step 1: 扩展兼职岗位返回结构**

返回不再只是：
- `postId`
- `departmentId`
- `postName`

而是至少补：
- `departmentName`
- `companyId`
- `companyName`
- `roleIds`
- `roleNames`

- [ ] **Step 2: 切换上下文保持现有主键**

`switchContext(activePostId)` 继续成立，不改接口路径。

- [ ] **Step 3: 验证 current-user**

Run: 针对认证测试或本地接口验证，确认切换后 `activePostId` 更新且返回岗位信息正确。

## Chunk 4: 前端用户管理页

### Task 5: 把用户页改成任职模型

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/system/user-pages.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/system-users.ts`

- [ ] **Step 1: 扩展前端表单 schema**

从：
- `companyId`
- `primaryPostId`
- `roleIds`

改成：
- `primaryAssignment`
- `partTimeAssignments`

- [ ] **Step 2: 主职卡片**

主职卡片支持：
- 公司
- 岗位
- 角色多选
- 启用状态

- [ ] **Step 3: 兼职任职列表**

支持：
- 新增兼职任职
- 删除兼职任职
- 每条兼职选公司/岗位/角色/启用状态

- [ ] **Step 4: 预览区改成任职摘要**

当前预览改显示：
- 主职
- 兼职数
- 当前主职角色

- [ ] **Step 5: 前端测试**

Test:
- 表单初始化
- 新增兼职
- 删除兼职
- 保存 payload 正确

Files:
- `/Users/west/dev/code/west/west-flow-ai/frontend/src/features/system/user-pages.test.tsx`

- [ ] **Step 6: Run tests**

Run:
- `pnpm -C frontend exec vitest run src/features/system/user-pages.test.tsx --reporter=verbose`
- `pnpm -C frontend typecheck`

Expected: PASS

## Chunk 5: 前端上下文切换器

### Task 6: 把顶部切换器接到真实任职上下文

**Files:**
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/components/layout/team-switcher.tsx`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/lib/api/auth.ts`
- Modify: `/Users/west/dev/code/west/west-flow-ai/frontend/src/stores/auth-store.ts`

- [ ] **Step 1: 读取当前用户任职列表**

切换器展示：
- 岗位名
- 部门名
- 主职/兼职标识

- [ ] **Step 2: 调用 switchContext**

点击任职后：
- 调 `switchContext(activePostId)`
- 刷新当前用户
- 刷新依赖当前人上下文的菜单/页面数据

- [ ] **Step 3: 验证交互**

Run:
- 本地登录
- 切换任职
- 确认当前岗位显示变化

## Chunk 6: 总体验证与提交

### Task 7: 全链路验证

**Files:**
- Modify as needed from previous tasks only

- [ ] **Step 1: 后端专项测试**

Run:
- `mvn -q -f backend/pom.xml -Dtest=SystemUserControllerTest test`

- [ ] **Step 2: 前端专项测试**

Run:
- `pnpm -C frontend exec vitest run src/features/system/user-pages.test.tsx --reporter=verbose`
- `pnpm -C frontend typecheck`
- `pnpm -C frontend lint`

- [ ] **Step 3: 手工验证**

验证：
- 新建带兼职用户
- 编辑兼职用户
- 登录后看到多个任职
- 切换上下文成功

- [ ] **Step 4: Commit**

```bash
git add backend frontend docs/plans/2026-03-26-user-post-assignment-design.md docs/superpowers/plans/2026-03-26-user-post-assignment-plan.md
git commit -m "feat: 支持用户任职与兼职上下文管理"
```

Plan complete and saved to `docs/superpowers/plans/2026-03-26-user-post-assignment-plan.md`. Ready to execute?
