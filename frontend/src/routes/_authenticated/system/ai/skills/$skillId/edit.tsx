import { createFileRoute } from '@tanstack/react-router'
import { AiSkillEditPage } from '@/features/ai-admin/registry-pages'

// AI 技能编辑路由。
export const Route = createFileRoute('/_authenticated/system/ai/skills/$skillId/edit')({
  component: RouteComponent,
})

function RouteComponent() {
  const { skillId } = Route.useParams()

  return <AiSkillEditPage skillId={skillId} />
}
