import { createFileRoute } from '@tanstack/react-router'
import { WorkflowVersionDetailPage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/versions/$processDefinitionId/')({
  component: VersionDetailRoute,
})

function VersionDetailRoute() {
  const { processDefinitionId } = Route.useParams()
  return <WorkflowVersionDetailPage processDefinitionId={processDefinitionId} />
}
