import { z } from 'zod'
import { createFileRoute } from '@tanstack/react-router'
import { SignIn } from '@/features/auth/sign-in'

const searchSchema = z.object({
  redirect: z.string().optional(),
})

// 登录页路由，兼容登录后回跳地址。
export const Route = createFileRoute('/(auth)/sign-in')({
  component: SignIn,
  validateSearch: searchSchema,
})
