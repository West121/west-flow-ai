import { createFileRoute } from '@tanstack/react-router'
import { AiAgentListPage } from '@/features/ai-admin/registry-pages'
import { listQueryRouteSearchSchema, normalizeListQuerySearch } from '@/features/shared/table/query-contract'

// AI 智能体列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/agents/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()

  return <AiAgentListPage search={search} navigate={navigate} />
}
