import { createFileRoute } from '@tanstack/react-router'
import { AiConfirmationListPage } from '@/features/ai-admin/record-pages'
import { listQueryRouteSearchSchema, normalizeListQuerySearch } from '@/features/shared/table/query-contract'

// AI 确认记录列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/confirmations/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()

  return <AiConfirmationListPage search={search} navigate={navigate} />
}
