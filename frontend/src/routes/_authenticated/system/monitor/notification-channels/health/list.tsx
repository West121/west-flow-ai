import { createFileRoute } from '@tanstack/react-router'
import { SystemMonitorNotificationChannelHealthListPage } from '@/features/system/monitor-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

// 通知渠道健康监控列表页。
export const Route = createFileRoute('/_authenticated/system/monitor/notification-channels/health/list')({
  validateSearch: listQuerySearchSchema,
  component: SystemMonitorNotificationChannelHealthListPage,
})
