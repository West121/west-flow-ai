import { createFileRoute } from '@tanstack/react-router'
import { DepartmentDetailPage } from '@/features/system/org-pages'

export const Route = createFileRoute(
  '/_authenticated/system/departments/$departmentId/'
)({
  component: DepartmentDetailRoute,
})

function DepartmentDetailRoute() {
  const { departmentId } = Route.useParams()

  return <DepartmentDetailPage departmentId={departmentId} />
}
