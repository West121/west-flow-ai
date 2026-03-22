import { createFileRoute } from '@tanstack/react-router'
import { TriggersListPage } from '@/features/system/trigger-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/triggers/list')({
  beforeLoad: ensureProcessAdminRouteAccess,
  validateSearch: listQuerySearchSchema,
  component: TriggersListPage,
})
