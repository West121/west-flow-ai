import { createFileRoute } from '@tanstack/react-router'
import {
  listQueryRouteSearchSchema,
  normalizeListQuerySearch,
} from '@/features/shared/table/query-contract'
import { PLMProjectListPage } from '@/features/plm/project-pages'

export const Route = createFileRoute('/_authenticated/plm/projects/')({
  validateSearch: listQueryRouteSearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()

  return <PLMProjectListPage search={search} navigate={navigate} />
}
