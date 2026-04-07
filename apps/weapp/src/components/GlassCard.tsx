import { CSSProperties, PropsWithChildren } from 'react'
import { Text, View } from '@tarojs/components'
import { colors } from '../styles/theme'
import { cardStyle } from './PageShell'

type GlassCardProps = PropsWithChildren<{
  title?: string
  description?: string
  style?: CSSProperties
}>

export function GlassCard({ title, description, style, children }: GlassCardProps) {
  return (
    <View style={cardStyle(style)}>
      {title ? (
        <Text style={{ display: 'block', color: colors.text, fontSize: '18px', fontWeight: 700 }}>
          {title}
        </Text>
      ) : null}
      {description ? (
        <Text
          style={{
            display: 'block',
            color: colors.textSecondary,
            fontSize: '13px',
            lineHeight: '20px',
            marginTop: title ? '8px' : '0',
          }}
        >
          {description}
        </Text>
      ) : null}
      <View style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: title || description ? '16px' : '0' }}>
        {children}
      </View>
    </View>
  )
}
