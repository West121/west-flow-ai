import { createFileRoute } from '@tanstack/react-router'
import { SystemMonitorTriggerExecutionListPage } from '@/features/system/monitor-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

// 触发执行监控列表页。
export const Route = createFileRoute('/_authenticated/system/monitor/trigger-executions/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: SystemMonitorTriggerExecutionListPage,
})
