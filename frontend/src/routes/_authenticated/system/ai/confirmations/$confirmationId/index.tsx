import { createFileRoute } from '@tanstack/react-router'
import { AiConfirmationDetailPage } from '@/features/ai-admin/record-pages'

// AI 确认记录详情路由。
export const Route = createFileRoute('/_authenticated/system/ai/confirmations/$confirmationId/')({
  component: RouteComponent,
})

function RouteComponent() {
  const { confirmationId } = Route.useParams()

  return <AiConfirmationDetailPage confirmationId={confirmationId} />
}
