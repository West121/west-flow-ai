import { createFileRoute } from '@tanstack/react-router'
import { NotificationTemplateDetailPage } from '@/features/system/notification-pages'

export const Route = createFileRoute('/_authenticated/system/notifications/templates/$templateId/')({
  component: NotificationTemplateDetailPage,
})
