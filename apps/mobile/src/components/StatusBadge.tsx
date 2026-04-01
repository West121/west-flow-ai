import { StyleSheet, Text, View } from 'react-native'

type StatusBadgeProps = {
  label: string
  tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger'
}

const toneStyles = {
  neutral: { backgroundColor: '#F3EEE6', color: '#5F564E' },
  info: { backgroundColor: '#E8F3FF', color: '#2066C0' },
  success: { backgroundColor: '#E8F7EF', color: '#27825B' },
  warning: { backgroundColor: '#FFF3DD', color: '#AC6A00' },
  danger: { backgroundColor: '#FDEBEC', color: '#B53B46' },
} as const

export function StatusBadge({
  label,
  tone = 'neutral',
}: StatusBadgeProps) {
  const style = toneStyles[tone]
  return (
    <View style={[styles.badge, { backgroundColor: style.backgroundColor }]}>
      <Text style={[styles.label, { color: style.color }]}>{label}</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: 'flex-start',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  label: {
    fontSize: 12,
    fontWeight: '700',
  },
})
