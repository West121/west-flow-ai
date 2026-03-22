import { createFileRoute } from '@tanstack/react-router'
import { TriggersListPage } from '@/features/system/trigger-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// 触发器列表路由只负责权限校验、挂载列表页并校验查询参数。
export const Route = createFileRoute('/_authenticated/system/triggers/list')({
  beforeLoad: ensureProcessAdminRouteAccess,
  validateSearch: listQuerySearchSchema,
  component: TriggersListPage,
})
