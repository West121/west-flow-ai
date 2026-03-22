import { createFileRoute } from '@tanstack/react-router'
import { WorkflowDesignerPage } from '@/features/workflow/pages'

export const Route = createFileRoute('/_authenticated/workflow/designer')({
  component: WorkflowDesignerPage,
})
