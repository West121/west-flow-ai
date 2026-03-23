import { createFileRoute } from '@tanstack/react-router'
import { AiToolEditPage } from '@/features/ai-admin/registry-pages'

// AI 工具编辑路由。
export const Route = createFileRoute('/_authenticated/system/ai/tools/$toolId/edit')({
  component: RouteComponent,
})

function RouteComponent() {
  const { toolId } = Route.useParams()

  return <AiToolEditPage toolId={toolId} />
}
