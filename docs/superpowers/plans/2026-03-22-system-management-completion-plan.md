# Phase 2 系统管理运营后台补齐 Implementation Plan

> **For agentic workers:** REQUIRED: Use subagent-driven-development. 每条实现线独立开发、独立验证、最后统一集成。

**Goal:** 将系统管理补齐为完整运营后台，同时清理无关残留页面和重复菜单，形成 Phase 2 闭环。

**Architecture:** 以后端资源域为边界，拆成“字典+消息”“日志+监控”“文件+通知”“清理与菜单统一”四条并行线。主线程负责契约收口、冲突合并、全量验证与提交。

**Tech Stack:** `Spring Boot`, `MyBatis-Plus`, `Flyway`, `PostgreSQL`, `Redis`, `React 19`, `TanStack Router`, `TanStack Query`, `zustand`, `shadcn/ui`

---

## Chunk 1: 文档与契约冻结

### Task 1: 固定 Phase 2 设计与菜单结构

**Files:**
- Create: `docs/superpowers/specs/2026-03-22-system-management-completion-design.md`
- Create: `docs/superpowers/plans/2026-03-22-system-management-completion-plan.md`
- Modify: `docs/superpowers/specs/2026-03-22-remaining-roadmap-design.md`
- Modify: `docs/superpowers/plans/2026-03-22-remaining-roadmap-plan.md`

- [ ] 将 Phase 2 方案写入设计文档
- [ ] 将并行拆分与验证标准写入实施计划
- [ ] 在剩余路线图文档中补 Phase 2 专项文档引用

## Chunk 2: 字典管理与消息管理

### Task 2: 补齐字典管理后端与前端

**Files:**
- Create: `backend/src/main/java/com/westflow/system/dict/**`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `frontend/src/features/system/dict-pages.tsx`
- Create: `frontend/src/lib/api/system-dicts.ts`
- Create: `frontend/src/routes/_authenticated/system/dicts/**`

- [ ] 新增字典类型、字典项表结构与种子数据
- [ ] 提供字典类型、字典项分页/详情/新建/编辑接口
- [ ] 列表支持分页、模糊查询、筛选、排序、分组
- [ ] 前端提供独立列表、详情、新建、编辑页

### Task 3: 补齐消息管理后端与前端

**Files:**
- Create: `backend/src/main/java/com/westflow/system/message/**`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `frontend/src/features/system/message-pages.tsx`
- Create: `frontend/src/lib/api/system-messages.ts`
- Create: `frontend/src/routes/_authenticated/system/messages/**`

- [ ] 新增站内消息、消息投递记录表结构与种子数据
- [ ] 提供消息分页/详情/新建接口
- [ ] 支持已读未读、目标用户、发送状态查询
- [ ] 前端提供独立 CRUD 页面

## Chunk 3: 日志管理与监控管理

### Task 4: 补齐日志管理后端与前端

**Files:**
- Create: `backend/src/main/java/com/westflow/system/log/**`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `frontend/src/features/system/log-pages.tsx`
- Create: `frontend/src/lib/api/system-logs.ts`
- Create: `frontend/src/routes/_authenticated/system/logs/**`

- [ ] 统一审计日志、登录日志、通知发送日志、消息投递日志查询模型
- [ ] 提供分页与详情接口
- [ ] 支持时间范围、结果状态、用户、模块查询
- [ ] 前端提供独立查询页与详情页

### Task 5: 补齐监控管理后端与前端

**Files:**
- Create: `backend/src/main/java/com/westflow/system/monitor/**`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `frontend/src/features/system/monitor-pages.tsx`
- Create: `frontend/src/lib/api/system-monitor.ts`
- Create: `frontend/src/routes/_authenticated/system/monitor/**`

- [ ] 提供编排扫描记录、触发执行记录、通知渠道健康状态查询接口
- [ ] 复用现有 orchestrator、trigger、notification 数据
- [ ] 前端提供独立列表与详情页

## Chunk 4: 文件管理与通知管理扩展

### Task 6: 补齐文件管理后端与前端

**Files:**
- Create: `backend/src/main/java/com/westflow/system/file/**`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `frontend/src/features/system/file-pages.tsx`
- Create: `frontend/src/lib/api/system-files.ts`
- Create: `frontend/src/routes/_authenticated/system/files/**`

- [ ] 新增文件元数据表结构
- [ ] 提供上传、分页、详情、下载、逻辑删除接口
- [ ] 前端提供独立列表、详情、上传、新建页

### Task 7: 扩展通知管理后端与前端

**Files:**
- Create: `backend/src/main/java/com/westflow/system/notification/template/**`
- Create: `backend/src/main/java/com/westflow/system/notification/record/**`
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `frontend/src/features/system/notification-pages.tsx`
- Create: `frontend/src/lib/api/system-notifications.ts`
- Create: `frontend/src/routes/_authenticated/system/notifications/**`

- [ ] 新增通知模板与通知记录表结构
- [ ] 将通知渠道配置并入通知管理菜单结构
- [ ] 前端提供模板、记录、渠道的独立入口页

## Chunk 5: 清理项与菜单统一

### Task 8: 清理残留路由与菜单

**Files:**
- Delete: `frontend/src/routes/_authenticated/apps/**`
- Delete: `frontend/src/routes/_authenticated/chats/**`
- Delete: `frontend/src/routes/_authenticated/tasks/**`
- Delete: `frontend/src/routes/_authenticated/help-center/**`
- Modify: `frontend/src/components/layout/data/sidebar-data.ts`
- Modify: `frontend/src/routeTree.gen.ts`

- [ ] 删除无关残留路由
- [ ] 删除相关菜单入口
- [ ] 清除重复的系统管理菜单项
- [ ] 重构系统管理为明确分组结构

### Task 9: 权限点与联调收口

**Files:**
- Modify: `backend/src/main/resources/db/migration/V1__init.sql`
- Modify: `backend/src/main/java/com/westflow/identity/service/FixtureAuthService.java`
- Modify: `frontend/src/components/layout/data/sidebar-data.ts`

- [ ] 为新模块补权限点与种子角色
- [ ] 前后端菜单与权限点保持一致
- [ ] 确保 `SYSTEM_ADMIN` 可访问新增模块

## Chunk 6: 验证与提交

### Task 10: 统一验证并提交 Phase 2

**Files:**
- Verify all touched files

- [ ] 运行后端测试：`mvn -f backend/pom.xml test`
- [ ] 运行前端测试：`pnpm --dir frontend test --run`
- [ ] 运行前端类型检查：`pnpm --dir frontend typecheck`
- [ ] 运行前端静态检查：`pnpm --dir frontend lint`
- [ ] 运行前端构建：`pnpm --dir frontend build`
- [ ] 确认系统管理菜单不重复
- [ ] 确认残留路由已删除
- [ ] 提交 Phase 2 单独 commit
