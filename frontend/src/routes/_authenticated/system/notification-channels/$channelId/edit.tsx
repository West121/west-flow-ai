import { createFileRoute } from '@tanstack/react-router'
import { NotificationChannelEditPage } from '@/features/system/notification-channel-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

// 通知渠道编辑路由只负责权限校验和参数传递。
export const Route = createFileRoute('/_authenticated/system/notification-channels/$channelId/edit')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: NotificationChannelEditRoute,
})

function NotificationChannelEditRoute() {
  const { channelId } = Route.useParams()

  return <NotificationChannelEditPage channelId={channelId} />
}
