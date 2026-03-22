import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const currentUserFixture = {
  userId: 'usr_001',
  username: 'zhangsan',
  displayName: '张三',
  mobile: '13800000000',
  email: 'zhangsan@example.com',
  avatar: '',
  companyId: 'cmp_001',
  activePostId: 'post_001',
  activeDepartmentId: 'dept_001',
  roles: ['OA_USER', 'DEPT_MANAGER'],
  permissions: ['oa:leave:create', 'workflow:task:approve'],
  dataScopes: [
    {
      scopeType: 'DEPARTMENT_AND_CHILDREN',
      scopeValue: 'dept_001',
    },
  ],
  partTimePosts: [
    {
      postId: 'post_002',
      departmentId: 'dept_002',
      postName: '项目助理',
    },
  ],
  delegations: [
    {
      principalUserId: 'usr_002',
      delegateUserId: 'usr_001',
      status: 'ACTIVE',
    },
  ],
  aiCapabilities: ['ai:copilot:open', 'ai:process:start', 'ai:task:handle'],
  menus: [
    {
      id: 'menu_oa_leave',
      title: '请假申请',
      path: '/oa/leave/list',
    },
  ],
}

function clearCookies() {
  document.cookie.split(';').forEach((cookie) => {
    const [name] = cookie.split('=')
    const trimmedName = name?.trim()

    if (trimmedName) {
      document.cookie = `${trimmedName}=; path=/; max-age=0`
    }
  })
}

describe('useAuthStore', () => {
  beforeEach(() => {
    clearCookies()
    vi.resetModules()
  })

  afterEach(() => {
    clearCookies()
  })

  it('hydrates the access token from cookie storage', async () => {
    document.cookie = 'west_flow_ai_access_token=cookie-token; path=/'

    const { useAuthStore } = await import('./auth-store')

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'cookie-token',
      currentUser: null,
    })
  })

  it('stores the frozen current-user contract and clears auth state on reset', async () => {
    const { useAuthStore } = await import('./auth-store')
    const state = useAuthStore.getState() as Record<string, unknown>

    expect(state).toMatchObject({
      accessToken: '',
      currentUser: null,
    })
    expect(state.setCurrentUser).toEqual(expect.any(Function))
    expect(state.setAccessToken).toEqual(expect.any(Function))
    expect(state.reset).toEqual(expect.any(Function))

    ;(state.setCurrentUser as (user: typeof currentUserFixture) => void)(
      currentUserFixture
    )
    ;(state.setAccessToken as (token: string) => void)('token-123')

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'token-123',
      currentUser: currentUserFixture,
    })
    expect(document.cookie).toContain('west_flow_ai_access_token=token-123')

    ;(state.reset as () => void)()

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: '',
      currentUser: null,
    })
  })
})
