import { createFileRoute } from '@tanstack/react-router'
import { AgentsListPage } from '@/features/system/agent-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/agents/list')({
  beforeLoad: ensureProcessAdminRouteAccess,
  validateSearch: listQuerySearchSchema,
  component: AgentsListPage,
})
