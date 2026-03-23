import { createFileRoute } from '@tanstack/react-router'
import { AiSkillListPage } from '@/features/ai-admin/registry-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// AI 技能列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/skills/list')({
  validateSearch: listQuerySearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()

  return <AiSkillListPage search={search} navigate={navigate} />
}
