import { createFileRoute } from '@tanstack/react-router'
import { AiSkillDetailPage } from '@/features/ai-admin/registry-pages'

// AI 技能详情路由。
export const Route = createFileRoute('/_authenticated/system/ai/skills/$skillId/')({
  component: RouteComponent,
})

function RouteComponent() {
  const { skillId } = Route.useParams()

  return <AiSkillDetailPage skillId={skillId} />
}
