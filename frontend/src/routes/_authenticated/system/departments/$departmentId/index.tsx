import { createFileRoute } from '@tanstack/react-router'
import { DepartmentDetailPage } from '@/features/system/org-pages'

// 部门详情路由只负责把参数传给详情页。
export const Route = createFileRoute(
  '/_authenticated/system/departments/$departmentId/'
)({
  component: DepartmentDetailRoute,
})

function DepartmentDetailRoute() {
  const { departmentId } = Route.useParams()

  return <DepartmentDetailPage departmentId={departmentId} />
}
