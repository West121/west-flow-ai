import { createFileRoute } from '@tanstack/react-router'
import { SystemLoginLogListPage } from '@/features/system/log-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/logs/login/list')({
  validateSearch: listQuerySearchSchema,
  component: SystemLoginLogListPage,
})
