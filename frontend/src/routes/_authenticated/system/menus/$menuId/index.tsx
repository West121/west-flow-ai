import { createFileRoute } from '@tanstack/react-router'
import { MenuDetailPageRoute } from '@/features/system/menu-pages'

export const Route = createFileRoute('/_authenticated/system/menus/$menuId/')({
  component: MenuDetailPageRoute,
})
