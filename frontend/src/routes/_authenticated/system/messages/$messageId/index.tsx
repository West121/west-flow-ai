import { createFileRoute } from '@tanstack/react-router'
import { MessageDetailPage } from '@/features/system/message-pages'

// 消息详情路由只负责参数注入。
export const Route = createFileRoute('/_authenticated/system/messages/$messageId/')({
  component: MessageDetailRoute,
})

function MessageDetailRoute() {
  const { messageId } = Route.useParams()

  return <MessageDetailPage messageId={messageId} />
}
