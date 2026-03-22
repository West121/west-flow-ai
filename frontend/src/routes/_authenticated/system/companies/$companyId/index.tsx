import { createFileRoute } from '@tanstack/react-router'
import { CompanyDetailPage } from '@/features/system/org-pages'

export const Route = createFileRoute(
  '/_authenticated/system/companies/$companyId/'
)({
  component: CompanyDetailRoute,
})

function CompanyDetailRoute() {
  const { companyId } = Route.useParams()

  return <CompanyDetailPage companyId={companyId} />
}
