import type { PropsWithChildren } from 'react'
import { StyleSheet, Text, View, type ViewProps } from 'react-native'

type SectionCardProps = PropsWithChildren<ViewProps & {
  title?: string
  description?: string
  compact?: boolean
}>

export function SectionCard({
  title,
  description,
  compact = false,
  children,
  style,
  ...viewProps
}: SectionCardProps) {
  return (
    <View {...viewProps} style={[styles.card, compact && styles.compactCard, style]}>
      {title ? <Text style={styles.title}>{title}</Text> : null}
      {description ? <Text style={styles.description}>{description}</Text> : null}
      <View style={styles.body}>{children}</View>
    </View>
  )
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 24,
    borderWidth: 1,
    borderColor: '#E5DDD1',
    backgroundColor: '#FFFCF7',
    padding: 18,
    shadowColor: '#3D2E21',
    shadowOpacity: 0.06,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 8 },
    elevation: 1,
  },
  compactCard: {
    padding: 14,
  },
  title: {
    color: '#171312',
    fontSize: 18,
    fontWeight: '700',
  },
  description: {
    color: '#75695E',
    marginTop: 6,
    lineHeight: 20,
  },
  body: {
    marginTop: 14,
    gap: 12,
  },
})
