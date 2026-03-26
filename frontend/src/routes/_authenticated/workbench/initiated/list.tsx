import { createFileRoute } from '@tanstack/react-router'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'
import { WorkbenchInitiatedListPage } from '@/features/workbench/pages'

// 我发起列表路由只负责挂载页面并校验查询参数。
export const Route = createFileRoute('/_authenticated/workbench/initiated/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: WorkbenchInitiatedListPage,
})
