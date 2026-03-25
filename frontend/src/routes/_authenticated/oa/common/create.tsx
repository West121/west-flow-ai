import { createFileRoute } from '@tanstack/react-router'
import { z } from 'zod'
import { OACommonCreatePage } from '@/features/oa/pages'

// 通用申请发起路由只负责挂载通用表单页。
export const Route = createFileRoute('/_authenticated/oa/common/create')({
  validateSearch: z.object({
    draftId: z.string().optional(),
  }),
  component: OACommonCreatePage,
})
