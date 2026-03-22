import { createFileRoute } from '@tanstack/react-router'
import { DepartmentsListPage } from '@/features/system/org-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute(
  '/_authenticated/system/departments/list'
)({
  validateSearch: listQuerySearchSchema,
  component: DepartmentsListPage,
})
