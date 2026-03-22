import { createFileRoute } from '@tanstack/react-router'
import { NotificationChannelCreatePage } from '@/features/system/notification-channel-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

// 通知渠道新建路由只负责权限校验和挂载表单页。
export const Route = createFileRoute('/_authenticated/system/notification-channels/create')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: NotificationChannelCreatePage,
})
