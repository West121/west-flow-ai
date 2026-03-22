import { createFileRoute } from '@tanstack/react-router'
import { UserCreatePage } from '@/features/system/user-pages'

// 用户新建路由只负责挂载表单页。
export const Route = createFileRoute('/_authenticated/system/users/create')({
  component: UserCreatePage,
})
