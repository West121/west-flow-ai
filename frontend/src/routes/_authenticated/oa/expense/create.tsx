import { createFileRoute } from '@tanstack/react-router'
import { OAExpenseCreatePage } from '@/features/oa/pages'

export const Route = createFileRoute('/_authenticated/oa/expense/create')({
  component: OAExpenseCreatePage,
})
