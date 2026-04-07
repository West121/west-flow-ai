import { StyleSheet, Text, View } from 'react-native'

type StatusBadgeProps = {
  label: string
  tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger'
}

const toneStyles = {
  neutral: { backgroundColor: 'rgba(255,255,255,0.58)', color: '#666276' },
  info: { backgroundColor: 'rgba(210,231,255,0.58)', color: '#2E6AC5' },
  success: { backgroundColor: 'rgba(215,246,229,0.62)', color: '#1E8054' },
  warning: { backgroundColor: 'rgba(255,236,198,0.7)', color: '#9A6200' },
  danger: { backgroundColor: 'rgba(255,220,226,0.72)', color: '#AF4058' },
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
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.66)',
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  label: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.3,
    textTransform: 'uppercase',
  },
})
