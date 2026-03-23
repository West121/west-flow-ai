import { createFileRoute } from '@tanstack/react-router'
import { AiConversationListPage } from '@/features/ai-admin/record-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// AI 会话审计列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/conversations/list')({
  validateSearch: listQuerySearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <AiConversationListPage search={search} navigate={navigate} />
}
