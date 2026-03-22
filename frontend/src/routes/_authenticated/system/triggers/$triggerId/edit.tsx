import { createFileRoute } from '@tanstack/react-router'
import { TriggerEditPage } from '@/features/system/trigger-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

export const Route = createFileRoute('/_authenticated/system/triggers/$triggerId/edit')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: TriggerEditRoute,
})

function TriggerEditRoute() {
  const { triggerId } = Route.useParams()

  return <TriggerEditPage triggerId={triggerId} />
}
