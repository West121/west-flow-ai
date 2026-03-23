import { createFileRoute } from '@tanstack/react-router'
import { AiConfirmationListPage } from '@/features/ai-admin/record-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// AI 确认记录列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/confirmations/list')({
  validateSearch: listQuerySearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <AiConfirmationListPage search={search} navigate={navigate} />
}
