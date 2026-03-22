import { createFileRoute } from '@tanstack/react-router'
import { OALeaveCreatePage } from '@/features/oa/pages'

export const Route = createFileRoute('/_authenticated/oa/leave/create')({
  component: OALeaveCreatePage,
})
