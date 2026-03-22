import { createFileRoute } from '@tanstack/react-router'
import { WorkflowVersionsListPage } from '@/features/workflow/management-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/versions/list')({
  validateSearch: listQuerySearchSchema,
  component: VersionsListRoute,
})

function VersionsListRoute() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()
  return <WorkflowVersionsListPage search={search} navigate={navigate} />
}
