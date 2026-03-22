import { createFileRoute } from '@tanstack/react-router'
import { TriggerCreatePage } from '@/features/system/trigger-pages'
import { ensureProcessAdminRouteAccess } from '@/features/system/process-admin-guard'

export const Route = createFileRoute('/_authenticated/system/triggers/create')({
  beforeLoad: ensureProcessAdminRouteAccess,
  component: TriggerCreatePage,
})
