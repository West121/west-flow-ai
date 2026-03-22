import { createFileRoute } from '@tanstack/react-router'
import { OACommonBillDetailPage } from '@/features/oa/pages'

// 通用申请详情路由只负责把单号传给详情页。
export const Route = createFileRoute('/_authenticated/oa/common/$billId')({
  component: OACommonBillDetailPage,
})
