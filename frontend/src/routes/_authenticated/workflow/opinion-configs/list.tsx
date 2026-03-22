import { createFileRoute } from '@tanstack/react-router'
import { ApprovalOpinionConfigsListPage } from '@/features/workflow/management-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/opinion-configs/list')({
  validateSearch: listQuerySearchSchema,
  component: OpinionConfigsListRoute,
})

function OpinionConfigsListRoute() {
  const search = Route.useSearch()
  const navigate = Route.useNavigate()
  return <ApprovalOpinionConfigsListPage search={search} navigate={navigate} />
}
