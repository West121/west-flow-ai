import { createFileRoute } from '@tanstack/react-router'
import { SettingsNotifications } from '@/features/settings/notifications'

// 通知设置页路由。
export const Route = createFileRoute('/_authenticated/settings/notifications')({
  component: SettingsNotifications,
})
