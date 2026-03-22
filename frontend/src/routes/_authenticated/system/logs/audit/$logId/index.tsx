import { createFileRoute } from '@tanstack/react-router'
import { SystemAuditLogDetailPage } from '@/features/system/log-pages'

export const Route = createFileRoute('/_authenticated/system/logs/audit/$logId/')({
  component: SystemAuditLogDetailPage,
})
