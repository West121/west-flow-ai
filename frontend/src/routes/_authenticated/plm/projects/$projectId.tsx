import { createFileRoute } from '@tanstack/react-router'
import { PLMProjectDetailPage } from '@/features/plm/project-pages'

export const Route = createFileRoute('/_authenticated/plm/projects/$projectId')(
  {
    component: function PLMProjectDetailRoute() {
      const { projectId } = Route.useParams()

      return <PLMProjectDetailPage projectId={projectId} />
    },
  }
)
