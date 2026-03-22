import { createFileRoute } from '@tanstack/react-router'
import { OALeaveBillDetailPage } from '@/features/oa/pages'

// 请假详情路由只负责把单号传给详情页。
export const Route = createFileRoute('/_authenticated/oa/leave/$billId')({
  component: OALeaveBillDetailPage,
})
