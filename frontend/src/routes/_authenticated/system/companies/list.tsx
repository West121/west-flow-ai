import { createFileRoute } from '@tanstack/react-router'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'
import { CompaniesListPage } from '@/features/system/org-pages'

export const Route = createFileRoute('/_authenticated/system/companies/list')({
  validateSearch: listQuerySearchSchema,
  component: CompaniesListPage,
})
