import { createFileRoute } from '@tanstack/react-router'
import { CompanyCreatePage } from '@/features/system/org-pages'

export const Route = createFileRoute(
  '/_authenticated/system/companies/create'
)({
  component: CompanyCreatePage,
})
