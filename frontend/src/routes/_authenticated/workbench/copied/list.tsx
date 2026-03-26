import { createFileRoute } from '@tanstack/react-router'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'
import { WorkbenchCopiedListPage } from '@/features/workbench/pages'

// 抄送列表路由只负责挂载页面并校验查询参数。
export const Route = createFileRoute('/_authenticated/workbench/copied/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: WorkbenchCopiedListPage,
})
