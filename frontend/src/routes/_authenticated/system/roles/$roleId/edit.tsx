/* eslint-disable react-refresh/only-export-components */
import { createFileRoute } from '@tanstack/react-router'
import { RoleEditPage } from '@/features/system/role-pages'

export const Route = createFileRoute(
  '/_authenticated/system/roles/$roleId/edit'
)({
  component: RoleEditRoute,
})

function RoleEditRoute() {
  const { roleId } = Route.useParams()

  return <RoleEditPage roleId={roleId} />
}
