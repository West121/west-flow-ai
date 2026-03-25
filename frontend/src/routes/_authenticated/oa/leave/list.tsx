import { createFileRoute } from '@tanstack/react-router'
import { OALeaveListPage } from '@/features/oa/pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/oa/leave/list')({
  validateSearch: listQuerySearchSchema,
  component: OALeaveListPage,
})
