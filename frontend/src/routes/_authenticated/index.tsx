import { createFileRoute } from '@tanstack/react-router'
import { Dashboard } from '@/features/dashboard'

// 已登录后的首页路由，直接进入平台总览。
export const Route = createFileRoute('/_authenticated/')({
  component: Dashboard,
})
