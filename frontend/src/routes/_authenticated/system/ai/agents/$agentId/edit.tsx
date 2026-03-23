import { createFileRoute } from '@tanstack/react-router'
import { AiAgentEditPage } from '@/features/ai-admin/registry-pages'

// AI 智能体编辑路由。
export const Route = createFileRoute('/_authenticated/system/ai/agents/$agentId/edit')({
  component: RouteComponent,
})

function RouteComponent() {
  const { agentId } = Route.useParams()

  return <AiAgentEditPage agentId={agentId} />
}
