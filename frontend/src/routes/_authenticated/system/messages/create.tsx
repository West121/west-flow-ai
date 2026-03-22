import { createFileRoute } from '@tanstack/react-router'
import { MessageCreatePage } from '@/features/system/message-pages'

// 消息新建路由只负责挂载页面。
export const Route = createFileRoute('/_authenticated/system/messages/create')({
  component: MessageCreatePage,
})
