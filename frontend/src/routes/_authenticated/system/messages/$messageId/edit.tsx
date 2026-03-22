import { createFileRoute } from '@tanstack/react-router'
import { MessageEditPage } from '@/features/system/message-pages'

// 消息编辑路由只负责参数透传。
export const Route = createFileRoute('/_authenticated/system/messages/$messageId/edit')({
  component: MessageEditRoute,
})

function MessageEditRoute() {
  const { messageId } = Route.useParams()

  return <MessageEditPage messageId={messageId} />
}
