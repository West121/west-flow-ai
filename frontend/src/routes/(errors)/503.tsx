import { createFileRoute } from '@tanstack/react-router'
import { MaintenanceError } from '@/features/errors/maintenance-error'

// 503 维护页路由。
export const Route = createFileRoute('/(errors)/503')({
  component: MaintenanceError,
})
