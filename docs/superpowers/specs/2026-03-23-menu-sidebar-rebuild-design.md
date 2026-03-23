# 2026-03-23 菜单与 Sidebar 体系重构设计

## 目标

本轮只收口菜单体系，不夹带高级流程能力、AI 深化或 OA/PLM 业务深化。

目标有三条：

1. 左侧 sidebar 改成完全按后端查询结果渲染，不再使用前端静态菜单树兜底。
2. 菜单管理改成树形表格管理，菜单只维护一套层级与排序。
3. 初始化菜单 SQL 重排成统一产品结构，并把创建/编辑/详情/执行等子页面改成权限型节点，不展示到 sidebar。

## 菜单模型

`wf_menu.menu_type` 统一收口为三种：

- `DIRECTORY`
  - 目录节点
  - 仅承载层级和分组
  - 可展示到 sidebar
- `MENU`
  - 可导航页面
  - 可展示到 sidebar
- `PERMISSION`
  - 作为路由子页面或动作权限标识
  - 不展示到 sidebar

`PERMISSION` 节点覆盖：

- `create`
- `edit`
- `detail`
- `execute`
- `approve`
- `publish`
- `delete`
- 其他只用于守卫和授权的页面/动作

## Sidebar 查询规则

前端 sidebar 不再维护业务菜单树。

前端只保留：

- 图标名到组件的映射
- 团队/品牌展示信息
- 当前登录用户信息

sidebar 查询结果来自后端菜单树接口，过滤规则固定为：

- `enabled = true`
- `visible = true`
- `menuType in (DIRECTORY, MENU)`
- 且必须是当前登录用户有权限的节点

前端不再按本地 `sidebar-data.ts` 拼功能树。

## 菜单树接口

新增菜单树接口，供 sidebar 和菜单管理页共用：

- `GET /api/v1/system/menus/sidebar-tree`
  - 返回当前用户可见的目录/菜单树
- `GET /api/v1/system/menus/tree`
  - 返回完整菜单树，供菜单管理页使用

保留已有：

- `POST /api/v1/system/menus/page`
- `GET /api/v1/system/menus/{menuId}`
- `GET /api/v1/system/menus/options`
- `POST /api/v1/system/menus`
- `PUT /api/v1/system/menus/{menuId}`

其中树接口返回结构需包含：

- `menuId`
- `parentMenuId`
- `menuName`
- `menuType`
- `routePath`
- `permissionCode`
- `iconName`
- `sortOrder`
- `visible`
- `enabled`
- `children`

## 菜单管理页

菜单管理页从平铺分页表改成树形表格：

- 展开/折叠层级
- 显示目录、菜单、权限三种类型
- 显示父级、路由、权限码、排序、显示/隐藏、状态
- 支持新增子目录/子菜单/子权限
- 支持编辑节点

本轮不做拖拽排序，只保留：

- 修改 `sortOrder`
- 修改 `parentMenuId`

## 初始化 SQL 重排

顶级目录顺序统一为：

1. 工作台
2. 组织管理
3. 系统管理
4. 流程中心
5. 流程管理
6. OA
7. PLM

其中“组织管理”从现有“系统管理”内部拆成顶级目录，与“系统管理”并列。

需要检查并补齐菜单种子：

- 工作台
  - 平台总览
  - 待办
- 流程中心
  - 待办
  - 已办
  - 我发起
  - 抄送我
  - 发起流程
- 流程管理
  - 流程定义
  - 流程设计
  - 流程版本
  - 发布记录
  - 实例监控
  - 操作日志
  - 审批意见配置
  - 业务流程绑定
- OA
  - 请假申请
  - 报销申请
  - 通用申请
  - OA 流程查询
- PLM
  - 发起中心
  - ECR
  - ECO
  - 物料主数据变更
  - PLM 流程查询

子页面和动作页改成：

- `menu_type = PERMISSION`
- `visible = false`

## 实现顺序

本轮只做 `Phase M1`：

1. 后端菜单树查询接口
2. 初始化 SQL 重排和补齐
3. 前端动态 sidebar
4. 菜单管理树形表格

后续阶段再继续：

1. 更高级流程能力
2. AI 深化
3. OA / PLM 业务深化
