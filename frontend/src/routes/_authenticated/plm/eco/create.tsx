import { createFileRoute } from '@tanstack/react-router'
import { PLMECOCreatePage } from '@/features/plm/pages'

// ECO 发起路由只负责挂载变更执行页。
export const Route = createFileRoute('/_authenticated/plm/eco/create')({
  component: PLMECOCreatePage,
})
