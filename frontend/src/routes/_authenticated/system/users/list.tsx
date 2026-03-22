import { createFileRoute } from '@tanstack/react-router'
import { UsersListPage } from '@/features/system/user-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/users/list')({
  validateSearch: listQuerySearchSchema,
  component: UsersListPage,
})
