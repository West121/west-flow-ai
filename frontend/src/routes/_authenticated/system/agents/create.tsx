import { createFileRoute } from '@tanstack/react-router'
import { AgentCreatePage } from '@/features/system/agent-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

export const Route = createFileRoute('/_authenticated/system/agents/create')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: AgentCreatePage,
})
