import { createFileRoute } from '@tanstack/react-router'
import { RoleCreatePage } from '@/features/system/role-pages'

// 角色新建路由只负责挂载表单页。
export const Route = createFileRoute('/_authenticated/system/roles/create')({
  component: RoleCreatePage,
})
