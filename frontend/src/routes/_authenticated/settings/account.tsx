import { createFileRoute } from '@tanstack/react-router'
import { SettingsAccount } from '@/features/settings/account'

// 账号设置页路由。
export const Route = createFileRoute('/_authenticated/settings/account')({
  component: SettingsAccount,
})
