import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { getMock, postMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
}))

vi.mock('@/lib/api/client', () => ({
  apiClient: {
    get: getMock,
    post: postMock,
  },
  unwrapResponse: <T>(response: { data: { data: T } }) => response.data.data,
}))

const loginResponseFixture = {
  accessToken: 'token-123',
  tokenType: 'Bearer',
  expiresIn: 7200,
}

const currentUserFixture = {
  userId: 'usr_001',
  username: 'zhangsan',
  displayName: '张三',
  mobile: '13800000000',
  email: 'zhangsan@example.com',
  avatar: '',
  companyId: 'cmp_001',
  activePostId: 'post_002',
  activeDepartmentId: 'dept_002',
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

function okResponse<T>(data: T) {
  return {
    data: {
      code: 'OK',
      message: 'success',
      data,
      requestId: 'req_001',
    },
  }
}

describe('auth api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('logs in against the frozen auth endpoint and returns the bearer token payload', async () => {
    postMock.mockResolvedValue(okResponse(loginResponseFixture))

    const { login } = await import('./auth')

    await expect(
      login({
        username: 'zhangsan',
        password: 'password123',
      })
    ).resolves.toEqual(loginResponseFixture)

    expect(postMock).toHaveBeenCalledWith('/auth/login', {
      username: 'zhangsan',
      password: 'password123',
    })
  })

  it('switches context then re-fetches current-user before returning the refreshed contract', async () => {
    postMock.mockResolvedValue(okResponse(null))
    getMock.mockResolvedValue(okResponse(currentUserFixture))

    const { getCurrentUser, switchContext } = await import('./auth')

    await expect(getCurrentUser()).resolves.toEqual(currentUserFixture)
    await expect(
      switchContext({
        activePostId: 'post_002',
      })
    ).resolves.toEqual(currentUserFixture)

    expect(postMock).toHaveBeenCalledWith('/auth/switch-context', {
      activePostId: 'post_002',
    })
    expect(getMock).toHaveBeenNthCalledWith(1, '/auth/current-user')
    expect(getMock).toHaveBeenNthCalledWith(2, '/auth/current-user')
  })
})
