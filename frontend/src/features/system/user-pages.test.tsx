import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { toFormValues, UserEditPage } from './user-pages'

const {
  navigateMock,
  useSearchMock,
  systemUserApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  useSearchMock: vi.fn(),
  systemUserApiMocks: {
    createSystemUser: vi.fn(),
    getSystemUserDetail: vi.fn(),
    getSystemUserFormOptions: vi.fn(),
    listSystemUsers: vi.fn(),
    updateSystemUser: vi.fn(),
  },
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    to,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to?: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => navigateMock,
  getRouteApi: () => ({
    useSearch: useSearchMock,
    useNavigate: () => navigateMock,
  }),
}))

vi.mock('@/features/shared/page-shell', () => ({
  PageShell: ({
    title,
    description,
    actions,
    children,
  }: {
    title: string
    description?: string
    actions?: React.ReactNode
    children: React.ReactNode
  }) => (
    <section>
      <h1>{title}</h1>
      {description ? <p>{description}</p> : null}
      {actions}
      {children}
    </section>
  ),
}))

vi.mock('@/lib/api/system-users', () => systemUserApiMocks)

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  )
}

describe('system user pages', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    useSearchMock.mockReset()
    Object.values(systemUserApiMocks).forEach((mock) => mock.mockReset())
    useSearchMock.mockReturnValue({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })

    systemUserApiMocks.getSystemUserFormOptions.mockResolvedValue({
      companies: [
        {
          id: 'cmp_001',
          name: '西流科技',
        },
      ],
      posts: [
        {
          id: 'post_001',
          name: '报销审核岗',
          departmentId: 'dept_001',
          departmentName: '财务部',
        },
      ],
      roles: [
        {
          id: 'role_oa_user',
          name: 'OA 普通用户',
          roleCode: 'OA_USER',
          roleCategory: 'BUSINESS',
        },
        {
          id: 'role_dept_manager',
          name: '部门经理',
          roleCode: 'DEPT_MANAGER',
          roleCategory: 'SYSTEM',
        },
      ],
    })

    systemUserApiMocks.getSystemUserDetail.mockResolvedValue({
      userId: 'usr_001',
      displayName: '张三',
      username: 'zhangsan',
      mobile: '13800000000',
      email: 'zhangsan@example.com',
      companyId: '',
      companyName: '',
      departmentId: '',
      departmentName: '',
      postId: '',
      postName: '',
      roleIds: [],
      enabled: true,
      primaryAssignment: {
        userPostId: 'up_001',
        companyId: 'cmp_001',
        companyName: '西流科技',
        departmentId: 'dept_001',
        departmentName: '财务部',
        postId: 'post_001',
        postName: '报销审核岗',
        roleIds: ['role_oa_user', 'role_dept_manager'],
        roleNames: ['OA 普通用户', '部门经理'],
        primary: true,
        enabled: true,
      },
      partTimeAssignments: [],
    })
  })

  it('prefills company and post from the primary assignment when top-level fields are empty', () => {
    const values = toFormValues({
      userId: 'usr_001',
      displayName: '张三',
      username: 'zhangsan',
      mobile: '13800000000',
      email: 'zhangsan@example.com',
      companyId: '',
      companyName: '',
      departmentId: '',
      departmentName: '',
      postId: '',
      postName: '',
      roleIds: [],
      enabled: true,
      primaryAssignment: {
        userPostId: 'up_001',
        companyId: 'cmp_001',
        companyName: '西流科技',
        departmentId: 'dept_001',
        departmentName: '财务部',
        postId: 'post_001',
        postName: '报销审核岗',
        roleIds: ['role_oa_user', 'role_dept_manager'],
        roleNames: ['OA 普通用户', '部门经理'],
        primary: true,
        enabled: true,
      },
      partTimeAssignments: [],
    })

    expect(values.companyId).toBe('cmp_001')
    expect(values.primaryPostId).toBe('post_001')
    expect(values.roleIds).toEqual(['role_oa_user', 'role_dept_manager'])
  })

  it('shows role selection as a multi-select dropdown', async () => {
    renderWithQuery(<UserEditPage userId='usr_001' />)

    const roleTrigger = await screen.findByRole('button', { name: '角色分配' })
    expect(roleTrigger).toHaveTextContent('已选 2 项')
  })
})
