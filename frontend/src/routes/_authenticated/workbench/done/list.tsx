import { createFileRoute } from '@tanstack/react-router'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'
import { WorkbenchDoneListPage } from '@/features/workbench/pages'

// 已办列表路由只负责挂载页面并校验查询参数。
export const Route = createFileRoute('/_authenticated/workbench/done/list')({
  validateSearch: listQuerySearchSchema,
  component: WorkbenchDoneListPage,
})
