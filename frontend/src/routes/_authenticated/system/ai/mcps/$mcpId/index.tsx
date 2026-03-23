import { createFileRoute } from '@tanstack/react-router'
import { AiMcpDetailPage } from '@/features/ai-admin/registry-pages'

// AI MCP 详情路由。
export const Route = createFileRoute('/_authenticated/system/ai/mcps/$mcpId/')({
  component: RouteComponent,
})

function RouteComponent() {
  const { mcpId } = Route.useParams()

  return <AiMcpDetailPage mcpId={mcpId} />
}
