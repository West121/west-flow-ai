import { createFileRoute } from '@tanstack/react-router'
import { WorkflowBindingCreatePage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/bindings/create')({
  component: WorkflowBindingCreatePage,
})
