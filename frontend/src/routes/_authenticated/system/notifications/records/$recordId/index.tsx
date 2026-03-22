import { createFileRoute } from '@tanstack/react-router'
import { NotificationRecordDetailPage } from '@/features/system/notification-pages'

export const Route = createFileRoute('/_authenticated/system/notifications/records/$recordId/')({
  component: NotificationRecordDetailPage,
})
