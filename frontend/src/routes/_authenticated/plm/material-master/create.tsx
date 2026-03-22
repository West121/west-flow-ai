import { createFileRoute } from '@tanstack/react-router'
import { PLMMaterialChangeCreatePage } from '@/features/plm/pages'

// 物料主数据变更发起路由只负责挂载申请页。
export const Route = createFileRoute(
  '/_authenticated/plm/material-master/create'
)({
  component: PLMMaterialChangeCreatePage,
})
