import { createFileRoute } from '@tanstack/react-router'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'
import { WorkbenchDoneListPage } from '@/features/workbench/pages'

export const Route = createFileRoute('/_authenticated/workbench/done/list')({
  validateSearch: listQuerySearchSchema,
  component: WorkbenchDoneListPage,
})
