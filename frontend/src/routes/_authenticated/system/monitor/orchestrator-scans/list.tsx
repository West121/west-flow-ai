import { createFileRoute } from '@tanstack/react-router'
import { SystemMonitorOrchestratorScanListPage } from '@/features/system/monitor-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

// 编排扫描监控列表页。
export const Route = createFileRoute('/_authenticated/system/monitor/orchestrator-scans/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: SystemMonitorOrchestratorScanListPage,
})
