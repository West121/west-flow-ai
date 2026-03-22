import { createFileRoute } from '@tanstack/react-router'
import { AgentCreatePage } from '@/features/system/agent-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

// 处理人新建路由只负责权限校验和挂载表单页。
export const Route = createFileRoute('/_authenticated/system/agents/create')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: AgentCreatePage,
})
