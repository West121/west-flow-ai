import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { navigateMock, authApiMocks, authStoreMocks, toastSuccessMock } =
  vi.hoisted(() => ({
    navigateMock: vi.fn(),
    authApiMocks: {
      login: vi.fn(),
      getCurrentUser: vi.fn(),
    },
    authStoreMocks: {
      setAccessToken: vi.fn(),
      setCurrentUser: vi.fn(),
    },
    toastSuccessMock: vi.fn(),
  }))

vi.mock('@tanstack/react-router', () => ({
  useSearch: () => ({ redirect: '/workbench/todos/list' }),
  useNavigate: () => navigateMock,
}))

vi.mock('../auth-layout', () => ({
  AuthLayout: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
}))

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: (
    selector: (state: {
      setAccessToken: typeof authStoreMocks.setAccessToken
      setCurrentUser: typeof authStoreMocks.setCurrentUser
    }) => unknown
  ) =>
    selector({
      setAccessToken: authStoreMocks.setAccessToken,
      setCurrentUser: authStoreMocks.setCurrentUser,
    }),
}))

vi.mock('@/lib/api/auth', () => authApiMocks)

vi.mock('sonner', () => ({
  toast: {
    success: toastSuccessMock,
  },
}))

describe('SignIn smoke', () => {
  beforeEach(() => {
    authApiMocks.login.mockResolvedValue({
      accessToken: 'token_001',
      tokenType: 'Bearer',
      expiresIn: 7200,
    })
    authApiMocks.getCurrentUser.mockResolvedValue({
      userId: 'usr_001',
      username: 'zhangsan',
      displayName: '张三',
      mobile: '13800000000',
      email: 'zhangsan@example.com',
      avatar: '',
      companyId: 'cmp_001',
      activePostId: 'post_001',
      activeDepartmentId: 'dept_001',
      roles: ['OA_USER'],
      permissions: ['oa:leave:create'],
      dataScopes: [],
      partTimePosts: [],
      delegations: [],
      aiCapabilities: ['ai:copilot:open'],
      menus: [],
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('logs in, hydrates current-user, and redirects to the requested page', async () => {
    const { SignIn } = await import('./index')

    render(<SignIn />)

    fireEvent.change(screen.getByLabelText('用户名'), {
      target: { value: 'zhangsan' },
    })
    fireEvent.change(screen.getByLabelText('密码'), {
      target: { value: 'westflow123' },
    })
    fireEvent.click(screen.getByRole('button', { name: '登录' }))

    await waitFor(() =>
      expect(authApiMocks.login).toHaveBeenCalledWith({
        username: 'zhangsan',
        password: 'westflow123',
      })
    )
    await waitFor(() =>
      expect(authApiMocks.getCurrentUser).toHaveBeenCalledTimes(1)
    )
    expect(authStoreMocks.setAccessToken).toHaveBeenCalledWith('token_001')
    expect(authStoreMocks.setCurrentUser).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'usr_001',
        displayName: '张三',
      })
    )
    expect(toastSuccessMock).toHaveBeenCalledWith('欢迎回来，张三')
    expect(navigateMock).toHaveBeenCalledWith({
      to: '/workbench/todos/list',
      replace: true,
    })
  })
})
