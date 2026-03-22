import { createFileRoute } from '@tanstack/react-router'
import { SystemNotificationLogListPage } from '@/features/system/log-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/logs/notifications/list')({
  validateSearch: listQuerySearchSchema,
  component: SystemNotificationLogListPage,
})
