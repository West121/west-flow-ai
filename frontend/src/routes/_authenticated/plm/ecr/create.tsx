import { createFileRoute } from '@tanstack/react-router'
import { PLMECRCreatePage } from '@/features/plm/pages'

// ECR 发起路由只负责挂载变更申请页。
export const Route = createFileRoute('/_authenticated/plm/ecr/create')({
  component: PLMECRCreatePage,
})
