import { createFileRoute } from '@tanstack/react-router'
import { z } from 'zod'
import { AICopilotPage } from '@/features/ai'

// AI Copilot 独立入口路由，承载全局聊天面板和会话壳层。
export const Route = createFileRoute('/_authenticated/ai')({
  validateSearch: z.object({
    sourceRoute: z.string().optional().catch(''),
  }),
  component: AICopilotRoute,
})

function AICopilotRoute() {
  const search = Route.useSearch()

  return <AICopilotPage sourceRoute={search.sourceRoute ?? ''} />
}
