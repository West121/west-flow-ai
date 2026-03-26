import { createFileRoute } from '@tanstack/react-router'
import { WorkflowInstancesListPage } from '@/features/workflow/management-pages'
import {
  listQueryRouteSearchSchema,
  normalizeListQuerySearch,
} from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/instances/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: InstancesListRoute,
})

function InstancesListRoute() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()
  return <WorkflowInstancesListPage search={search} navigate={navigate} />
}
