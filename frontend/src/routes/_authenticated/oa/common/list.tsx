import { createFileRoute } from '@tanstack/react-router'
import { OACommonListPage } from '@/features/oa/pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/oa/common/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: OACommonListPage,
})
