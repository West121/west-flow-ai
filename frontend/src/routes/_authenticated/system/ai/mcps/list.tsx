import { createFileRoute } from '@tanstack/react-router'
import { AiMcpListPage } from '@/features/ai-admin/registry-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// AI MCP 列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/mcps/list')({
  validateSearch: listQuerySearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <AiMcpListPage search={search} navigate={navigate} />
}
