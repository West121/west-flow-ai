import { createFileRoute } from '@tanstack/react-router'
import { WorkflowPublishRecordsListPage } from '@/features/workflow/management-pages'
import {
  listQueryRouteSearchSchema,
  normalizeListQuerySearch,
} from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/publish-records/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: PublishRecordsListRoute,
})

function PublishRecordsListRoute() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()
  return <WorkflowPublishRecordsListPage search={search} navigate={navigate} />
}
