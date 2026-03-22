import { createFileRoute } from '@tanstack/react-router'
import { WorkflowOperationLogDetailPage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/operation-logs/$logId/')({
  component: OperationLogDetailRoute,
})

function OperationLogDetailRoute() {
  const { logId } = Route.useParams()
  return <WorkflowOperationLogDetailPage logId={logId} />
}
