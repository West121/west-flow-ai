import { createFileRoute } from '@tanstack/react-router'
import { NotificationTemplateEditPage } from '@/features/system/notification-pages'

export const Route = createFileRoute('/_authenticated/system/notifications/templates/$templateId/edit')({
  component: NotificationTemplateEditPage,
})
