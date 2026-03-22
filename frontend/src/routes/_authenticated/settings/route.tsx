import { createFileRoute } from '@tanstack/react-router'
import { Settings } from '@/features/settings'

// 设置模块根路由，承载二级导航布局。
export const Route = createFileRoute('/_authenticated/settings')({
  component: Settings,
})
