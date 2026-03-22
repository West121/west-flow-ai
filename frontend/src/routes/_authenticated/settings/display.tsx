import { createFileRoute } from '@tanstack/react-router'
import { SettingsDisplay } from '@/features/settings/display'

// 显示设置页路由。
export const Route = createFileRoute('/_authenticated/settings/display')({
  component: SettingsDisplay,
})
