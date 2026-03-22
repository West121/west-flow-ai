import { createFileRoute } from '@tanstack/react-router'
import { OACommonBillDetailPage } from '@/features/oa/pages'

export const Route = createFileRoute('/_authenticated/oa/common/$billId')({
  component: OACommonBillDetailPage,
})
