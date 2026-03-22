import { createFileRoute } from '@tanstack/react-router'
import { CompanyEditPage } from '@/features/system/org-pages'

export const Route = createFileRoute(
  '/_authenticated/system/companies/$companyId/edit'
)({
  component: CompanyEditRoute,
})

function CompanyEditRoute() {
  const { companyId } = Route.useParams()

  return <CompanyEditPage companyId={companyId} />
}
