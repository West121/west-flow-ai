import { createFileRoute } from '@tanstack/react-router'
import { MenusListPage } from '@/features/system/menu-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

export const Route = createFileRoute('/_authenticated/system/menus/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: MenusListPage,
})
