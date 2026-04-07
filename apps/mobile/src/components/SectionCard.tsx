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
    borderRadius: 30,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.66)',
    backgroundColor: 'rgba(255,255,255,0.58)',
    padding: 18,
    shadowColor: '#6470A9',
    shadowOpacity: 0.12,
    shadowRadius: 22,
    shadowOffset: { width: 0, height: 14 },
    elevation: 4,
  },
  compactCard: {
    padding: 14,
  },
  title: {
    color: '#1B2132',
    fontSize: 18,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  description: {
    color: '#777287',
    marginTop: 8,
    lineHeight: 20,
  },
  body: {
    marginTop: 16,
    gap: 12,
  },
})
