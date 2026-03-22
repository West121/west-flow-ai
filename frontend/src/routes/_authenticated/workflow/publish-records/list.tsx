import { createFileRoute } from '@tanstack/react-router'
import { WorkflowPublishRecordsListPage } from '@/features/workflow/management-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/publish-records/list')({
  validateSearch: listQuerySearchSchema,
  component: PublishRecordsListRoute,
})

function PublishRecordsListRoute() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()
  return <WorkflowPublishRecordsListPage search={search} navigate={navigate} />
}
