import { createFileRoute } from '@tanstack/react-router'
import { AiMcpEditPage } from '@/features/ai-admin/registry-pages'

// AI MCP 编辑路由。
export const Route = createFileRoute('/_authenticated/system/ai/mcps/$mcpId/edit')({
  component: RouteComponent,
})

function RouteComponent() {
  const { mcpId } = Route.useParams()

  return <AiMcpEditPage mcpId={mcpId} />
}
