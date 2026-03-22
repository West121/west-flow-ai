import { createFileRoute } from '@tanstack/react-router'
import { WorkflowBindingDetailPage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/bindings/$bindingId/')({
  component: BindingDetailRoute,
})

function BindingDetailRoute() {
  const { bindingId } = Route.useParams()
  return <WorkflowBindingDetailPage bindingId={bindingId} />
}
