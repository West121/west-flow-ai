import type { PropsWithChildren } from 'react'
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native'
import { Link, type Href } from 'expo-router'
import { SafeAreaView } from 'react-native-safe-area-context'

type ActionLink = {
  href: Href
  label: string
}

type ScreenShellProps = PropsWithChildren<{
  title: string
  description?: string
  actions?: ActionLink[]
  scrollable?: boolean
}>

function ScreenHeader({
  title,
  description,
  actions,
}: Omit<ScreenShellProps, 'children' | 'scrollable'>) {
  return (
    <>
      <View style={styles.headerAccent} />
      <Text style={styles.title}>{title}</Text>
      {description ? <Text style={styles.description}>{description}</Text> : null}

      {actions && actions.length > 0 ? (
        <View style={styles.actions}>
          {actions.map((action, index) => (
            <Link key={`${action.label}:${index}`} href={action.href} asChild>
              <Pressable style={styles.actionButton}>
                <Text style={styles.actionText}>{action.label}</Text>
              </Pressable>
            </Link>
          ))}
        </View>
      ) : null}
    </>
  )
}

export function ScreenShell({
  title,
  description,
  actions,
  children,
  scrollable = true,
}: ScreenShellProps) {
  const content = scrollable ? (
    <ScrollView
      style={styles.scrollView}
      contentContainerStyle={styles.scrollContent}
      showsVerticalScrollIndicator={false}
    >
      <ScreenHeader title={title} description={description} actions={actions} />
      <View style={styles.body}>{children}</View>
    </ScrollView>
  ) : (
    <View style={styles.container}>
      <ScreenHeader title={title} description={description} actions={actions} />
      <View style={styles.body}>{children}</View>
    </View>
  )

  return (
    <SafeAreaView style={styles.safeArea}>
      <View pointerEvents="none" style={styles.background}>
        <View style={[styles.glow, styles.glowPrimary]} />
        <View style={[styles.glow, styles.glowSecondary]} />
        <View style={[styles.glow, styles.glowTertiary]} />
      </View>
      {content}
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#F4F3F8',
  },
  background: {
    ...StyleSheet.absoluteFillObject,
    overflow: 'hidden',
  },
  glow: {
    position: 'absolute',
    borderRadius: 999,
    opacity: 0.9,
  },
  glowPrimary: {
    top: -90,
    right: -10,
    width: 260,
    height: 260,
    backgroundColor: 'rgba(255,255,255,0.72)',
  },
  glowSecondary: {
    top: 180,
    left: -80,
    width: 240,
    height: 240,
    backgroundColor: 'rgba(213,227,255,0.32)',
  },
  glowTertiary: {
    bottom: 120,
    right: -70,
    width: 220,
    height: 220,
    backgroundColor: 'rgba(255,223,233,0.24)',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: 120,
  },
  container: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 12,
  },
  headerAccent: {
    width: 42,
    height: 6,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.92)',
    marginBottom: 14,
  },
  title: {
    color: '#171A27',
    fontSize: 36,
    fontWeight: '800',
    letterSpacing: -1.1,
  },
  description: {
    color: '#6E6A7E',
    fontSize: 16,
    lineHeight: 24,
    marginTop: 8,
    maxWidth: 300,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    marginTop: 20,
  },
  actionButton: {
    backgroundColor: 'rgba(23,26,39,0.92)',
    borderRadius: 18,
    paddingHorizontal: 18,
    paddingVertical: 12,
    shadowColor: '#161A25',
    shadowOpacity: 0.18,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 10 },
  },
  actionText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  body: {
    flex: 1,
    marginTop: 24,
  },
})
