import { createFileRoute } from '@tanstack/react-router'
import { SystemLoginLogDetailPage } from '@/features/system/log-pages'

// 登录日志详情路由，仅负责挂载对应的页面组件。
export const Route = createFileRoute('/_authenticated/system/logs/login/$logId/')({
  component: SystemLoginLogDetailPage,
})
