import { createFileRoute } from '@tanstack/react-router'
import { WorkflowInstanceDetailPage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/instances/$instanceId/')({
  component: InstanceDetailRoute,
})

function InstanceDetailRoute() {
  const { instanceId } = Route.useParams()
  return <WorkflowInstanceDetailPage instanceId={instanceId} />
}
