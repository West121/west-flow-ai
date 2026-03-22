import { createFileRoute } from '@tanstack/react-router'
import { ApprovalOpinionConfigEditPage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/opinion-configs/$configId/edit')({
  component: OpinionConfigEditRoute,
})

function OpinionConfigEditRoute() {
  const { configId } = Route.useParams()
  return <ApprovalOpinionConfigEditPage configId={configId} />
}
