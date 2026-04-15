import { createFileRoute } from '@tanstack/react-router'
import { PLMProjectCreatePage } from '@/features/plm/project-pages'

export const Route = createFileRoute('/_authenticated/plm/projects/create')({
  component: PLMProjectCreatePage,
})
