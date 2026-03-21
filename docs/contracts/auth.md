# 用户上下文与权限模型

> 状态：Frozen v1
> Owner：后端权限 owner
> 生效里程碑：M0

## 目标

统一登录态、当前用户上下文、组织身份、兼任、代理、菜单权限、数据权限和 AI 能力权限模型。

## 登录协议

- 认证框架：`Sa-Token`
- 前端统一使用 `Authorization: Bearer <token>`
- 登录成功后，前端通过 `GET /api/v1/auth/current-user` 获取完整上下文

## 当前用户结构

```json
{
  "userId": "usr_001",
  "username": "zhangsan",
  "displayName": "张三",
  "mobile": "13800000000",
  "email": "zhangsan@example.com",
  "avatar": "",
  "companyId": "cmp_001",
  "activePostId": "post_001",
  "activeDepartmentId": "dept_001",
  "roles": ["OA_USER", "DEPT_MANAGER"],
  "permissions": ["oa:leave:create", "workflow:task:approve"],
  "dataScopes": [
    {
      "scopeType": "DEPARTMENT_AND_CHILDREN",
      "scopeValue": "dept_001"
    }
  ],
  "partTimePosts": [
    {
      "postId": "post_002",
      "departmentId": "dept_002",
      "postName": "项目助理"
    }
  ],
  "delegations": [
    {
      "principalUserId": "usr_002",
      "delegateUserId": "usr_001",
      "status": "ACTIVE"
    }
  ],
  "aiCapabilities": [
    "ai:copilot:open",
    "ai:process:start",
    "ai:task:handle"
  ],
  "menus": [
    {
      "id": "menu_oa_leave",
      "title": "请假申请",
      "path": "/oa/leave/list"
    }
  ]
}
```

## 权限模型

### 菜单权限

- 控制侧边栏显示
- 控制路由可访问性
- 每个 CRUD 功能页面单独授权，不用父菜单粗放放权

### 按钮/动作权限

- 控制创建、编辑、删除、导出、审批、撤销、催办等动作
- 前端只负责隐藏和禁用
- 后端必须二次校验

### 数据权限

M0 支持以下范围：

- `ALL`
- `SELF`
- `DEPARTMENT`
- `DEPARTMENT_AND_CHILDREN`
- `CUSTOM`

说明：

- 业务列表与待办列表均需要数据权限过滤
- AI 工具调用也必须继承当前用户数据权限

## 兼任与上下文切换

- 一个用户可拥有多个岗位
- M0 支持一个“当前激活岗位”
- 前端通过 `POST /api/v1/auth/switch-context` 切换当前岗位上下文
- 切换后重新拉取 `current-user`

## 代理与委派

### 代理

- 代理是预设关系
- 代理人处理后，委托人与代理人都能追踪记录

### 委派

- 委派是任务级动作
- 委派记录必须进入任务审计链路

## AI 权限

M0 必须区分以下能力：

- `ai:copilot:open`
- `ai:process:start`
- `ai:task:handle`
- `ai:stats:query`
- `ai:designer:generate`

规则：

- 用户无 `ai:copilot:open` 时，不展示 Copilot 入口
- 用户无具体能力权限时，AI 可以聊天，但不能调用对应工具

## 前端落地规则

- 当前用户上下文存放于统一鉴权 store
- 菜单、按钮、列表接口、AI 工具可见性均从同一上下文模型读取
- 返回主列表时必须保留当前查询态与当前用户上下文
