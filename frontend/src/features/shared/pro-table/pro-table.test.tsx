import { fireEvent, render, screen } from '@testing-library/react'
import { type ColumnDef } from '@tanstack/react-table'
import { describe, expect, it, vi } from 'vitest'
import { ProTable } from './pro-table'

vi.mock('@/hooks/use-table-url-state', () => ({
  useTableUrlState: () => ({
    globalFilter: '',
    onGlobalFilterChange: vi.fn(),
    pagination: {
      pageIndex: 0,
      pageSize: 20,
    },
    onPaginationChange: vi.fn(),
    ensurePageInRange: vi.fn(),
    columnFilters: [],
    onColumnFiltersChange: vi.fn(),
  }),
}))

vi.mock('@/features/shared/page-shell', () => ({
  PageShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('./pro-table-toolbar', () => ({
  ProTableToolbar: () => null,
}))

type TreeRow = {
  id: string
  name: string
  status: string
  createdAt: string
  children: TreeRow[]
}

const columns: ColumnDef<TreeRow>[] = [
  {
    accessorKey: 'name',
    header: '名称',
    cell: ({ row }) => (
      <div style={{ paddingLeft: `${row.depth * 20}px` }}>
        {row.getCanExpand() ? (
          <button
            type='button'
            aria-label={
              row.getIsExpanded() ? `收起 ${row.original.name}` : `展开 ${row.original.name}`
            }
            onClick={row.getToggleExpandedHandler()}
          >
            {row.getIsExpanded() ? '-' : '+'}
          </button>
        ) : null}
        <span>{row.original.name}</span>
      </div>
    ),
  },
  {
    accessorKey: 'status',
    header: '状态',
  },
  {
    accessorKey: 'createdAt',
    header: '创建时间',
  },
]

describe('ProTable tree mode', () => {
  it('supports expand and collapse with sub rows', () => {
    const data: TreeRow[] = [
      {
        id: 'dept_root',
        name: '总部',
        status: '启用',
        createdAt: '2026-03-25T09:00:00+08:00',
        children: [
          {
            id: 'dept_child',
            name: '财务部',
            status: '停用',
            createdAt: '2026-03-25T10:00:00+08:00',
            children: [],
          },
        ],
      },
    ]

    render(
      <ProTable<TreeRow>
        title='部门列表'
        description='部门树'
        searchPlaceholder='搜索部门'
        search={{
          page: 1,
          pageSize: 20,
          keyword: '',
          filters: [],
          sorts: [],
          groups: [],
        }}
        navigate={vi.fn()}
        columns={columns}
        data={data}
        getSubRows={(row) => row.children}
      />
    )

    expect(screen.getByText('总部')).toBeInTheDocument()
    expect(screen.queryByText('财务部')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '展开 总部' }))

    expect(screen.getByText('财务部')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '收起 总部' })).toBeInTheDocument()
  })
})
