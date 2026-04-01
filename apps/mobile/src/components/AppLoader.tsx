import { ActivityIndicator, StyleSheet, Text, View } from 'react-native'

export function AppLoader({ message = '正在加载…' }: { message?: string }) {
  return (
    <View style={styles.container}>
      <ActivityIndicator color="#171312" />
      <Text style={styles.message}>{message}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  message: {
    color: '#5C524A',
    fontSize: 14,
  },
})
