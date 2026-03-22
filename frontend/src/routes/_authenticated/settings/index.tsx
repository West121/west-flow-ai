import { createFileRoute } from '@tanstack/react-router'
import { SettingsProfile } from '@/features/settings/profile'

// 设置主页路由，默认展示个人资料页。
export const Route = createFileRoute('/_authenticated/settings/')({
  component: SettingsProfile,
})
