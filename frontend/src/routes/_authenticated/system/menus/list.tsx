import { createFileRoute } from '@tanstack/react-router'
import { MenusListPage } from '@/features/system/menu-pages'
import { listQuerySearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/menus/list')({
  validateSearch: listQuerySearchSchema,
  component: MenusListPage,
})
