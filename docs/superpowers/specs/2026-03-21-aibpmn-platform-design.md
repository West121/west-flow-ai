# AIBPMN 审批流程平台总设计

> 状态：Current v2026-03-23
> 日期：2026-03-21，最近刷新：2026-03-23
> 适用范围：`main` 当前真实平台状态总览

## 1. 当前真实状态

当前仓库已经完成平台主链路的基础闭环，文档口径以真实代码为准，不再沿用 demo 时代描述。

当前已落地的核心事实：

- 流程运行时已经切到真实 `Flowable`，正式运行态入口为 `/api/v1/process-runtime/*`
- 登录认证已经切到真实数据库账号体系，认证框架为 `Sa-Token`
- `OA` 业务发起、工作台待办、审批单详情、主要任务动作已经形成真实闭环
- `AI` Copilot 已完成基础对话、工具命中、写操作确认、富响应块闭环
- `PLM` 已完成 `ECR / ECO / 物料主数据变更` 三类业务的发起、列表、详情、审批单双向联查基础闭环

## 2. 废弃口径

以下内容仅允许出现在历史归档文档里，不再代表当前平台契约：

- 旧 demo 运行态路径口径
- “当前仍以 demo 运行态为主”
- “登录仍是 demo / fixture / 假数据口径”
- “AI 与 PLM 仅为未来阶段”

说明：

- 历史任务快照类名统一按 `ProcessTaskSnapshot` 这一任务快照命名描述归档，不再在当前总设计中展开旧文件名
- 当前对外接口、路由和契约必须统一按正式运行时与正式业务能力描述

## 3. 当前平台结构

### 3.1 仓库结构

```text
/
├─ docs/
├─ frontend/
├─ backend/
├─ infra/
└─ scripts/
```

### 3.2 前端基线

- `Vite`
- `TanStack Router`
- `TanStack Query`
- `react-hook-form + zod`
- `shadcn/ui`
- `react-flow`

### 3.3 后端基线

- `Spring Boot`
- `JDK 21`
- `MyBatis-Plus`
- `PostgreSQL`
- `Flyway`
- `Redis`
- `MinIO`
- `Sa-Token`
- `Flowable`
- `LiteFlow`
- `Spring AI + Spring AI Alibaba`

### 3.4 后端领域边界

```text
com.westflow
├─ common
├─ identity
├─ system
├─ notification
├─ processdef
├─ processbinding
├─ processruntime
├─ approval
├─ oa
├─ plm
└─ ai
```

## 4. 当前能力快照

### 4.1 认证与权限

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/current-user`
- `POST /api/v1/auth/switch-context`
- 当前用户上下文已经包含菜单、权限、数据权限、代理关系和 `aiCapabilities`

### 4.2 流程运行时

- 正式入口为 `/api/v1/process-runtime/*`
- 已具备任务分页、审批单分页、任务详情、按业务查审批单
- 已具备 `claim / complete / transfer / return / reject / jump / take-back / wake-up / add-sign / remove-sign / revoke / urge / read / delegate / handover`

### 4.3 AI Copilot

- 已具备 Supervisor / Routing / 业务智能体的注册与路由
- 已具备 `task.query / task.handle / process.start / stats.query / plm.bill.query`
- 已具备富响应块：`text / confirm / form-preview / stats / result / failure / retry / trace`
- 写操作已进入确认流，读操作可直接执行

### 4.4 PLM

- 已具备 `PLM_ECR / PLM_ECO / PLM_MATERIAL`
- 已具备业务发起、业务列表、业务详情
- 已具备业务单到审批单、审批单到业务单的双向联查
- AI 已能对 PLM 单据做读查询与业务摘要

## 5. 当前路线图口径

`M0-M6` 的基础目标已经完成，旧的“先替换 demo 运行态、再接 AI + PLM”的描述不再作为当前行动路线。

当前剩余任务以 Phase 7 为准，见：

- `docs/superpowers/specs/2026-03-23-post-phase6-platform-consolidation-design.md`

已完成的旧路线图归档见：

- `docs/superpowers/specs/2026-03-22-remaining-roadmap-design.md`

## 6. 当前工作重点

当前平台剩余工作聚焦在“收口与深化”，而不是基础能力搭建：

- 运行态历史命名与轨迹收口
- 通知渠道生产化
- 文档与契约对齐
- AI 深化
- PLM 深化
- 前端工程化收尾
