import { createFileRoute, redirect } from '@tanstack/react-router'
import { useAuthStore } from '@/stores/auth-store'
import { getCurrentUser } from '@/lib/api/auth'
import { AuthenticatedLayout } from '@/components/layout/authenticated-layout'

// 进入已登录区域前，先确认 token 和当前用户信息可用。
async function ensureAuthenticatedUser(locationHref: string) {
  const { accessToken, currentUser, reset, setCurrentUser } =
    useAuthStore.getState()

  if (!accessToken) {
    reset()
    throw redirect({
      to: '/sign-in',
      search: { redirect: locationHref },
      replace: true,
    })
  }

  if (currentUser) {
    return
  }

  try {
    const nextCurrentUser = await getCurrentUser()
    setCurrentUser(nextCurrentUser)
  } catch {
    reset()
    throw redirect({
      to: '/sign-in',
      search: { redirect: locationHref },
      replace: true,
    })
  }
}

export const Route = createFileRoute('/_authenticated')({
  // 未登录或用户信息失效时统一回跳登录页。
  beforeLoad: ({ location }) => ensureAuthenticatedUser(location.href),
  component: AuthenticatedLayout,
})
