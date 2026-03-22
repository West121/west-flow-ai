import { createFileRoute } from '@tanstack/react-router'
import { WorkflowBindingEditPage } from '@/features/workflow/management-pages'

export const Route = createFileRoute('/_authenticated/workflow/bindings/$bindingId/edit')({
  component: BindingEditRoute,
})

function BindingEditRoute() {
  const { bindingId } = Route.useParams()
  return <WorkflowBindingEditPage bindingId={bindingId} />
}
