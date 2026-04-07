import { Platform } from 'react-native'

export default function ProcessPlayerPage() {
  if (Platform.OS === 'web') {
    const { ProcessPlayerScreen } = require('@/features/process-player/ProcessPlayerScreen.web')
    return <ProcessPlayerScreen />
  }

  const { ProcessPlayerScreen } = require('@/features/process-player/ProcessPlayerScreen')
  return <ProcessPlayerScreen />
}
