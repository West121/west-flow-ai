import { Text, View } from '@tarojs/components'
import { colors } from '../styles/theme'

type BadgeTone = 'info' | 'success' | 'warning' | 'danger' | 'neutral'

const toneMap: Record<BadgeTone, { bg: string; text: string }> = {
  info: { bg: 'rgba(108,132,182,0.14)', text: colors.info },
  success: { bg: 'rgba(113,136,122,0.14)', text: colors.success },
  warning: { bg: 'rgba(176,138,98,0.14)', text: colors.warning },
  danger: { bg: 'rgba(179,106,114,0.14)', text: colors.danger },
  neutral: { bg: 'rgba(17,24,39,0.08)', text: colors.textSecondary },
}

export function StatusBadge({
  label,
  tone = 'neutral',
}: {
  label: string
  tone?: BadgeTone
}) {
  const palette = toneMap[tone]
  return (
    <View
      style={{
        alignSelf: 'flex-start',
        padding: '6px 10px',
        borderRadius: '999px',
        background: palette.bg,
      }}
    >
      <Text style={{ color: palette.text, fontSize: '11px', fontWeight: 700 }}>{label}</Text>
    </View>
  )
}
