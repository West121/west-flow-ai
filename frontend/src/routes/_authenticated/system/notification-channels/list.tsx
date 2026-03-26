import { createFileRoute } from '@tanstack/react-router'
import { NotificationChannelsListPage } from '@/features/system/notification-channel-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

// 通知渠道列表路由只负责权限校验、挂载列表页并校验查询参数。
export const Route = createFileRoute('/_authenticated/system/notification-channels/list')({
  beforeLoad: ensureProcessAdminRouteAccess,
  validateSearch: listQueryRouteSearchSchema,
  component: NotificationChannelsListPage,
})
