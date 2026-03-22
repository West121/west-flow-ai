import { createFileRoute } from '@tanstack/react-router'
import { NotificationTemplateCreatePage } from '@/features/system/notification-pages'

export const Route = createFileRoute('/_authenticated/system/notifications/templates/create')({
  component: NotificationTemplateCreatePage,
})
