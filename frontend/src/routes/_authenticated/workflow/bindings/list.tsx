import { createFileRoute } from '@tanstack/react-router'
import { WorkflowBindingsListPage } from '@/features/workflow/management-pages'
import {
  listQueryRouteSearchSchema,
  normalizeListQuerySearch,
} from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/bindings/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: BindingsListRoute,
})

function BindingsListRoute() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()
  return <WorkflowBindingsListPage search={search} navigate={navigate} />
}
