import { createFileRoute } from '@tanstack/react-router'
import { SystemMonitorTriggerExecutionDetailPage } from '@/features/system/monitor-pages'

// 触发执行详情路由，承载单条执行明细页面。
export const Route = createFileRoute('/_authenticated/system/monitor/trigger-executions/$executionId/')({
  component: SystemMonitorTriggerExecutionDetailPage,
})
