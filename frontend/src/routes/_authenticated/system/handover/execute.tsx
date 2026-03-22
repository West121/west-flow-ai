import { createFileRoute } from '@tanstack/react-router'
import { SystemHandoverExecutePage } from '@/features/system/agent-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

export const Route = createFileRoute('/_authenticated/system/handover/execute')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: SystemHandoverExecutePage,
})
