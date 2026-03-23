import { createFileRoute } from '@tanstack/react-router'
import { RoleEditPage } from '@/features/system/role-pages'

// 角色编辑路由只负责把参数传给编辑页。
export const Route = createFileRoute(
  '/_authenticated/system/roles/$roleId/edit'
)({
  component: RoleEditRoute,
})

function RoleEditRoute() {
  const { roleId } = Route.useParams()

  return <RoleEditPage roleId={roleId} />
}
