import { createFileRoute } from '@tanstack/react-router'
import { AICopilotPage } from '@/features/ai'

// AI Copilot 独立入口路由，承载全局聊天面板和会话壳层。
export const Route = createFileRoute('/_authenticated/ai')({
  component: AICopilotPage,
})
