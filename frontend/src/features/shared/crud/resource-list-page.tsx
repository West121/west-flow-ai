import { type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Link } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import { ProTable } from '@/features/shared/pro-table'

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
  createAction?: {
    label: string
    href: string
  }
  extraActions?: ReactNode
}

// 列表页通用骨架，负责摘要区、表格区和分页查询态。
export function ResourceListPage<TData>({
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
  createAction,
  extraActions,
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
      extraActions={extraActions}
      onExport={() => undefined}
      createActionNode={createActionNode}
    />
  )
}
