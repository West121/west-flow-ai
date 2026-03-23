# 用户上下文与权限模型

> 状态：Current v2026-03-23
> Owner：后端权限 owner
> 适用范围：真实数据库登录、当前用户上下文与 AI 权限

## 1. 目标

统一登录、当前用户上下文、组织身份、兼任、代理、菜单权限、数据权限和 AI 能力权限模型。

本协议以当前真实认证实现为准，不再使用 demo 或 fixture 账号口径。

## 2. 登录协议

- 认证框架：`Sa-Token`
- 登录接口：`POST /api/v1/auth/login`
- 前端统一使用 `Authorization: Bearer <token>`
- 登录成功后，前端通过 `GET /api/v1/auth/current-user` 获取完整上下文

登录成功响应当前至少包含：

```json
{
  "accessToken": "token-value",
  "tokenType": "Bearer",
  "expiresIn": 7200
}
```

## 3. 当前用户结构

`GET /api/v1/auth/current-user` 当前返回结构如下：

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

## 4. 上下文切换

- 切换接口：`POST /api/v1/auth/switch-context`
- 当前请求体只接受 `activePostId`
- 切换成功后应重新拉取 `current-user`

请求体示例：

```json
{
  "activePostId": "post_002"
}
```

## 5. 权限模型

### 菜单权限

- 控制侧边栏显示
- 控制路由可访问性
- 由 `menus` 与 `permissions` 共同驱动

### 按钮与动作权限

- 控制创建、编辑、删除、审批、撤销、催办等动作
- 前端只负责隐藏和禁用
- 后端必须二次校验

### 数据权限

当前已使用的数据范围：

- `ALL`
- `SELF`
- `DEPARTMENT`
- `DEPARTMENT_AND_CHILDREN`
- `CUSTOM`

规则：

- 业务列表、待办列表、审批联查都必须继承当前用户数据权限
- AI 工具调用也必须继承当前用户的数据权限与能力权限

## 6. 兼任、代理与委派

### 兼任

- 一个用户可拥有多个岗位
- `activePostId` 表示当前激活岗位
- `partTimePosts` 表示可切换的兼任岗位集合

### 代理

- 代理是预设关系
- 当前上下文通过 `delegations` 返回代理信息

### 委派

- 委派是任务级动作
- 委派记录进入运行时任务审计链路，不通过认证接口直接处理

## 7. AI 权限

当前协议使用 `aiCapabilities` 表示 AI 可用能力，例如：

- `ai:copilot:open`
- `ai:process:start`
- `ai:task:handle`
- `ai:stats:query`
- `ai:plm:assist`
- `ai:designer:generate`

规则：

- 用户无 `ai:copilot:open` 时，不展示 Copilot 入口
- 用户无具体能力权限时，AI 可以保留基础对话，但不能命中对应工具

## 8. 废弃口径

以下说法已废弃：

- “当前登录仍是 demo 账号”
- “认证上下文只包含 mock 菜单或假权限”
- “AI 能力权限未来再接”
