import { useEffect, type PropsWithChildren } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { StatusBar } from 'expo-status-bar'
import { GestureHandlerRootView } from 'react-native-gesture-handler'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import { queryClient } from '@/lib/query-client'
import { useAuthStore } from '@/stores/auth-store'

export function AppProviders({ children }: PropsWithChildren) {
  const hydrate = useAuthStore((state) => state.hydrate)

  useEffect(() => {
    void hydrate()
  }, [hydrate])

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <QueryClientProvider client={queryClient}>
          {children}
          <StatusBar style="dark" />
        </QueryClientProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  )
}
