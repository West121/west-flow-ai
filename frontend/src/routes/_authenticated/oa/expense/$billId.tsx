import { createFileRoute } from '@tanstack/react-router'
import { OAExpenseBillDetailPage } from '@/features/oa/pages'

// 报销详情路由只负责把单号传给详情页。
export const Route = createFileRoute('/_authenticated/oa/expense/$billId')({
  component: OAExpenseBillDetailPage,
})
