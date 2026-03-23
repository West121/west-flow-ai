import { createFileRoute } from '@tanstack/react-router'
import { AiAgentDetailPage } from '@/features/ai-admin/registry-pages'

// AI 智能体详情路由。
export const Route = createFileRoute('/_authenticated/system/ai/agents/$agentId/')({
  component: RouteComponent,
})

function RouteComponent() {
  const { agentId } = Route.useParams()

  return <AiAgentDetailPage agentId={agentId} />
}
