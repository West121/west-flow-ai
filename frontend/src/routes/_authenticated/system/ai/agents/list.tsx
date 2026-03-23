import { createFileRoute } from '@tanstack/react-router'
import { AiAgentListPage } from '@/features/ai-admin/registry-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// AI 智能体列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/agents/list')({
  validateSearch: listQuerySearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <AiAgentListPage search={search} navigate={navigate} />
}
