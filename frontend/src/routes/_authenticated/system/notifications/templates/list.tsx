import { createFileRoute } from '@tanstack/react-router'
import { NotificationTemplatesListPage } from '@/features/system/notification-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/notifications/templates/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: NotificationTemplatesListPage,
})
