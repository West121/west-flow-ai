import Taro, { useRouter } from '@tarojs/taro'
import { Text, View, WebView } from '@tarojs/components'
import { GlassCard } from '../../components/GlassCard'
import { PageShell } from '../../components/PageShell'
import { buildProcessPlayerUrl } from '../../features/process-player/url'
import { colors } from '../../styles/theme'

export default function ProcessPlayerPage() {
  const router = useRouter<{ ticket?: string }>()
  const ticket = router.params.ticket

  if (!ticket) {
    return (
      <PageShell title="流程回顾" description="缺少任务编号，无法打开回顾播放器。">
        <GlassCard title="参数缺失">
          <Text style={{ color: colors.textSecondary }}>请从审批详情页重新进入流程回顾。</Text>
        </GlassCard>
      </PageShell>
    )
  }

  const src = buildProcessPlayerUrl(ticket)

  return (
    <View style={{ minHeight: '100vh', background: colors.bg, display: 'flex', flexDirection: 'column' }}>
      <View style={{ padding: '24px 20px 14px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <Text style={{ fontSize: '34px', fontWeight: 800, color: colors.text }}>流程回顾</Text>
        <Text style={{ fontSize: '14px', lineHeight: '22px', color: colors.textSecondary }}>
          通过 web-view 打开只读播放器，保留节点高亮、时间轴与动画回放。
        </Text>
      </View>
      <WebView src={src} style={{ flex: 1 }} />
    </View>
  )
}
