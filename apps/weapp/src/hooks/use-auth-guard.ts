import { useEffect } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { useAuthStore } from '../stores/auth-store'

export function useAuthGuard() {
  const hydrated = useAuthStore((state) => state.hydrated)
  const accessToken = useAuthStore((state) => state.accessToken)
  const hydrate = useAuthStore((state) => state.hydrate)

  console.log('[weapp/auth-guard] render', {
    hydrated,
    hasAccessToken: Boolean(accessToken),
  })

  useDidShow(() => {
    console.log('[weapp/auth-guard] didShow', {
      hydrated,
      hasAccessToken: Boolean(accessToken),
    })
    if (!hydrated) {
      void hydrate()
      return
    }
    if (!accessToken) {
      void Taro.redirectTo({ url: '/pages/sign-in/index' })
    }
  })

  useEffect(() => {
    console.log('[weapp/auth-guard] effect', {
      hydrated,
      hasAccessToken: Boolean(accessToken),
    })
    if (!hydrated) {
      void hydrate()
      return
    }

    if (!accessToken) {
      void Taro.redirectTo({ url: '/pages/sign-in/index' })
    }
  }, [accessToken, hydrate, hydrated])

  return hydrated && Boolean(accessToken)
}
