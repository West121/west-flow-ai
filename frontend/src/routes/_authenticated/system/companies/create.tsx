import { createFileRoute } from '@tanstack/react-router'
import { CompanyCreatePage } from '@/features/system/org-pages'

// 公司新建路由只负责挂载表单页。
export const Route = createFileRoute(
  '/_authenticated/system/companies/create'
)({
  component: CompanyCreatePage,
})
