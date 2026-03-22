import { createFileRoute } from '@tanstack/react-router'
import { OAExpenseBillDetailPage } from '@/features/oa/pages'

export const Route = createFileRoute('/_authenticated/oa/expense/$billId')({
  component: OAExpenseBillDetailPage,
})
