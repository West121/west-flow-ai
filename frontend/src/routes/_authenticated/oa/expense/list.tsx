import { createFileRoute } from '@tanstack/react-router'
import { OAExpenseListPage } from '@/features/oa/pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/oa/expense/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: OAExpenseListPage,
})
