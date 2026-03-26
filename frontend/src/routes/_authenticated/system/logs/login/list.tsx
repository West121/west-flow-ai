import { createFileRoute } from '@tanstack/react-router'
import { SystemLoginLogListPage } from '@/features/system/log-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/logs/login/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: SystemLoginLogListPage,
})
