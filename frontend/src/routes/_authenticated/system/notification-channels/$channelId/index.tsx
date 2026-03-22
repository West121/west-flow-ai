import { createFileRoute } from '@tanstack/react-router'
import { NotificationChannelDetailPage } from '@/features/system/notification-channel-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

export const Route = createFileRoute('/_authenticated/system/notification-channels/$channelId/')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: NotificationChannelDetailRoute,
})

function NotificationChannelDetailRoute() {
  const { channelId } = Route.useParams()

  return <NotificationChannelDetailPage channelId={channelId} />
}
