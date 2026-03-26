import { createFileRoute } from '@tanstack/react-router'
import { NotificationRecordsListPage } from '@/features/system/notification-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/notifications/records/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: NotificationRecordsListPage,
})
