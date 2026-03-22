import { createFileRoute } from '@tanstack/react-router'
import { WorkflowInstancesListPage } from '@/features/workflow/management-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/instances/list')({
  validateSearch: listQuerySearchSchema,
  component: InstancesListRoute,
})

function InstancesListRoute() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()
  return <WorkflowInstancesListPage search={search} navigate={navigate} />
}
