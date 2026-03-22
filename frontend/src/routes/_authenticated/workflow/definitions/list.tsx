import { createFileRoute } from '@tanstack/react-router'
import { WorkflowDefinitionsListPage } from '@/features/workflow/pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute(
  '/_authenticated/workflow/definitions/list'
)({
  validateSearch: listQuerySearchSchema,
  component: WorkflowDefinitionsListPage,
})
