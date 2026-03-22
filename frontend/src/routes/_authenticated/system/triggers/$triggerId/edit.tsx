import { createFileRoute } from '@tanstack/react-router'
import { TriggerEditPage } from '@/features/system/trigger-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

// 触发器编辑路由只负责权限校验和参数传递。
export const Route = createFileRoute('/_authenticated/system/triggers/$triggerId/edit')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: TriggerEditRoute,
})

function TriggerEditRoute() {
  const { triggerId } = Route.useParams()

  return <TriggerEditPage triggerId={triggerId} />
}
