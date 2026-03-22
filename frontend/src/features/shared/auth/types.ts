export const AUTH_ACCESS_TOKEN_COOKIE = 'west_flow_ai_access_token'

export type DataScope = {
  scopeType: string
  scopeValue: string
}

export type PartTimePost = {
  postId: string
  departmentId: string
  postName: string
}

export type Delegation = {
  principalUserId: string
  delegateUserId: string
  status: string
}

export type MenuItem = {
  id: string
  title: string
  path: string
}

export type CurrentUser = {
  userId: string
  username: string
  displayName: string
  mobile: string
  email: string
  avatar: string
  companyId: string
  activePostId: string
  activeDepartmentId: string
  roles: string[]
  permissions: string[]
  dataScopes: DataScope[]
  partTimePosts: PartTimePost[]
  delegations: Delegation[]
  aiCapabilities: string[]
  menus: MenuItem[]
}

export type LoginRequest = {
  username: string
  password: string
}

export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export type SwitchContextRequest = {
  activePostId: string
}
