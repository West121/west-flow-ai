import { createFileRoute } from '@tanstack/react-router'
import { OAQueryPage } from '@/features/oa/pages'

export const Route = createFileRoute('/_authenticated/oa/query')({
  component: OAQueryPage,
})
