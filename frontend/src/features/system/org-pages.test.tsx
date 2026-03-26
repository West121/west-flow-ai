import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CompanyCreatePage,
  DepartmentCreatePage,
  DepartmentsListPage,
  PostCreatePage,
  PostsListPage,
} from './org-pages'

const { navigateMock, orgApiMocks } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  orgApiMocks: {
    createCompany: vi.fn(),
    createDepartment: vi.fn(),
    createPost: vi.fn(),
    deleteCompany: vi.fn(),
    deleteDepartment: vi.fn(),
    deletePost: vi.fn(),
    getCompanyFormOptions: vi.fn(),
    getDepartmentFormOptions: vi.fn(),
    getDepartmentUsers: vi.fn(),
    getPostFormOptions: vi.fn(),
    getPostUsers: vi.fn(),
    getDepartmentTree: vi.fn(),
    listDepartments: vi.fn(),
    listPosts: vi.fn(),
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
    useSearch: () => ({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    }),
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
    description: string
    actions?: React.ReactNode
    children: React.ReactNode
  }) => (
    <div>
      <h1>{title}</h1>
      <p>{description}</p>
      {actions}
      {children}
    </div>
  ),
}))

vi.mock('@/features/shared/pro-table', () => ({
  ProTable: ({
    data,
    columns,
    title,
    getSubRows,
  }: {
    data: Array<Record<string, unknown>>
    columns: Array<{
      id?: string
      accessorKey?: string
      cell?: (context: {
        row: {
          original: Record<string, unknown>
          depth: number
          getCanExpand: () => boolean
          getIsExpanded: () => boolean
          getToggleExpandedHandler: () => () => void
        }
      }) => React.ReactNode
    }>
    title: string
    getSubRows?: (row: Record<string, unknown>) => Array<Record<string, unknown>> | undefined
  }) => {
    const [expanded, setExpanded] = useState<Record<string, boolean>>({})
    const isTreeMode = typeof getSubRows === 'function'

    const rows: Array<{
      row: Record<string, unknown>
      rowId: string
      depth: number
      children: Array<Record<string, unknown>>
      expanded: boolean
    }> = []

    const walk = (
      items: Array<Record<string, unknown>>,
      depth = 0,
      parentId = ''
    ) => {
      items.forEach((item, index) => {
        const rowId = parentId ? `${parentId}.${index}` : `${index}`
        const children = isTreeMode ? getSubRows?.(item) ?? [] : []
        const rowExpanded = Boolean(expanded[rowId])
        rows.push({
          row: item,
          rowId,
          depth,
          children,
          expanded: rowExpanded,
        })
        if (!isTreeMode || rowExpanded) {
          walk(children, depth + 1, rowId)
        }
      })
    }

    walk(data)

    return (
      <section>
        <h2>{title}</h2>
        <table>
          <tbody>
            {rows.map(({ row, rowId, depth, children, expanded: rowExpanded }) => (
              <tr key={String(row.id ?? row.departmentId ?? row.postId ?? rowId)}>
                {columns.map((column, columnIndex) => (
                  <td key={`${column.id ?? column.accessorKey ?? columnIndex}`}>
                    {column.cell
                      ? column.cell({
                          row: {
                            original: row,
                            depth,
                            getCanExpand: () => children.length > 0,
                            getIsExpanded: () => rowExpanded,
                            getToggleExpandedHandler: () => () =>
                              setExpanded((current) => ({
                                ...current,
                                [rowId]: !current[rowId],
                              })),
                          },
                        })
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
    )
  },
}))

vi.mock('@/lib/api/system-org', () => orgApiMocks)

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

describe('org pages', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    Object.values(orgApiMocks).forEach((mock) => mock.mockReset())
    orgApiMocks.getCompanyFormOptions.mockResolvedValue({
      companies: [
        {
          id: 'company_001',
          name: '西部科技',
        },
      ],
    })
    orgApiMocks.getDepartmentFormOptions.mockResolvedValue({
      companies: [
        {
          id: 'company_001',
          name: '西部科技',
        },
      ],
      parentDepartments: [
        {
          id: 'dept_root',
          companyId: 'company_001',
          name: '总部',
        },
      ],
    })
    orgApiMocks.getDepartmentTree.mockResolvedValue([
      {
        departmentId: 'dept_001',
        companyId: 'company_001',
        companyName: '西部科技',
        parentDepartmentId: null,
        parentDepartmentName: null,
        departmentName: '财务部',
        status: 'ENABLED',
        createdAt: '2026-03-25T09:00:00+08:00',
        children: [],
      },
    ])
    orgApiMocks.getPostFormOptions.mockResolvedValue({
      departments: [
        {
          id: 'dept_root',
          companyId: 'company_001',
          name: '总部',
        },
      ],
    })
    orgApiMocks.listDepartments.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          departmentId: 'dept_001',
          companyName: '西部科技',
          parentDepartmentName: null,
          departmentName: '财务部',
          status: 'ENABLED',
          createdAt: '2026-03-25T09:00:00+08:00',
        },
      ],
    })
    orgApiMocks.listPosts.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          postId: 'post_001',
          companyName: '西部科技',
          departmentName: '财务部',
          postName: '报销审核岗',
          status: 'ENABLED',
          createdAt: '2026-03-25T09:00:00+08:00',
        },
      ],
    })
    orgApiMocks.getDepartmentUsers.mockResolvedValue([
      {
        userId: 'usr_001',
        displayName: '张三',
        username: 'zhangsan',
        departmentName: '财务部',
        postName: '报销审核岗',
        status: 'ENABLED',
      },
    ])
    orgApiMocks.getPostUsers.mockResolvedValue([
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

  it('renders company form with ProForm shell and actions', async () => {
    renderWithQuery(<CompanyCreatePage />)

    expect(await screen.findByText('公司信息')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存并返回列表' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存并继续编辑' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '返回列表' })).toHaveAttribute(
      'href',
      '/system/companies/list'
    )
  })

  it('renders department form with ProForm shell and actions', async () => {
    renderWithQuery(<DepartmentCreatePage />)

    expect(await screen.findByText('部门信息')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存并返回列表' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存并继续编辑' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '返回列表' })).toHaveAttribute(
      'href',
      '/system/departments/list'
    )
  })

  it('renders post form with ProForm shell and actions', async () => {
    renderWithQuery(<PostCreatePage />)

    expect(await screen.findByText('岗位信息')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存并返回列表' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存并继续编辑' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '返回列表' })).toHaveAttribute(
      'href',
      '/system/posts/list'
    )
  })

  it('loads department associated users from row action', async () => {
    renderWithQuery(<DepartmentsListPage />)

    fireEvent.click(await screen.findByRole('button', { name: '关联用户' }))

    await waitFor(() => {
      expect(orgApiMocks.getDepartmentUsers).toHaveBeenCalledWith('dept_001')
    })
    expect(await screen.findByText('部门关联用户')).toBeInTheDocument()
    expect(screen.getByText('张三')).toBeInTheDocument()
    expect(screen.getByText('@zhangsan')).toBeInTheDocument()
  })

  it('loads post associated users from row action', async () => {
    renderWithQuery(<PostsListPage />)

    fireEvent.click(await screen.findByRole('button', { name: '关联用户' }))

    await waitFor(() => {
      expect(orgApiMocks.getPostUsers).toHaveBeenCalledWith('post_001')
    })
    expect(await screen.findByText('岗位关联用户')).toBeInTheDocument()
    expect(screen.getByText('张三')).toBeInTheDocument()
    expect(screen.getByText('@zhangsan')).toBeInTheDocument()
  })
})
