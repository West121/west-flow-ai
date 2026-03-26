import { createFileRoute } from '@tanstack/react-router'
import { SystemAuditLogListPage } from '@/features/system/log-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/logs/audit/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: SystemAuditLogListPage,
})
