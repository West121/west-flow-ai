import { PropsWithChildren } from 'react'
import { useEffect } from 'react'
import { useDidShow, useLaunch } from '@tarojs/taro'
import { useAuthStore } from '../stores/auth-store'

export function AppProviders({ children }: PropsWithChildren) {
  const hydrate = useAuthStore((state) => state.hydrate)

  useLaunch(() => {
    void hydrate()
  })

  useDidShow(() => {
    void hydrate()
  })

  useEffect(() => {
    void hydrate()
  }, [hydrate])

  return children
}
