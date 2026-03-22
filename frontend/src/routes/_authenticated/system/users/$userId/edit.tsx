import { createFileRoute } from '@tanstack/react-router'
import { UserEditPage } from '@/features/system/user-pages'

export const Route = createFileRoute(
  '/_authenticated/system/users/$userId/edit'
)({
  component: UserEditRoute,
})

function UserEditRoute() {
  const { userId } = Route.useParams()

  return <UserEditPage userId={userId} />
}
