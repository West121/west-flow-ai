import { createFileRoute } from '@tanstack/react-router'
import { DepartmentCreatePage } from '@/features/system/org-pages'

export const Route = createFileRoute(
  '/_authenticated/system/departments/create'
)({
  component: DepartmentCreatePage,
})
