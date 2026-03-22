import { createFileRoute } from '@tanstack/react-router'
import { CompanyDetailPage } from '@/features/system/org-pages'

// 公司详情路由只负责把参数传给详情页。
export const Route = createFileRoute(
  '/_authenticated/system/companies/$companyId/'
)({
  component: CompanyDetailRoute,
})

function CompanyDetailRoute() {
  const { companyId } = Route.useParams()

  return <CompanyDetailPage companyId={companyId} />
}
