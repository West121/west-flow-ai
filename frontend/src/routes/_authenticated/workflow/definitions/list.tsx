import { createFileRoute } from '@tanstack/react-router'
import { WorkflowDefinitionsListPage } from '@/features/workflow/pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

// 流程定义列表路由只负责挂载列表页并校验查询参数。
export const Route = createFileRoute(
  '/_authenticated/workflow/definitions/list'
)({
  validateSearch: listQueryRouteSearchSchema,
  component: WorkflowDefinitionsListPage,
})
