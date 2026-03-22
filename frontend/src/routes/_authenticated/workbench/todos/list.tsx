import { createFileRoute } from '@tanstack/react-router'
import { WorkbenchTodoListPage } from '@/features/workbench/pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/workbench/todos/list')({
  validateSearch: listQuerySearchSchema,
  component: WorkbenchTodoListPage,
})
