import { createFileRoute } from '@tanstack/react-router'
import { WorkflowBindingsListPage } from '@/features/workflow/management-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/bindings/list')({
  validateSearch: listQuerySearchSchema,
  component: BindingsListRoute,
})

function BindingsListRoute() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()
  return <WorkflowBindingsListPage search={search} navigate={navigate} />
}
