import { createFileRoute } from '@tanstack/react-router'
import { AiSkillListPage } from '@/features/ai-admin/registry-pages'
import { listQueryRouteSearchSchema, normalizeListQuerySearch } from '@/features/shared/table/query-contract'

// AI 技能列表路由。
export const Route = createFileRoute('/_authenticated/system/ai/skills/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()

  return <AiSkillListPage search={search} navigate={navigate} />
}
