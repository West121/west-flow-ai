import { createFileRoute } from '@tanstack/react-router'
import { UserDetailPage } from '@/features/system/user-pages'

// 用户详情路由只负责把参数传给详情页。
export const Route = createFileRoute('/_authenticated/system/users/$userId/')({
  component: UserDetailRoute,
})

function UserDetailRoute() {
  const { userId } = Route.useParams()

  return <UserDetailPage userId={userId} />
}
