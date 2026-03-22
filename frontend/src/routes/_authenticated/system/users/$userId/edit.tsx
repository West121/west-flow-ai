import { createFileRoute } from '@tanstack/react-router'
import { UserEditPage } from '@/features/system/user-pages'

// 用户编辑路由只负责把参数传给编辑页。
export const Route = createFileRoute(
  '/_authenticated/system/users/$userId/edit'
)({
  component: UserEditRoute,
})

function UserEditRoute() {
  const { userId } = Route.useParams()

  return <UserEditPage userId={userId} />
}
