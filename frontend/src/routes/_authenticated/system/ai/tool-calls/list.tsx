import { createFileRoute } from '@tanstack/react-router'
import { AiToolCallListPage } from '@/features/ai-admin/record-pages'
import { listQueryRouteSearchSchema, normalizeListQuerySearch } from '@/features/shared/table/query-contract'

// AI 工具调用记录列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/tool-calls/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()

  return <AiToolCallListPage search={search} navigate={navigate} />
}
