import { createFileRoute } from '@tanstack/react-router'
import { RoleDetailPageEntry } from '@/features/system/role-pages'

// 角色详情路由只负责把参数传给详情页。
export const Route = createFileRoute('/_authenticated/system/roles/$roleId/')({
  component: RoleDetailRoute,
})

function RoleDetailRoute() {
  const { roleId } = Route.useParams()

  return <RoleDetailPageEntry roleId={roleId} />
}
