import { createFileRoute } from '@tanstack/react-router'
import { OALeaveListPage } from '@/features/oa/pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/oa/leave/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: OALeaveListPage,
})
