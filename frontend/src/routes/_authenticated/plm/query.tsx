import { createFileRoute } from '@tanstack/react-router'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'
import { PLMQueryPage } from '@/features/plm/pages'

// PLM 流程查询路由只负责挂载列表页并校验查询参数。
export const Route = createFileRoute('/_authenticated/plm/query')({
  validateSearch: listQuerySearchSchema,
  component: PLMQueryPage,
})
