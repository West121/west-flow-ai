import { createFileRoute } from '@tanstack/react-router'
import { OACommonListPage } from '@/features/oa/pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/oa/common/list')({
  validateSearch: listQuerySearchSchema,
  component: OACommonListPage,
})
