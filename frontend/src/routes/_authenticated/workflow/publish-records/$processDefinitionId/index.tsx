import { createFileRoute } from '@tanstack/react-router'
import { WorkflowPublishRecordDetailPage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/publish-records/$processDefinitionId/')({
  component: PublishRecordDetailRoute,
})

function PublishRecordDetailRoute() {
  const { processDefinitionId } = Route.useParams()
  return <WorkflowPublishRecordDetailPage processDefinitionId={processDefinitionId} />
}
