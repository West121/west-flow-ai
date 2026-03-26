import { createFileRoute } from '@tanstack/react-router'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'
import { OAQueryPage } from '@/features/oa/pages'

// OA 查询路由只负责挂载列表页并校验查询参数。
export const Route = createFileRoute('/_authenticated/oa/query')({
  validateSearch: listQueryRouteSearchSchema,
  component: OAQueryPage,
})
