import { createFileRoute } from '@tanstack/react-router'
import { SystemMonitorNotificationChannelHealthDetailPage } from '@/features/system/monitor-pages'

// 通知渠道健康详情路由，承载单个渠道监控详情页。
export const Route = createFileRoute('/_authenticated/system/monitor/notification-channels/health/$channelId/')({
  component: SystemMonitorNotificationChannelHealthDetailPage,
})
