import { createFileRoute } from '@tanstack/react-router'
import { SettingsAppearance } from '@/features/settings/appearance'

// 外观设置页路由。
export const Route = createFileRoute('/_authenticated/settings/appearance')({
  component: SettingsAppearance,
})
