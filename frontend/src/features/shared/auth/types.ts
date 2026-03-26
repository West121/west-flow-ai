// 认证与会话相关的前端类型集中在这里，供登录、切换上下文和权限判断复用。
export const AUTH_ACCESS_TOKEN_COOKIE = 'west_flow_ai_access_token'

// 数据权限范围，描述当前用户在某个业务维度上可见的数据边界。
export type DataScope = {
  scopeType: string
  scopeValue: string
}

// 用户可兼任的岗位信息。
export type PartTimePost = {
  postId: string
  departmentId: string
  departmentName: string
  companyId: string
  companyName: string
  postName: string
  roleIds: string[]
  roleNames: string[]
  enabled: boolean
}

export type PostAssignment = {
  postId: string
  departmentId: string
  departmentName: string
  companyId: string
  companyName: string
  postName: string
  roleIds: string[]
  roleNames: string[]
  primary: boolean
  enabled: boolean
}

// 代理关系，描述当前用户把什么角色委托给谁处理。
export type Delegation = {
  principalUserId: string
  delegateUserId: string
  status: string
}

// 菜单项的最小前端表示。
export type MenuItem = {
  id: string
  title: string
  path: string
}

// 当前登录用户在前端需要直接消费的会话信息。
export type CurrentUser = {
  userId: string
  username: string
  displayName: string
  mobile: string
  email: string
  avatar: string
  companyId: string
  companyName: string
  activePostId: string
  activePostName: string
  activeDepartmentId: string
  activeDepartmentName: string
  roles: string[]
  permissions: string[]
  dataScopes: DataScope[]
  partTimePosts: PartTimePost[]
  postAssignments: PostAssignment[]
  delegations: Delegation[]
  aiCapabilities: string[]
  menus: MenuItem[]
}

// 登录表单提交参数。
export type LoginRequest = {
  username: string
  password: string
}

// 登录接口返回的访问令牌信息。
export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
}

// 切换当前激活岗位时使用的请求参数。
export type SwitchContextRequest = {
  activePostId: string
}
