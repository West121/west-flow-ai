import { createFileRoute } from '@tanstack/react-router'
import { GeneralError } from '@/features/errors/general-error'

// 500 错误页路由。
export const Route = createFileRoute('/(errors)/500')({
  component: GeneralError,
})
