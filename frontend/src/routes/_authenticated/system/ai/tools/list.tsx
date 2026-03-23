import { createFileRoute } from '@tanstack/react-router'
import { AiToolListPage } from '@/features/ai-admin/registry-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// AI 工具列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/tools/list')({
  validateSearch: listQuerySearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <AiToolListPage search={search} navigate={navigate} />
}
