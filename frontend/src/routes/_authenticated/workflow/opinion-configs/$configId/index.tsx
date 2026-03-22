import { createFileRoute } from '@tanstack/react-router'
import { ApprovalOpinionConfigDetailPage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/opinion-configs/$configId/')({
  component: OpinionConfigDetailRoute,
})

function OpinionConfigDetailRoute() {
  const { configId } = Route.useParams()
  return <ApprovalOpinionConfigDetailPage configId={configId} />
}
