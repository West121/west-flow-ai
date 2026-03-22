import { createFileRoute } from '@tanstack/react-router'
import { z } from 'zod'
import { WorkflowDesignerPage } from '@/features/workflow/pages'

export const Route = createFileRoute('/_authenticated/workflow/designer')({
  validateSearch: z.object({
    processDefinitionId: z.string().optional(),
  }),
  component: WorkflowDesignerPage,
})
