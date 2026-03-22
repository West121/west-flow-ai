import { createFileRoute } from '@tanstack/react-router'
import { OALeaveBillDetailPage } from '@/features/oa/pages'

export const Route = createFileRoute('/_authenticated/oa/leave/$billId')({
  component: OALeaveBillDetailPage,
})
