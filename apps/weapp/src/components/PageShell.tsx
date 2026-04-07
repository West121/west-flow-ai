import { CSSProperties, PropsWithChildren, ReactNode } from 'react'
import { ScrollView, Text, View } from '@tarojs/components'
import { colors, glassShadow } from '../styles/theme'

type PageShellProps = PropsWithChildren<{
  title: string
  description?: string
  scroll?: boolean
  headerRight?: ReactNode
}>

const rootStyle: CSSProperties = {
  minHeight: '100vh',
  background: `linear-gradient(180deg, ${colors.bg} 0%, ${colors.bgSoft} 100%)`,
  padding: '32px 24px 120px',
  boxSizing: 'border-box',
}

const orbBase: CSSProperties = {
  position: 'absolute',
  borderRadius: '999px',
  filter: 'blur(2px)',
  opacity: 0.85,
}

export function PageShell({
  title,
  description,
  scroll = true,
  headerRight,
  children,
}: PageShellProps) {
  const content = (
    <View style={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <View style={{ display: 'flex', flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: '16px' }}>
        <View style={{ display: 'flex', flexDirection: 'column', gap: '8px', flex: 1 }}>
          <Text style={{ fontSize: '42px', fontWeight: 800, color: colors.text, lineHeight: 1.05 }}>
            {title}
          </Text>
          {description ? (
            <Text style={{ fontSize: '15px', color: colors.textSecondary, lineHeight: '22px' }}>
              {description}
            </Text>
          ) : null}
        </View>
        {headerRight ? <View>{headerRight}</View> : null}
      </View>
      {children}
    </View>
  )

  return (
    <View style={{ ...rootStyle, position: 'relative', overflow: 'hidden' }}>
      <View
        style={{
          ...orbBase,
          top: '-48px',
          right: '-34px',
          width: '232px',
          height: '232px',
          background: 'rgba(255,255,255,0.72)',
        }}
      />
      <View
        style={{
          ...orbBase,
          top: '180px',
          left: '-104px',
          width: '228px',
          height: '228px',
          background: 'rgba(217,228,255,0.28)',
        }}
      />
      <View
        style={{
          ...orbBase,
          bottom: '80px',
          right: '-64px',
          width: '200px',
          height: '200px',
          background: 'rgba(238,243,248,0.8)',
        }}
      />
      {scroll ? (
        <ScrollView scrollY style={{ height: '100%' }}>
          {content}
        </ScrollView>
      ) : (
        content
      )}
    </View>
  )
}

export function cardStyle(extra?: CSSProperties): CSSProperties {
  return {
    background: colors.card,
    border: `1px solid ${colors.cardBorder}`,
    borderRadius: '28px',
    padding: '18px',
    boxSizing: 'border-box',
    backdropFilter: 'blur(14px)',
    boxShadow: glassShadow.boxShadow,
    ...extra,
  }
}
