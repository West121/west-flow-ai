import { createFileRoute } from '@tanstack/react-router'
import { OAExpenseCreatePage } from '@/features/oa/pages'

// 报销发起路由只负责挂载报销表单页。
export const Route = createFileRoute('/_authenticated/oa/expense/create')({
  component: OAExpenseCreatePage,
})
