import { createFileRoute } from '@tanstack/react-router'
import { listQueryRouteSearchSchema, normalizeListQuerySearch } from '@/features/shared/table/query-contract'
import { PLMECRListPage } from '@/features/plm/pages'

export const Route = createFileRoute('/_authenticated/plm/ecr/')({
  validateSearch: listQueryRouteSearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()

  return <PLMECRListPage search={search} navigate={navigate} />
}
