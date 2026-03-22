import { createFileRoute } from '@tanstack/react-router'
import { CompanyEditPage } from '@/features/system/org-pages'

// 公司编辑路由只负责把参数传给编辑页。
export const Route = createFileRoute(
  '/_authenticated/system/companies/$companyId/edit'
)({
  component: CompanyEditRoute,
})

function CompanyEditRoute() {
  const { companyId } = Route.useParams()

  return <CompanyEditPage companyId={companyId} />
}
