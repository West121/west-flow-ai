import { createFileRoute } from '@tanstack/react-router'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'
import { WorkbenchCopiedListPage } from '@/features/workbench/pages'

export const Route = createFileRoute('/_authenticated/workbench/copied/list')({
  validateSearch: listQuerySearchSchema,
  component: WorkbenchCopiedListPage,
})
