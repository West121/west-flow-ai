import { Fragment, type ReactNode, useEffect, useMemo, useState } from 'react'
import {
  type ColumnDef,
  type ExpandedState,
  type OnChangeFn,
  type Row,
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
import { Checkbox } from '@/components/ui/checkbox'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'
import { type NavigateFn, useTableUrlState } from '@/hooks/use-table-url-state'
import { PageShell } from '@/features/shared/page-shell'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import {
  DataTableBulkActions,
  DataTableColumnHeader,
  DataTablePagination,
} from '@/components/data-table'
import { ProTableBoard, type ProTableBoardColumn } from './pro-table-board'
import { type ProTableDensityMode } from './pro-table-density'
import { resolveDensityClassName } from './pro-table-density-utils'
import { ProTableToolbar } from './pro-table-toolbar'
import {
  type ProTableExportHandler,
} from './pro-table-export'

type SummaryItem = {
  label: string
  value: string
  hint: string
}

export type ProTableGroupOption<TData> = {
  field: string
  label: string
  getValue: (row: TData) => string
}

export type ProTableBulkActionsRenderContext<TData> = {
  selectedRows: TData[]
  clearSelection: () => void
}

function resolveColumnLabel<TData>(columnDef: ColumnDef<TData, unknown>) {
  const meta = columnDef.meta as { label?: string } | undefined

  if (meta?.label) {
    return meta.label
  }
  if (typeof columnDef.header === 'string') {
    return columnDef.header
  }

  return null
}

function toSortingState(search: ListQuerySearch): SortingState {
  return (search.sorts ?? []).map((item) => ({
    id: item.field,
    desc: item.direction === 'desc',
  }))
}

function compareSortableValues(a: unknown, b: unknown) {
  if (a == null && b == null) {
    return 0
  }
  if (a == null) {
    return 1
  }
  if (b == null) {
    return -1
  }
  if (typeof a === 'number' && typeof b === 'number') {
    return a - b
  }

  return String(a).localeCompare(String(b), 'zh-CN', {
    numeric: true,
    sensitivity: 'base',
  })
}

function sortTreeRows<TData extends object>(
  rows: TData[],
  field: string,
  desc: boolean,
  getSubRows: (row: TData) => TData[] | undefined
): TData[] {
  return [...rows]
    .sort((leftRow, rightRow) => {
      const leftValue = (leftRow as Record<string, unknown>)[field]
      const rightValue = (rightRow as Record<string, unknown>)[field]
      const result = compareSortableValues(leftValue, rightValue)
      return desc ? -result : result
    })
    .map((row) => {
      const subRows = getSubRows(row)

      if (!subRows || subRows.length === 0) {
        return row
      }

      const nextSubRows = sortTreeRows(subRows, field, desc, getSubRows)
      const matchedEntry = Object.entries(
        row as Record<string, unknown>
      ).find(([, value]) => value === subRows)

      if (!matchedEntry) {
        return row
      }

      const [subRowsKey] = matchedEntry
      return {
        ...(row as Record<string, unknown>),
        [subRowsKey]: nextSubRows,
      } as TData
    })
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

export function ProTable<TData extends object>({
  title,
  description,
  searchPlaceholder,
  search,
  navigate,
  columns,
  data,
  total,
  summaries = [],
  topContent,
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
  enableRowSelection = false,
  getRowId,
  groupOptions = [],
  renderBulkActions,
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
  topContent?: ReactNode
  createActionNode?: ReactNode
  extraActions?: ReactNode
  onRefresh?: () => void
  isRefreshing?: boolean
  onExport?: ProTableExportHandler<TData>
  onImport?: (file: File) => void
  supportsBoard?: boolean
  defaultViewMode?: 'table' | 'board'
  renderBoardCard?: (item: TData) => ReactNode
  resolveBoardColumns?: (rows: TData[]) => ProTableBoardColumn<TData>[]
  getSubRows?: (row: TData) => TData[] | undefined
  enableRowSelection?: boolean
  getRowId?: (row: TData, index: number, parent?: Row<TData>) => string
  groupOptions?: ProTableGroupOption<TData>[]
  renderBulkActions?: (
    context: ProTableBulkActionsRenderContext<TData>
  ) => ReactNode
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
  const activeGroupField = groups[0]?.field
  const isTreeMode = typeof getSubRows === 'function'
  const activeGroupOption = groupOptions.find(
    (option) => option.field === activeGroupField
  )
  const tableColumns = useMemo<ColumnDef<TData, unknown>[]>(() => {
    const enhanceColumn = (
      columnDef: ColumnDef<TData, unknown>
    ): ColumnDef<TData, unknown> => {
      const label = resolveColumnLabel(columnDef)
      const hasAccessor =
        ('accessorFn' in columnDef &&
          typeof columnDef.accessorFn !== 'undefined') ||
        ('accessorKey' in columnDef &&
          typeof columnDef.accessorKey !== 'undefined')
      const canSortByDefault =
        typeof label === 'string' &&
        hasAccessor

      return {
        ...columnDef,
        meta: ({
          ...(columnDef.meta as Record<string, unknown> | undefined),
          ...(label ? { label } : {}),
        } as ColumnDef<TData, unknown>['meta']),
        enableSorting: columnDef.enableSorting ?? canSortByDefault,
        enableResizing:
          columnDef.enableResizing ?? (columnDef.id !== 'select' && label !== '操作'),
        header:
          typeof label === 'string' && canSortByDefault
            ? ({ column }) => <DataTableColumnHeader column={column} title={label} />
            : columnDef.header,
      } as ColumnDef<TData, unknown>
    }

    if (!enableRowSelection) {
      return columns.map(enhanceColumn)
    }

    const selectionColumn: ColumnDef<TData, unknown> = {
      id: 'select',
      header: ({ table }) => (
        <div className='flex items-center justify-center'>
          <Checkbox
            aria-label='选择当前页全部'
            checked={
              table.getIsAllPageRowsSelected() ||
              (table.getIsSomePageRowsSelected() ? 'indeterminate' : false)
            }
            onCheckedChange={(value) => table.toggleAllPageRowsSelected(Boolean(value))}
          />
        </div>
      ),
      cell: ({ row }) => (
        <div className='flex items-center justify-center'>
          <Checkbox
            aria-label={`选择${row.id}`}
            checked={row.getIsSelected()}
            onCheckedChange={(value) => row.toggleSelected(Boolean(value))}
          />
        </div>
      ),
      enableSorting: false,
      enableHiding: false,
      size: 40,
    }

    return [selectionColumn, ...columns.map(enhanceColumn)]
  }, [columns, enableRowSelection])
  const tableData = useMemo(() => {
    if (!isTreeMode || !getSubRows || sorting.length === 0) {
      return data
    }

    const [{ id, desc }] = sorting
    return sortTreeRows(data as TData[], id, desc, getSubRows)
  }, [data, getSubRows, isTreeMode, sorting])

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

    return collectExpandedRows(tableData, getSubRows)
  }, [expanded, getSubRows, globalFilter, isTreeMode, tableData])

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
    data: tableData,
    columns: tableColumns,
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
    columnResizeMode: 'onChange',
    enableColumnResizing: true,
    defaultColumn: {
      minSize: 96,
      size: 160,
      maxSize: 420,
    },
    getCoreRowModel: getCoreRowModel(),
    ...(getRowId ? { getRowId } : {}),
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
    return resolveBoardColumns(tableData)
  }, [resolveBoardColumns, tableData])
  const currentRows = useMemo(
    () => table.getRowModel().rows.map((row) => row.original as TData),
    [table]
  )
  const selectedRows = useMemo(
    () =>
      table.getFilteredSelectedRowModel().rows.map((row) => row.original as TData),
    [table]
  )
  const visibleColumnCount = table.getVisibleLeafColumns().length
  const groupedRows = useMemo(() => {
    if (!activeGroupOption || viewMode !== 'table') {
      return []
    }

    const grouped = new Map<string, Row<TData>[]>()
    for (const row of table.getRowModel().rows) {
      const rawValue = activeGroupOption.getValue(row.original as TData)
      const groupValue = rawValue.trim() === '' ? '未设置' : rawValue
      const rowsInGroup = grouped.get(groupValue)
      if (rowsInGroup) {
        rowsInGroup.push(row)
      } else {
        grouped.set(groupValue, [row])
      }
    }
    return Array.from(grouped.entries())
  }, [activeGroupOption, table, viewMode])

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

      {topContent ? <div className='space-y-4'>{topContent}</div> : null}

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
            onExport={
              onExport
                ? (scope) => {
                    onExport(
                      scope,
                      scope === 'selected-rows' ? selectedRows : currentRows
                    )
                  }
                : undefined
            }
            onImport={onImport}
            extraActions={extraActions}
            createAction={createActionNode}
            supportsBoard={supportsBoard}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
            groupOptions={groupOptions}
            activeGroupField={activeGroupField}
            onGroupFieldChange={(field) => {
              navigate({
                search: (prev) => ({
                  ...prev,
                  page: undefined,
                  groups: field ? [{ field }] : undefined,
                }),
              })
            }}
          />

          {enableRowSelection && renderBulkActions ? (
            <DataTableBulkActions table={table} entityName='项'>
              {renderBulkActions({
                selectedRows,
                clearSelection: () => table.resetRowSelection(),
              })}
            </DataTableBulkActions>
          ) : null}

          {viewMode === 'board' && supportsBoard && renderBoardCard && resolveBoardColumns ? (
            <ProTableBoard columns={boardColumns} renderCard={renderBoardCard} />
          ) : (
            <>
              <div className={cn('overflow-hidden rounded-lg border', resolveDensityClassName(density))}>
                <Table
                  className='min-w-full table-fixed'
                  style={{ width: table.getTotalSize() }}
                >
                  <TableHeader>
                    {table.getHeaderGroups().map((headerGroup) => (
                      <TableRow key={headerGroup.id}>
                        {headerGroup.headers.map((header) => (
                          <TableHead
                            key={header.id}
                            className='relative bg-muted/60 text-foreground'
                            style={{ width: header.getSize() }}
                          >
                            <div className='flex min-w-0 items-center justify-between gap-2 pe-2'>
                              {header.isPlaceholder
                                ? null
                                : flexRender(header.column.columnDef.header, header.getContext())}
                            </div>
                            {header.column.getCanResize() && !header.isPlaceholder ? (
                              <div
                                role='separator'
                                aria-orientation='vertical'
                                aria-label='调整列宽'
                                onDoubleClick={() => header.column.resetSize()}
                                onMouseDown={header.getResizeHandler()}
                                onTouchStart={header.getResizeHandler()}
                                className={cn(
                                  'absolute inset-y-0 right-0 w-1.5 cursor-col-resize touch-none select-none bg-transparent transition-colors hover:bg-primary/25',
                                  header.column.getIsResizing() && 'bg-primary/35'
                                )}
                              />
                            ) : null}
                          </TableHead>
                        ))}
                      </TableRow>
                    ))}
                  </TableHeader>
                  <TableBody>
                    {table.getRowModel().rows.length > 0 ? (
                      activeGroupOption ? (
                        groupedRows.map(([groupValue, rows]) => (
                          <Fragment key={groupValue}>
                            <TableRow className='bg-muted/40 hover:bg-muted/40'>
                              <TableCell colSpan={visibleColumnCount} className='font-medium'>
                                {activeGroupOption.label}：{groupValue}
                                <span className='ms-2 text-xs text-muted-foreground'>
                                  {rows.length} 项
                                </span>
                              </TableCell>
                            </TableRow>
                            {rows.map((row) => (
                              <TableRow key={row.id} data-state={row.getIsSelected() ? 'selected' : undefined}>
                                {row.getVisibleCells().map((cell) => (
                                  <TableCell
                                    key={cell.id}
                                    className='align-top'
                                    style={{ width: cell.column.getSize() }}
                                  >
                                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                  </TableCell>
                                ))}
                              </TableRow>
                            ))}
                          </Fragment>
                        ))
                      ) : (
                        table.getRowModel().rows.map((row) => (
                          <TableRow key={row.id} data-state={row.getIsSelected() ? 'selected' : undefined}>
                            {row.getVisibleCells().map((cell) => (
                              <TableCell
                                key={cell.id}
                                className='align-top'
                                style={{ width: cell.column.getSize() }}
                              >
                                {flexRender(cell.column.columnDef.cell, cell.getContext())}
                              </TableCell>
                            ))}
                          </TableRow>
                        ))
                      )
                    ) : (
                      <TableRow>
                        <TableCell colSpan={visibleColumnCount} className='h-24 text-center text-muted-foreground'>
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
