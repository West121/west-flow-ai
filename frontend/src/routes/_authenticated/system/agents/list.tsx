import { createFileRoute } from '@tanstack/react-router'
import { AgentsListPage } from '@/features/system/agent-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// 处理人列表路由只负责权限校验、挂载列表页并校验查询参数。
export const Route = createFileRoute('/_authenticated/system/agents/list')({
  beforeLoad: ensureProcessAdminRouteAccess,
  validateSearch: listQuerySearchSchema,
  component: AgentsListPage,
})
