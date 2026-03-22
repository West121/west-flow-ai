import { createFileRoute } from '@tanstack/react-router'
import { NotificationChannelsListPage } from '@/features/system/notification-channel-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/notification-channels/list')({
  beforeLoad: ensureProcessAdminRouteAccess,
  validateSearch: listQuerySearchSchema,
  component: NotificationChannelsListPage,
})
