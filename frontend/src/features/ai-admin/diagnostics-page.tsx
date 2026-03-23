import { type ColumnDef } from '@tanstack/react-table'
import { useQuery } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import { listAiMcpDiagnostics, type AiMcpDiagnosticRecord } from '@/lib/api/ai-admin'
import { formatDateTime, formatDurationMillis } from './shared-formatters'

type PageSearchProps = {
  search: ListQuerySearch
  navigate: NavigateFn
}

function connectionLabel(value: string) {
  switch (value) {
    case 'UP':
      return '连通'
    case 'DOWN':
      return '失败'
    case 'DISABLED':
      return '已停用'
    case 'INTERNAL':
      return '平台内置'
    default:
      return value
  }
}

function connectionVariant(value: string) {
  switch (value) {
    case 'UP':
    case 'INTERNAL':
      return 'secondary' as const
    case 'DOWN':
      return 'destructive' as const
    default:
      return 'outline' as const
  }
}

function emptyPage(search: ListQuerySearch) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

function listSummary(search: ListQuerySearch, total: number) {
  return [
    { label: '总条数', value: `${total}`, hint: `当前查询页：${search.page} / ${search.pageSize}` },
    { label: '筛选项', value: `${search.filters?.length ?? 0}`, hint: '支持关键词、状态、传输类型和连通状态筛选' },
    { label: '分组项', value: `${search.groups?.length ?? 0}`, hint: '协议已保留分组能力' },
  ]
}

const columns: ColumnDef<AiMcpDiagnosticRecord, unknown>[] = [
  { accessorKey: 'mcpCode', header: '编码' },
  { accessorKey: 'mcpName', header: '名称' },
  { accessorKey: 'transportType', header: '传输类型' },
  {
    accessorKey: 'connectionStatus',
    header: '连通状态',
    cell: ({ row }) => (
      <Badge variant={connectionVariant(row.original.connectionStatus)}>
        {connectionLabel(row.original.connectionStatus)}
      </Badge>
    ),
  },
  { accessorKey: 'toolCount', header: '工具数', cell: ({ row }) => row.original.toolCount ?? '-' },
  { accessorKey: 'runtimeToolCalls', header: '运行调用', cell: ({ row }) => row.original.observability?.totalToolCalls ?? '-' },
  { accessorKey: 'responseTimeMillis', header: '耗时', cell: ({ row }) => formatDurationMillis(row.original.responseTimeMillis) },
  { accessorKey: 'failureStage', header: '失败阶段', cell: ({ row }) => row.original.failureStage || '-' },
  { accessorKey: 'failureReason', header: '失败原因', cell: ({ row }) => row.original.failureReason || '-' },
  { accessorKey: 'latestFailureReason', header: '最近运行失败', cell: ({ row }) => row.original.observability?.latestFailureReason || '-' },
  { accessorKey: 'checkedAt', header: '检查时间', cell: ({ row }) => formatDateTime(row.original.checkedAt) },
]

/**
 * MCP 连通性诊断页。
 */
export function AiMcpDiagnosticsPage({ search, navigate }: PageSearchProps) {
  const query = useQuery({
    queryKey: ['ai-admin', 'mcps', 'diagnostics', search],
    queryFn: () => listAiMcpDiagnostics(search),
  })

  const data = query.data ?? emptyPage(search)

  return (
    <ResourceListPage
      title='MCP 连通性诊断'
      description='查看外部 MCP 的连通性、响应耗时、工具数量、失败阶段和失败原因。'
      endpoint='/system/ai/mcps/diagnostics/page'
      searchPlaceholder='搜索 MCP 编码、名称、地址或失败原因'
      search={search}
      navigate={navigate}
      columns={columns}
      data={data.records}
      total={data.total}
      summaries={listSummary(search, data.total)}
      createAction={undefined}
    />
  )
}
