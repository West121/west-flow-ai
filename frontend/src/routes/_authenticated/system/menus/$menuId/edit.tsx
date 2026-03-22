import { createFileRoute } from '@tanstack/react-router'
import { MenuEditPage } from '@/features/system/menu-pages'

export const Route = createFileRoute('/_authenticated/system/menus/$menuId/edit')(
  {
    component: MenuEditPage,
  }
)
