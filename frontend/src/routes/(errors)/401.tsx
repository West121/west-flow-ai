import { createFileRoute } from '@tanstack/react-router'
import { UnauthorisedError } from '@/features/errors/unauthorized-error'

// 401 错误页路由。
export const Route = createFileRoute('/(errors)/401')({
  component: UnauthorisedError,
})
