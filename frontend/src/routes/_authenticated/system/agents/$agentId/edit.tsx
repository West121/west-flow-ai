import { createFileRoute } from '@tanstack/react-router'
import { AgentEditPage } from '@/features/system/agent-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

// 处理人编辑路由只负责权限校验和参数传递。
export const Route = createFileRoute('/_authenticated/system/agents/$agentId/edit')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: AgentEditRoute,
})

function AgentEditRoute() {
  const { agentId } = Route.useParams() as { agentId: string }

  return <AgentEditPage agentId={agentId} />
}
