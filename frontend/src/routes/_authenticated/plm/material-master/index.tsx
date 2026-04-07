import { createFileRoute } from '@tanstack/react-router'
import { listQueryRouteSearchSchema, normalizeListQuerySearch } from '@/features/shared/table/query-contract'
import { PLMMaterialChangeListPage } from '@/features/plm/pages'

export const Route = createFileRoute('/_authenticated/plm/material-master/')({
  validateSearch: listQueryRouteSearchSchema,
  component: RouteComponent,
})

function RouteComponent() {
  const search = normalizeListQuerySearch(Route.useSearch())
  const navigate = Route.useNavigate()

  return <PLMMaterialChangeListPage search={search} navigate={navigate} />
}
