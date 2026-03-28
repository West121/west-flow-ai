import { type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Link } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import {
  ProTable,
  type ProTableBulkActionsRenderContext,
  type ProTableGroupOption,
} from '@/features/shared/pro-table'

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
  total?: number
  summaries: SummaryItem[]
  topContent?: ReactNode
  createAction?: {
    label: string
    href: string
  }
  extraActions?: ReactNode
  onRefresh?: () => void
  isRefreshing?: boolean
  onImport?: (file: File) => void
  enableRowSelection?: boolean
  getRowId?: (row: TData, index: number) => string
  groupOptions?: ProTableGroupOption<TData>[]
  renderBulkActions?: (
    context: ProTableBulkActionsRenderContext<TData>
  ) => ReactNode
}

// 列表页通用骨架，负责摘要区、表格区和分页查询态。
export function ResourceListPage<TData extends object>({
  title,
  description,
  endpoint: _endpoint,
  searchPlaceholder,
  search,
  navigate,
  columns,
  data,
  total,
  summaries,
  topContent,
  createAction,
  extraActions,
  onRefresh,
  isRefreshing,
  onImport,
  enableRowSelection,
  getRowId,
  groupOptions,
  renderBulkActions,
}: ResourceListPageProps<TData>) {
  const createActionNode = createAction ? (
    <Button asChild>
      <Link to={createAction.href} search={{}}>
        {createAction.label}
      </Link>
    </Button>
  ) : undefined

  return (
    <ProTable
      title={title}
      description={description}
      searchPlaceholder={searchPlaceholder}
      search={search}
      navigate={navigate}
      columns={columns}
      data={data}
      total={total}
      summaries={summaries}
      topContent={topContent}
      extraActions={extraActions}
      onRefresh={onRefresh}
      isRefreshing={isRefreshing}
      onExport={() => undefined}
      onImport={onImport}
      createActionNode={createActionNode}
      enableRowSelection={enableRowSelection}
      getRowId={getRowId}
      groupOptions={groupOptions}
      renderBulkActions={renderBulkActions}
    />
  )
}
