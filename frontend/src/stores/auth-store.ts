import { create } from 'zustand'
import {
  AUTH_ACCESS_TOKEN_COOKIE,
  type CurrentUser,
} from '@/features/shared/auth/types'
import { getCookie, removeCookie, setCookie } from '@/lib/cookies'

export { AUTH_ACCESS_TOKEN_COOKIE }

// 统一管理登录令牌和当前用户信息，并同步到 Cookie。
type AuthState = {
  accessToken: string
  currentUser: CurrentUser | null
  setCurrentUser: (currentUser: CurrentUser | null) => void
  setAccessToken: (accessToken: string) => void
  reset: () => void
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: getCookie(AUTH_ACCESS_TOKEN_COOKIE) ?? '',
  currentUser: null,
  setCurrentUser: (currentUser) => set({ currentUser }),
  setAccessToken: (accessToken) => {
    if (accessToken) {
      setCookie(AUTH_ACCESS_TOKEN_COOKIE, accessToken)
    } else {
      removeCookie(AUTH_ACCESS_TOKEN_COOKIE)
    }

    set({ accessToken })
  },
  reset: () => {
    removeCookie(AUTH_ACCESS_TOKEN_COOKIE)
    set({
      accessToken: '',
      currentUser: null,
    })
  },
}))
