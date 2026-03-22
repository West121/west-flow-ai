import { createFileRoute } from '@tanstack/react-router'
import { RoleCreatePage } from '@/features/system/role-pages'

export const Route = createFileRoute('/_authenticated/system/roles/create')({
  component: RoleCreatePage,
})
