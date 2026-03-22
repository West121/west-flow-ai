import { createFileRoute } from '@tanstack/react-router'
import { DepartmentEditPage } from '@/features/system/org-pages'

// 部门编辑路由只负责把参数传给编辑页。
export const Route = createFileRoute(
  '/_authenticated/system/departments/$departmentId/edit'
)({
  component: DepartmentEditRoute,
})

function DepartmentEditRoute() {
  const { departmentId } = Route.useParams()

  return <DepartmentEditPage departmentId={departmentId} />
}
