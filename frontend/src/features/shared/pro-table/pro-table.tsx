import { type ReactNode, useEffect, useMemo, useState } from 'react'
import {
  type ColumnDef,
  type ExpandedState,
  type OnChangeFn,
  type SortingState,
  type VisibilityState,
  flexRender,
  getCoreRowModel,
  getExpandedRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'
import { type NavigateFn, useTableUrlState } from '@/hooks/use-table-url-state'
import { PageShell } from '@/features/shared/page-shell'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import { DataTablePagination } from '@/components/data-table'
import { ProTableBoard, type ProTableBoardColumn } from './pro-table-board'
import { type ProTableDensityMode } from './pro-table-density'
import { resolveDensityClassName } from './pro-table-density-utils'
import { ProTableToolbar } from './pro-table-toolbar'
import { type ProTableExportScope } from './pro-table-export'

type SummaryItem = {
  label: string
  value: string
  hint: string
}

function toSortingState(search: ListQuerySearch): SortingState {
  return (search.sorts ?? []).map((item) => ({
    id: item.field,
    desc: item.direction === 'desc',
  }))
}

function collectExpandedRows<TData>(
  rows: TData[],
  getSubRows: (row: TData) => TData[] | undefined,
  parentId = ''
): Record<string, boolean> {
  return rows.reduce<Record<string, boolean>>((expanded, row, index) => {
    const rowId = parentId ? `${parentId}.${index}` : `${index}`
    const subRows = getSubRows(row) ?? []

    if (subRows.length > 0) {
      expanded[rowId] = true
      Object.assign(expanded, collectExpandedRows(subRows, getSubRows, rowId))
    }

    return expanded
  }, {})
}

export function ProTable<TData>({
  title,
  description,
  searchPlaceholder,
  search,
  navigate,
  columns,
  data,
  total,
  summaries = [],
  createActionNode,
  extraActions,
  onRefresh,
  isRefreshing = false,
  onExport,
  onImport,
  supportsBoard = false,
  defaultViewMode = 'table',
  renderBoardCard,
  resolveBoardColumns,
  getSubRows,
}: {
  title: string
  description: string
  searchPlaceholder: string
  search: ListQuerySearch
  navigate: NavigateFn
  columns: ColumnDef<TData, unknown>[]
  data: TData[]
  total?: number
  summaries?: SummaryItem[]
  createActionNode?: ReactNode
  extraActions?: ReactNode
  onRefresh?: () => void
  isRefreshing?: boolean
  onExport?: (scope: ProTableExportScope) => void
  onImport?: () => void
  supportsBoard?: boolean
  defaultViewMode?: 'table' | 'board'
  renderBoardCard?: (item: TData) => ReactNode
  resolveBoardColumns?: (rows: TData[]) => ProTableBoardColumn<TData>[]
  getSubRows?: (row: TData) => TData[] | undefined
}) {
  'use no memo'

  const [rowSelection, setRowSelection] = useState({})
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({})
  const [sorting, setSorting] = useState<SortingState>(() => toSortingState(search))
  const [density, setDensity] = useState<ProTableDensityMode>('default')
  const [viewMode, setViewMode] = useState<'table' | 'board'>(defaultViewMode)
  const [expanded, setExpanded] = useState<ExpandedState>({})
  const filters = search.filters ?? []
  const sorts = search.sorts ?? []
  const groups = search.groups ?? []
  const isTreeMode = typeof getSubRows === 'function'

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
  const treeExpandedState = useMemo(() => {
    if (!isTreeMode) {
      return expanded
    }

    const normalizedGlobalFilter = (globalFilter ?? '').trim()
    if (normalizedGlobalFilter === '' || !getSubRows) {
      return expanded
    }

    return collectExpandedRows(data, getSubRows)
  }, [data, expanded, getSubRows, globalFilter, isTreeMode])

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

  // TanStack Table 当前会触发 React Compiler 的兼容性告警，这里显式保留非 memo 边界。
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
      ...(isTreeMode ? { expanded: treeExpandedState } : {}),
    },
    onGlobalFilterChange,
    onPaginationChange,
    onRowSelectionChange: setRowSelection,
    onSortingChange,
    onColumnVisibilityChange: setColumnVisibility,
    onExpandedChange: setExpanded,
    getCoreRowModel: getCoreRowModel(),
    ...(isTreeMode
      ? {
          enableExpanding: true as const,
          filterFromLeafRows: true as const,
          maxLeafRowFilterDepth: 1000,
          getSubRows,
          getExpandedRowModel: getExpandedRowModel(),
        }
      : total !== undefined
        ? {
            manualPagination: true as const,
            pageCount: Math.max(1, Math.ceil(total / search.pageSize)),
          }
        : {
            getFilteredRowModel: getFilteredRowModel(),
            getSortedRowModel: getSortedRowModel(),
            getPaginationRowModel: getPaginationRowModel(),
          }),
  })

  useEffect(() => {
    ensurePageInRange(table.getPageCount())
  }, [ensurePageInRange, table])

  useEffect(() => {
    if (typeof window === 'undefined') {
      return
    }

    const nextSearchParams = new URLSearchParams(window.location.search)
    let changed = false

    const removeIfDefault = (key: string, shouldRemove: boolean) => {
      if (shouldRemove && nextSearchParams.has(key)) {
        nextSearchParams.delete(key)
        changed = true
      }
    }

    removeIfDefault('page', search.page <= 1)
    removeIfDefault('pageSize', search.pageSize === 20)
    removeIfDefault('keyword', search.keyword.trim() === '')
    removeIfDefault('filters', search.filters.length === 0)
    removeIfDefault('sorts', search.sorts.length === 0)
    removeIfDefault('groups', search.groups.length === 0)

    if (!changed) {
      return
    }

    const nextHref = `${window.location.pathname}${
      nextSearchParams.toString() ? `?${nextSearchParams}` : ''
    }${window.location.hash}`
    window.history.replaceState(window.history.state, '', nextHref)
  }, [search])

  const querySummary = useMemo(
    () => [
      `分页 ${search.page} / 每页 ${search.pageSize}`,
      `筛选 ${filters.length} 项`,
      `排序 ${sorts.length} 项`,
      `分组 ${groups.length} 项`,
    ],
    [filters.length, groups.length, search.page, search.pageSize, sorts.length]
  )

  const boardColumns = useMemo(() => {
    if (!resolveBoardColumns) {
      return []
    }
    return resolveBoardColumns(data)
  }, [data, resolveBoardColumns])

  return (
    <PageShell
      title={title}
      description={description}
      contentClassName='gap-4 sm:gap-6'
    >
      {summaries.length > 0 ? (
        <div className='grid gap-4 lg:grid-cols-3'>
          {summaries.map((item) => (
            <Card key={item.label}>
              <CardHeader className='pb-2'>
                <CardDescription>{item.label}</CardDescription>
                <CardTitle className='text-3xl'>{item.value}</CardTitle>
              </CardHeader>
              <CardContent className='pt-0 text-sm text-muted-foreground'>
                {item.hint}
              </CardContent>
            </Card>
          ))}
        </div>
      ) : null}

      <Card>
        <CardContent className='flex flex-col gap-4'>
          <ProTableToolbar
            table={table}
            total={total ?? data.length}
            searchPlaceholder={searchPlaceholder}
            searchValue={globalFilter ?? ''}
            onSearchChange={(value) => onGlobalFilterChange?.(value)}
            queryBadges={
              filters.length > 0 || sorts.length > 0 || groups.length > 0
                ? querySummary
                : []
            }
            density={density}
            onDensityChange={setDensity}
            onRefresh={onRefresh}
            isRefreshing={isRefreshing}
            onExport={onExport}
            onImport={onImport}
            extraActions={extraActions}
            createAction={createActionNode}
            supportsBoard={supportsBoard}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
          />

          {viewMode === 'board' && supportsBoard && renderBoardCard && resolveBoardColumns ? (
            <ProTableBoard columns={boardColumns} renderCard={renderBoardCard} />
          ) : (
            <>
              <div className={cn('overflow-hidden rounded-lg border', resolveDensityClassName(density))}>
                <Table>
                  <TableHeader>
                    {table.getHeaderGroups().map((headerGroup) => (
                      <TableRow key={headerGroup.id}>
                        {headerGroup.headers.map((header) => (
                          <TableHead key={header.id}>
                            {header.isPlaceholder
                              ? null
                              : flexRender(header.column.columnDef.header, header.getContext())}
                          </TableHead>
                        ))}
                      </TableRow>
                    ))}
                  </TableHeader>
                  <TableBody>
                    {table.getRowModel().rows.length > 0 ? (
                      table.getRowModel().rows.map((row) => (
                        <TableRow key={row.id} data-state={row.getIsSelected() ? 'selected' : undefined}>
                          {row.getVisibleCells().map((cell) => (
                            <TableCell key={cell.id} className='align-top'>
                              {flexRender(cell.column.columnDef.cell, cell.getContext())}
                            </TableCell>
                          ))}
                        </TableRow>
                      ))
                    ) : (
                      <TableRow>
                        <TableCell colSpan={columns.length} className='h-24 text-center text-muted-foreground'>
                          暂无数据，请调整查询条件或稍后再试。
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </div>
              {!isTreeMode ? <DataTablePagination table={table} /> : null}
            </>
          )}
        </CardContent>
      </Card>
    </PageShell>
  )
}
