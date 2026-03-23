import { createFileRoute } from '@tanstack/react-router'
import { AiToolDetailPage } from '@/features/ai-admin/registry-pages'

// AI 工具详情路由。
export const Route = createFileRoute('/_authenticated/system/ai/tools/$toolId/')({
  component: RouteComponent,
})

function RouteComponent() {
  const { toolId } = Route.useParams()

  return <AiToolDetailPage toolId={toolId} />
}
