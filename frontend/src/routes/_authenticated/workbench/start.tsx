import { createFileRoute } from '@tanstack/react-router'
import { WorkbenchStartPage } from '@/features/workbench/pages'

// 业务入口路由只负责挂载工作台发起页。
export const Route = createFileRoute('/_authenticated/workbench/start')({
  component: WorkbenchStartPage,
})
