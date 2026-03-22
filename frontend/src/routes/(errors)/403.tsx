import { createFileRoute } from '@tanstack/react-router'
import { ForbiddenError } from '@/features/errors/forbidden'

// 403 错误页路由。
export const Route = createFileRoute('/(errors)/403')({
  component: ForbiddenError,
})
