import { createFileRoute } from '@tanstack/react-router'
import { UserCreatePage } from '@/features/system/user-pages'

export const Route = createFileRoute('/_authenticated/system/users/create')({
  component: UserCreatePage,
})
