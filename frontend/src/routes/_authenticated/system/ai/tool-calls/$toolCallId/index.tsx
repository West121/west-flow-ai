import { createFileRoute } from '@tanstack/react-router'
import { AiToolCallDetailPage } from '@/features/ai-admin/record-pages'

// AI 工具调用记录详情路由。
export const Route = createFileRoute('/_authenticated/system/ai/tool-calls/$toolCallId/')({
  component: RouteComponent,
})

function RouteComponent() {
  const { toolCallId } = Route.useParams()

  return <AiToolCallDetailPage toolCallId={toolCallId} />
}
