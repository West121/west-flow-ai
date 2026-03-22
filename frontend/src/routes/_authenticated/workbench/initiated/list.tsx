import { createFileRoute } from '@tanstack/react-router'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'
import { WorkbenchInitiatedListPage } from '@/features/workbench/pages'

export const Route = createFileRoute('/_authenticated/workbench/initiated/list')({
  validateSearch: listQuerySearchSchema,
  component: WorkbenchInitiatedListPage,
})
