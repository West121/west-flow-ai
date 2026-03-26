import { createFileRoute } from '@tanstack/react-router'
import { WorkflowVersionsListPage } from '@/features/workflow/management-pages'
import {
  listQueryRouteSearchSchema,
  normalizeListQuerySearch,
} from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/versions/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: VersionsListRoute,
})

function VersionsListRoute() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()
  return <WorkflowVersionsListPage search={search} navigate={navigate} />
}
