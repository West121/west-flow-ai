import { createFileRoute } from '@tanstack/react-router'
import { WorkbenchTodoDetailPage } from '@/features/workbench/pages'

export const Route = createFileRoute('/_authenticated/workbench/todos/$taskId')({
  component: function WorkbenchTodoDetailRoute() {
    const { taskId } = Route.useParams()

    return <WorkbenchTodoDetailPage taskId={taskId} />
  },
})
