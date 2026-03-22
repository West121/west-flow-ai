import { createFileRoute } from '@tanstack/react-router'
import { WorkflowOperationLogsListPage } from '@/features/workflow/management-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/operation-logs/list')({
  validateSearch: listQuerySearchSchema,
  component: OperationLogsListRoute,
})

function OperationLogsListRoute() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()
  return <WorkflowOperationLogsListPage search={search} navigate={navigate} />
}
