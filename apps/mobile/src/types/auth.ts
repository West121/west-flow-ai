export type DataScope = {
  scopeType: string
  scopeValue: string
}

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

export type LoginRequest = {
  username: string
  password: string
}

export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
}
