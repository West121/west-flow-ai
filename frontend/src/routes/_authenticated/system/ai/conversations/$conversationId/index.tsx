import { createFileRoute } from '@tanstack/react-router'
import { AiConversationDetailPage } from '@/features/ai-admin/record-pages'

// AI 会话详情路由。
export const Route = createFileRoute('/_authenticated/system/ai/conversations/$conversationId/')({
  component: RouteComponent,
})

function RouteComponent() {
  const { conversationId } = Route.useParams()

  return <AiConversationDetailPage conversationId={conversationId} />
}
