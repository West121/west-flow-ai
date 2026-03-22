import { createFileRoute } from '@tanstack/react-router'
import { MenuCreatePage } from '@/features/system/menu-pages'

export const Route = createFileRoute('/_authenticated/system/menus/create')({
  component: MenuCreatePage,
})
