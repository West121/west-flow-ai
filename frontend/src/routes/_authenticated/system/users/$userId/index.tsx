import { createFileRoute } from '@tanstack/react-router'
import { UserDetailPage } from '@/features/system/user-pages'

export const Route = createFileRoute('/_authenticated/system/users/$userId/')({
  component: UserDetailRoute,
})

function UserDetailRoute() {
  const { userId } = Route.useParams()

  return <UserDetailPage userId={userId} />
}
