import { createFileRoute } from '@tanstack/react-router'
import { z } from 'zod'
import { OALeaveCreatePage } from '@/features/oa/pages'

// 请假发起路由只负责挂载请假表单页。
export const Route = createFileRoute('/_authenticated/oa/leave/create')({
  validateSearch: z.object({
    draftId: z.string().optional(),
  }),
  component: OALeaveCreatePage,
})
