import { useEffect, useMemo, useState } from 'react'
import { Link } from '@tanstack/react-router'
import {
  type ColumnDef,
  type OnChangeFn,
  type SortingState,
  type VisibilityState,
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { FileDown, Funnel, Plus, Rows3, ShieldCheck } from 'lucide-react'
import { DataTablePagination, DataTableToolbar } from '@/components/data-table'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { type NavigateFn, useTableUrlState } from '@/hooks/use-table-url-state'
import { cn } from '@/lib/utils'
import { PageShell } from '@/features/shared/page-shell'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'

type SummaryItem = {
  label: string
  value: string
  hint: string
}

type ResourceListPageProps<TData> = {
  title: string
  description: string
  endpoint: string
  searchPlaceholder: string
  search: ListQuerySearch
  navigate: NavigateFn
  columns: ColumnDef<TData, unknown>[]
  data: TData[]
  summaries: SummaryItem[]
  createAction?: {
    label: string
    href: string
  }
}

function toSortingState(search: ListQuerySearch): SortingState {
  return (search.sorts ?? []).map((item) => ({
    id: item.field,
    desc: item.direction === 'desc',
  }))
}

export function ResourceListPage<TData>({
  title,
  description,
  endpoint,
  searchPlaceholder,
  search,
  navigate,
  columns,
  data,
  summaries,
  createAction,
}: ResourceListPageProps<TData>) {
  const [rowSelection, setRowSelection] = useState({})
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({})
  const [sorting, setSorting] = useState<SortingState>(() =>
    toSortingState(search)
  )
  const filters = search.filters ?? []
  const sorts = search.sorts ?? []
  const groups = search.groups ?? []

  const {
    globalFilter,
    onGlobalFilterChange,
    pagination,
    onPaginationChange,
    ensurePageInRange,
  } = useTableUrlState({
    search,
    navigate,
    pagination: { defaultPage: 1, defaultPageSize: 20 },
    globalFilter: { enabled: true, key: 'keyword' },
  })

  useEffect(() => {
    setSorting(toSortingState(search))
  }, [search])

  const onSortingChange: OnChangeFn<SortingState> = (updater) => {
    const next = typeof updater === 'function' ? updater(sorting) : updater
    setSorting(next)
    navigate({
      search: (prev) => ({
        ...prev,
        page: undefined,
        sorts:
          next.length > 0
            ? next.map((item) => ({
                field: item.id,
                direction: item.desc ? 'desc' : 'asc',
              }))
            : undefined,
      }),
    })
  }

  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data,
    columns,
    state: {
      globalFilter,
      pagination,
      rowSelection,
      sorting,
      columnVisibility,
    },
    onGlobalFilterChange,
    onPaginationChange,
    onRowSelectionChange: setRowSelection,
    onSortingChange,
    onColumnVisibilityChange: setColumnVisibility,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
  })

  useEffect(() => {
    ensurePageInRange(table.getPageCount())
  }, [ensurePageInRange, table])

  const querySummary = useMemo(
    () => [
      `分页 ${search.page} / 每页 ${search.pageSize}`,
      `筛选 ${filters.length} 项`,
      `排序 ${sorts.length} 项`,
      `分组 ${groups.length} 项`,
    ],
    [filters.length, groups.length, search.page, search.pageSize, sorts.length]
  )

  const actions = (
    <>
      <Button variant='outline' disabled>
        <FileDown data-icon='inline-start' />
        导出预留
      </Button>
      {createAction ? (
        <Button asChild>
          <Link to={createAction.href}>
            <Plus data-icon='inline-start' />
            {createAction.label}
          </Link>
        </Button>
      ) : null}
    </>
  )

  return (
    <PageShell title={title} description={description} actions={actions}>
      <div className='grid gap-4 lg:grid-cols-3'>
        {summaries.map((item) => (
          <Card key={item.label}>
            <CardHeader className='pb-3'>
              <CardDescription>{item.label}</CardDescription>
              <CardTitle className='text-3xl'>{item.value}</CardTitle>
            </CardHeader>
            <CardContent className='text-sm text-muted-foreground'>
              {item.hint}
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader className='gap-4 lg:flex-row lg:items-start lg:justify-between'>
          <div className='space-y-2'>
            <CardTitle>列表能力基线</CardTitle>
            <CardDescription>
              当前骨架已按 M0 协议保留独立列表页、分页查询态和表格列显隐。
              后续接入接口时统一对接 <code>{endpoint}</code>。
            </CardDescription>
          </div>
          <div className='flex flex-wrap gap-2'>
            <Badge variant='secondary'>
              <ShieldCheck />
              权限态控制
            </Badge>
            <Badge variant='secondary'>
              <Funnel />
              精确筛选
            </Badge>
            <Badge variant='secondary'>
              <Rows3 />
              分组预留
            </Badge>
          </div>
        </CardHeader>
        <CardContent className='flex flex-col gap-4'>
          <div className='flex flex-wrap gap-2 text-sm text-muted-foreground'>
            {querySummary.map((item) => (
              <span
                key={item}
                className='rounded-full border bg-muted/40 px-3 py-1'
              >
                {item}
              </span>
            ))}
          </div>

          <DataTableToolbar table={table} searchPlaceholder={searchPlaceholder} />

          <div className='overflow-hidden rounded-lg border'>
            <Table>
              <TableHeader>
                {table.getHeaderGroups().map((headerGroup) => (
                  <TableRow key={headerGroup.id}>
                    {headerGroup.headers.map((header) => (
                      <TableHead key={header.id}>
                        {header.isPlaceholder
                          ? null
                          : flexRender(
                              header.column.columnDef.header,
                              header.getContext()
                            )}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows.length > 0 ? (
                  table.getRowModel().rows.map((row) => (
                    <TableRow
                      key={row.id}
                      data-state={row.getIsSelected() ? 'selected' : undefined}
                    >
                      {row.getVisibleCells().map((cell) => (
                        <TableCell key={cell.id} className={cn('align-top')}>
                          {flexRender(
                            cell.column.columnDef.cell,
                            cell.getContext()
                          )}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell
                      colSpan={columns.length}
                      className='h-24 text-center text-muted-foreground'
                    >
                      暂无数据，请调整查询条件或稍后接入真实接口。
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>

          <DataTablePagination table={table} />
        </CardContent>
      </Card>
    </PageShell>
  )
}
