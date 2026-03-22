import { createFileRoute } from '@tanstack/react-router'
import { AgentDetailPage } from '@/features/system/agent-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

export const Route = createFileRoute('/_authenticated/system/agents/$agentId/')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: AgentDetailRoute,
})

function AgentDetailRoute() {
  const { agentId } = Route.useParams() as { agentId: string }

  return <AgentDetailPage agentId={agentId} />
}
