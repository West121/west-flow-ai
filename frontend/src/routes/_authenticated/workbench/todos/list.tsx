import { createFileRoute } from '@tanstack/react-router'
import { WorkbenchTodoListPage } from '@/features/workbench/pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

// 待办列表路由只负责挂载页面并校验查询参数。
export const Route = createFileRoute('/_authenticated/workbench/todos/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: WorkbenchTodoListPage,
})
