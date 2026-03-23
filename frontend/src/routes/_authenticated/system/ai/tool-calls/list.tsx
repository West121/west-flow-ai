import { createFileRoute } from '@tanstack/react-router'
import { AiToolCallListPage } from '@/features/ai-admin/record-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// AI 工具调用记录列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/tool-calls/list')({
  validateSearch: listQuerySearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <AiToolCallListPage search={search} navigate={navigate} />
}
