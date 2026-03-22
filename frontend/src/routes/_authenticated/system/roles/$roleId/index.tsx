/* eslint-disable react-refresh/only-export-components */
import { createFileRoute } from '@tanstack/react-router'
import { RoleDetailPageEntry } from '@/features/system/role-pages'

export const Route = createFileRoute('/_authenticated/system/roles/$roleId/')({
  component: RoleDetailRoute,
})

function RoleDetailRoute() {
  const { roleId } = Route.useParams()

  return <RoleDetailPageEntry roleId={roleId} />
}
