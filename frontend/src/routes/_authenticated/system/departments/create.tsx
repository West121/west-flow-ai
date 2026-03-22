import { createFileRoute } from '@tanstack/react-router'
import { DepartmentCreatePage } from '@/features/system/org-pages'

// 部门新建路由只负责挂载表单页。
export const Route = createFileRoute(
  '/_authenticated/system/departments/create'
)({
  component: DepartmentCreatePage,
})
