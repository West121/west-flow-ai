import { create } from 'zustand'
import * as SecureStore from 'expo-secure-store'
import type { CurrentUser } from '@/types/auth'
import { getCurrentUser, login } from '@/lib/api/auth'

const TOKEN_KEY = 'westflow_mobile_access_token'
const USER_KEY = 'westflow_mobile_current_user'

type AuthState = {
  hydrated: boolean
  accessToken: string | null
  currentUser: CurrentUser | null
  isSigningIn: boolean
  hydrate: () => Promise<void>
  signIn: (username: string, password: string) => Promise<void>
  signOut: () => Promise<void>
  refreshCurrentUser: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  hydrated: false,
  accessToken: null,
  currentUser: null,
  isSigningIn: false,
  hydrate: async () => {
    const [accessToken, currentUserRaw] = await Promise.all([
      SecureStore.getItemAsync(TOKEN_KEY),
      SecureStore.getItemAsync(USER_KEY),
    ])

    set({
      accessToken,
      currentUser: currentUserRaw ? (JSON.parse(currentUserRaw) as CurrentUser) : null,
      hydrated: true,
    })

    if (accessToken && !get().currentUser) {
      try {
        await get().refreshCurrentUser()
      } catch {
        await get().signOut()
      }
    }
  },
  signIn: async (username, password) => {
    set({ isSigningIn: true })
    try {
      const auth = await login({ username, password })
      await SecureStore.setItemAsync(TOKEN_KEY, auth.accessToken)
      set({ accessToken: auth.accessToken })
      const currentUser = await getCurrentUser()
      await SecureStore.setItemAsync(USER_KEY, JSON.stringify(currentUser))
      set({ currentUser, isSigningIn: false })
    } catch (error) {
      set({ isSigningIn: false })
      throw error
    }
  },
  signOut: async () => {
    await Promise.all([
      SecureStore.deleteItemAsync(TOKEN_KEY),
      SecureStore.deleteItemAsync(USER_KEY),
    ])
    set({
      accessToken: null,
      currentUser: null,
      isSigningIn: false,
      hydrated: true,
    })
  },
  refreshCurrentUser: async () => {
    const currentUser = await getCurrentUser()
    await SecureStore.setItemAsync(USER_KEY, JSON.stringify(currentUser))
    set({ currentUser })
  },
}))
