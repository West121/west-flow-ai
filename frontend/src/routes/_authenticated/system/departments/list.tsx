import { createFileRoute } from '@tanstack/react-router'
import { DepartmentsListPage } from '@/features/system/org-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

// 部门列表路由只负责挂载列表页并校验查询参数。
export const Route = createFileRoute(
  '/_authenticated/system/departments/list'
)({
  validateSearch: listQueryRouteSearchSchema,
  component: DepartmentsListPage,
})
