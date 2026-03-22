import { createFileRoute } from '@tanstack/react-router'
import { TriggerCreatePage } from '@/features/system/trigger-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

// 触发器新建路由只负责权限校验和挂载表单页。
export const Route = createFileRoute('/_authenticated/system/triggers/create')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: TriggerCreatePage,
})
