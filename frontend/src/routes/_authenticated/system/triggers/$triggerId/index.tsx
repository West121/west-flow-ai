import { createFileRoute } from '@tanstack/react-router'
import { TriggerDetailPage } from '@/features/system/trigger-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

export const Route = createFileRoute('/_authenticated/system/triggers/$triggerId/')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: TriggerDetailRoute,
})

function TriggerDetailRoute() {
  const { triggerId } = Route.useParams()

  return <TriggerDetailPage triggerId={triggerId} />
}
