import { createFileRoute } from '@tanstack/react-router'
import { ApprovalOpinionConfigsListPage } from '@/features/workflow/management-pages'
import {
  listQueryRouteSearchSchema,
  normalizeListQuerySearch,
} from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workflow/opinion-configs/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: OpinionConfigsListRoute,
})

function OpinionConfigsListRoute() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()
  return <ApprovalOpinionConfigsListPage search={search} navigate={navigate} />
}
