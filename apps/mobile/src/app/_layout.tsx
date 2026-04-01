import { Stack } from 'expo-router'
import { AppProviders } from '@/providers/AppProviders'

export default function RootLayout() {
  return (
    <AppProviders>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="sign-in" options={{ headerShown: false }} />
        <Stack.Screen name="approval" options={{ headerShown: true, title: '审批单' }} />
        <Stack.Screen name="process-player" options={{ headerShown: true, title: '流程播放器' }} />
      </Stack>
    </AppProviders>
  )
}
