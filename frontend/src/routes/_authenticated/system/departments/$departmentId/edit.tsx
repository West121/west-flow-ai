import { createFileRoute } from '@tanstack/react-router'
import { DepartmentEditPage } from '@/features/system/org-pages'

export const Route = createFileRoute(
  '/_authenticated/system/departments/$departmentId/edit'
)({
  component: DepartmentEditRoute,
})

function DepartmentEditRoute() {
  const { departmentId } = Route.useParams()

  return <DepartmentEditPage departmentId={departmentId} />
}
