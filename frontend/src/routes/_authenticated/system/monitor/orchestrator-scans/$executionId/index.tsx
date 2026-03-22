import { createFileRoute } from '@tanstack/react-router'
import { SystemMonitorOrchestratorScanDetailPage } from '@/features/system/monitor-pages'

// 编排扫描详情路由，承载单条执行记录页面。
export const Route = createFileRoute('/_authenticated/system/monitor/orchestrator-scans/$executionId/')({
  component: SystemMonitorOrchestratorScanDetailPage,
})
