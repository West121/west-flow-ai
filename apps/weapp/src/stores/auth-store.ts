import Taro from '@tarojs/taro'
import { create } from 'zustand'
import { CurrentUser } from '../types/auth'
import { getCurrentUser, login } from '../lib/api/auth'

const TOKEN_KEY = 'westflow_weapp_access_token'
const USER_KEY = 'westflow_weapp_current_user'

function readPersistedAuthSnapshot() {
  const accessToken = Taro.getStorageSync<string>(TOKEN_KEY) || null
  const currentUserRaw = Taro.getStorageSync<string>(USER_KEY) || null
  console.log('[weapp/auth] readPersistedAuthSnapshot', {
    hasToken: Boolean(accessToken),
    hasCurrentUser: Boolean(currentUserRaw),
  })
  return {
    accessToken,
    currentUser: currentUserRaw ? (JSON.parse(currentUserRaw) as CurrentUser) : null,
  }
}

const persistedAuthSnapshot = readPersistedAuthSnapshot()

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
  hydrated: true,
  accessToken: persistedAuthSnapshot.accessToken,
  currentUser: persistedAuthSnapshot.currentUser,
  isSigningIn: false,
  hydrate: async () => {
    const { accessToken, currentUser } = readPersistedAuthSnapshot()
    console.log('[weapp/auth] hydrate:start', {
      hasToken: Boolean(accessToken),
      hasCurrentUser: Boolean(currentUser),
    })
    set({
      accessToken,
      currentUser,
      hydrated: true,
    })

    if (accessToken && !currentUser) {
      try {
        await get().refreshCurrentUser()
      } catch {
        console.log('[weapp/auth] hydrate:refreshCurrentUser failed, signing out')
        await get().signOut()
      }
    }
  },
  signIn: async (username, password) => {
    console.log('[weapp/auth] signIn:start', { username })
    set({ isSigningIn: true })
    try {
      const auth = await login({ username, password })
      Taro.setStorageSync(TOKEN_KEY, auth.accessToken)
      set({ accessToken: auth.accessToken, hydrated: true })
      const currentUser = await getCurrentUser()
      Taro.setStorageSync(USER_KEY, JSON.stringify(currentUser))
      console.log('[weapp/auth] signIn:success', {
        username,
        hasToken: Boolean(auth.accessToken),
        currentUserId: currentUser.userId,
      })
      set({ currentUser, hydrated: true, isSigningIn: false })
    } catch (error) {
      console.error('[weapp/auth] signIn:error', error)
      set({ isSigningIn: false })
      throw error
    }
  },
  signOut: async () => {
    console.log('[weapp/auth] signOut')
    Taro.removeStorageSync(TOKEN_KEY)
    Taro.removeStorageSync(USER_KEY)
    set({
      hydrated: true,
      accessToken: null,
      currentUser: null,
      isSigningIn: false,
    })
  },
  refreshCurrentUser: async () => {
    console.log('[weapp/auth] refreshCurrentUser:start')
    const currentUser = await getCurrentUser()
    Taro.setStorageSync(USER_KEY, JSON.stringify(currentUser))
    console.log('[weapp/auth] refreshCurrentUser:success', {
      currentUserId: currentUser.userId,
    })
    set({ currentUser, hydrated: true })
  },
}))
