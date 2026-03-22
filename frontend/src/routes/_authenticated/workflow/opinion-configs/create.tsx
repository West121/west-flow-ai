import { createFileRoute } from '@tanstack/react-router'
import { ApprovalOpinionConfigCreatePage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/opinion-configs/create')({
  component: ApprovalOpinionConfigCreatePage,
})
