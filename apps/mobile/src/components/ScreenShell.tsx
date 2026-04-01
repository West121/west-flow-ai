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
      <Text style={styles.kicker}>West Flow Mobile</Text>
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

  return <SafeAreaView style={styles.safeArea}>{content}</SafeAreaView>
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#F6F4EF',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: 32,
  },
  container: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 20,
  },
  kicker: {
    color: '#7B6F63',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
  },
  title: {
    color: '#171312',
    fontSize: 34,
    fontWeight: '800',
    marginTop: 8,
  },
  description: {
    color: '#5C524A',
    fontSize: 15,
    lineHeight: 22,
    marginTop: 12,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    marginTop: 20,
  },
  actionButton: {
    backgroundColor: '#171312',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  actionText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  body: {
    flex: 1,
    marginTop: 20,
  },
})
