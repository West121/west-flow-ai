import { createFileRoute } from '@tanstack/react-router'
import { RolesListPage } from '@/features/system/role-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/roles/list')({
  validateSearch: listQuerySearchSchema,
  component: RolesListPage,
})
