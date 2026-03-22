import { createFileRoute } from '@tanstack/react-router'
import { WorkbenchTodoDetailPage } from '@/features/workbench/pages'

// 待办详情路由只负责把任务参数传给详情页。
export const Route = createFileRoute('/_authenticated/workbench/todos/$taskId')({
  component: function WorkbenchTodoDetailRoute() {
    const { taskId } = Route.useParams()

    return <WorkbenchTodoDetailPage taskId={taskId} />
  },
})
