import { createFileRoute } from '@tanstack/react-router'
import { PLMStartPage } from '@/features/plm/pages'

// PLM 发起中心路由只负责挂载发起入口页。
export const Route = createFileRoute('/_authenticated/plm/start')({
  component: PLMStartPage,
})
