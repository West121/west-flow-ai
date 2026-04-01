import { Redirect } from 'expo-router'
import { ActivityIndicator, View } from 'react-native'
import { useAuthStore } from '@/stores/auth-store'

export default function IndexPage() {
  const hydrated = useAuthStore((state) => state.hydrated)
  const accessToken = useAuthStore((state) => state.accessToken)

  if (!hydrated) {
    return (
      <View
        style={{
          flex: 1,
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#F6F4EF',
        }}
      >
        <ActivityIndicator color="#171312" />
      </View>
    )
  }

  return <Redirect href={accessToken ? '/workbench' : '/sign-in'} />
}
