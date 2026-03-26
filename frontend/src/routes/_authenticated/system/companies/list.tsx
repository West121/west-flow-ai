import { createFileRoute } from '@tanstack/react-router'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'
import { CompaniesListPage } from '@/features/system/org-pages'

export const Route = createFileRoute('/_authenticated/system/companies/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: CompaniesListPage,
})
