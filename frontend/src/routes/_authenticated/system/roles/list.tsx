import { createFileRoute } from '@tanstack/react-router'
import { RolesListPage } from '@/features/system/role-pages'
import { listQueryRouteSearchSchema } from '@/features/shared/table/query-contract'

// 角色列表路由只负责挂载列表页并校验查询参数。
export const Route = createFileRoute('/_authenticated/system/roles/list')({
  validateSearch: listQueryRouteSearchSchema,
  component: RolesListPage,
})
