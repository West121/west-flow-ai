import { createFileRoute } from '@tanstack/react-router'
import { WorkflowOperationLogsListPage } from '@/features/workflow/management-pages'
import {
  listQueryRouteSearchSchema,
  normalizeListQuerySearch,
} from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/operation-logs/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: OperationLogsListRoute,
})

function OperationLogsListRoute() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()
  return <WorkflowOperationLogsListPage search={search} navigate={navigate} />
}
