# 用户任职与兼职模型设计

## 背景

当前系统已经有 `activePostId`、`partTimePosts` 和 `switchContext` 基础能力，但“用户管理”仍只维护：

- 所属公司
- 主岗位
- 角色集合

这会导致“兼职”语义不完整。真实业务里，兼职不是单纯多一个角色或岗位，而是一条完整的任职关系，至少应包含：

- 所属公司
- 所属部门
- 所属岗位
- 任职附带角色
- 是否主职
- 是否启用

## 目标

把“兼职”统一建模为“任职记录（Post Assignment）”，让用户以不同任职上下文进入系统，并让审批、数据权限、菜单权限都能依赖当前任职生效。

## 设计原则

### 1. 兼职是任职，不是字段

用户可以拥有多条任职记录：

- 1 条主职
- N 条兼职

每条任职记录都明确绑定：

- `companyId`
- `departmentId`
- `postId`
- `roleIds`
- `isPrimary`
- `enabled`

### 2. 当前上下文按任职切换

登录后用户的工作上下文不再是“当前角色”，而是“当前任职”：

- `activePostId`

切换任职时，系统应同步改变：

- 当前部门
- 当前岗位
- 当前角色集合
- 数据权限计算口径

### 3. 用户主表只保留主身份摘要

`wf_user` 继续保留：

- `company_id`
- `active_department_id`
- `active_post_id`

它们作为当前主上下文摘要字段存在，便于现有查询兼容。更完整的任职关系从 `wf_user_post` 与新增的任职角色绑定表读取。

## 推荐方案

### 方案 A：仅扩展用户表

在 `wf_user` 上增加更多岗位/角色字段。

优点：
- 改动少

缺点：
- 不适合多条兼职
- 角色和岗位耦合混乱
- 无法表达任职级别的启停与权限

### 方案 B：以任职记录为中心建模（推荐）

保留 `wf_user_post` 作为任职主表，并补齐“任职角色”关系。

优点：
- 语义正确
- 和现有 `activePostId / switchContext` 一致
- 适合审批、权限、数据范围按任职切换

缺点：
- 用户管理页和认证查询需要一起调整

### 方案 C：完全独立的任职域模型

新建 `wf_user_assignment` 等全新结构，逐步替换 `wf_user_post`。

优点：
- 最纯粹

缺点：
- 迁移成本过大
- 当前阶段收益不成比例

## 最终方案

采用 **方案 B**。

### 数据模型

保留：

- `wf_user`
- `wf_user_post`
- `wf_user_role`

新增：

- `wf_user_post_role`

语义：

- `wf_user_post` 表示“用户有哪些任职”
- `wf_user_post_role` 表示“这条任职绑定哪些角色”
- `wf_user_role` 先作为兼容层保留，短期由主职角色或聚合角色回填

### 接口模型

用户详情新增：

- `primaryAssignment`
- `partTimeAssignments`

每条任职记录返回：

- `postId`
- `postName`
- `departmentId`
- `departmentName`
- `companyId`
- `companyName`
- `roleIds`
- `roleNames`
- `primary`
- `enabled`

用户保存接口改为：

- 基础信息
- `primaryAssignment`
- `partTimeAssignments`

### 前端模型

用户管理页不再只维护“主岗位 + 角色”，而是：

- 基础信息
- 主职任职
- 兼职任职列表

每条任职都可选：

- 公司
- 岗位
- 角色
- 是否启用

### 上下文切换

登录后保留：

- `activePostId`
- `partTimePosts`

但前端顶部切换器应改成真实“任职切换器”，显示：

- 部门
- 岗位
- 主职/兼职标识

## 兼容策略

### 短期兼容

- 列表页继续展示主职部门、主职岗位
- 现有依赖 `wf_user.active_post_id` 的逻辑不立即推翻
- `switchContext` 仍以 `postId` 为上下文主键

### 中期过渡

- 审批选人按当前任职获取角色和部门
- 数据权限按当前任职绑定角色聚合计算
- 菜单权限按当前任职角色聚合计算

## 成功标准

- 用户管理页能维护主职与多条兼职任职
- 登录后能切换任职上下文
- 当前用户接口能返回完整兼职任职信息
- 不同任职的角色差异能影响菜单/数据权限计算
- 保持现有登录和审批主链路不回归
