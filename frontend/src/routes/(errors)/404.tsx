import { createFileRoute } from '@tanstack/react-router'
import { NotFoundError } from '@/features/errors/not-found-error'

// 404 错误页路由。
export const Route = createFileRoute('/(errors)/404')({
  component: NotFoundError,
})
