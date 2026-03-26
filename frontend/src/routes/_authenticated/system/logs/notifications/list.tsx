import { createFileRoute } from '@tanstack/react-router'
import { SystemNotificationLogListPage } from '@/features/system/log-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/logs/notifications/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: SystemNotificationLogListPage,
})
