import { createFileRoute } from '@tanstack/react-router'
import { NotificationChannelCreatePage } from '@/features/system/notification-channel-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

export const Route = createFileRoute('/_authenticated/system/notification-channels/create')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: NotificationChannelCreatePage,
})
