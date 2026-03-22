import { createFileRoute } from '@tanstack/react-router'
import { SystemNotificationLogDetailPage } from '@/features/system/log-pages'

export const Route = createFileRoute('/_authenticated/system/logs/notifications/$recordId/')({
  component: SystemNotificationLogDetailPage,
})
