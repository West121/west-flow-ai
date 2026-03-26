import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RolesListPage } from './role-pages'

const {
  navigateMock,
  routeNavigateMock,
  useSearchMock,
  systemRoleApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  routeNavigateMock: vi.fn(),
  useSearchMock: vi.fn(),
  systemRoleApiMocks: {
    deleteRole: vi.fn(),
    getRoleUsers: vi.fn(),
    listRoles: vi.fn(),
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
    useNavigate: () => routeNavigateMock,
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

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: ({
    title,
    data,
    columns,
  }: {
    title: string
    data: Array<Record<string, unknown>>
    columns: Array<{
      id?: string
      accessorKey?: string
      cell?: (context: { row: { original: Record<string, unknown> } }) => React.ReactNode
    }>
  }) => (
    <section>
      <h2>{title}</h2>
      <table>
        <tbody>
          {data.map((row, rowIndex) => (
            <tr key={String(row.roleId ?? rowIndex)}>
              {columns.map((column, columnIndex) => (
                <td key={`${column.id ?? column.accessorKey ?? columnIndex}`}>
                  {column.cell
                    ? column.cell({ row: { original: row } })
                    : column.accessorKey
                      ? String(row[column.accessorKey] ?? '')
                      : null}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  ),
}))

vi.mock('@/lib/api/system-roles', () => systemRoleApiMocks)

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

describe('role pages', () => {
  beforeEach(() => {
    vi.resetModules()
    navigateMock.mockReset()
    routeNavigateMock.mockReset()
    useSearchMock.mockReset()
    Object.values(systemRoleApiMocks).forEach((mock) => mock.mockReset())
    useSearchMock.mockReturnValue({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })
    systemRoleApiMocks.listRoles.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          roleId: 'role_dept_manager',
          roleCode: 'DEPT_MANAGER',
          roleName: '部门经理',
          roleCategory: 'SYSTEM',
          dataScopeSummary: '部门及子部门',
          menuCount: 3,
          status: 'ENABLED',
          createdAt: '2026-03-25T09:00:00+08:00',
        },
      ],
    })
    systemRoleApiMocks.getRoleUsers.mockResolvedValue([
      {
        userId: 'usr_001',
        displayName: '张三',
        username: 'zhangsan',
        departmentName: '财务部',
        postName: '报销审核岗',
        status: 'ENABLED',
      },
    ])
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('loads role associated users from row action', async () => {
    renderWithQuery(<RolesListPage />)

    fireEvent.click(await screen.findByRole('button', { name: '关联用户' }))

    await waitFor(() => {
      expect(systemRoleApiMocks.getRoleUsers).toHaveBeenCalledWith(
        'role_dept_manager'
      )
    })
    expect(await screen.findByText('角色关联用户')).toBeInTheDocument()
    expect(screen.getByText('张三')).toBeInTheDocument()
    expect(screen.getByText('@zhangsan')).toBeInTheDocument()
  })
})
