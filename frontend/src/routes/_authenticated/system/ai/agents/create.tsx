import { createFileRoute } from '@tanstack/react-router'
import { AiAgentCreatePage } from '@/features/ai-admin/registry-pages'

// AI 智能体新建路由。
export const Route = createFileRoute('/_authenticated/system/ai/agents/create')({
  component: AiAgentCreatePage,
})
