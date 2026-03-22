import { createFileRoute } from '@tanstack/react-router'
import { OACommonCreatePage } from '@/features/oa/pages'

// 通用申请发起路由只负责挂载通用表单页。
export const Route = createFileRoute('/_authenticated/oa/common/create')({
  component: OACommonCreatePage,
})
