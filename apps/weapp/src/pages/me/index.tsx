import Taro from '@tarojs/taro'
import { Text, View } from '@tarojs/components'
import { GlassCard } from '../../components/GlassCard'
import { PageShell, cardStyle } from '../../components/PageShell'
import { useAuthGuard } from '../../hooks/use-auth-guard'
import { useAuthStore } from '../../stores/auth-store'
import { colors } from '../../styles/theme'

export default function MePage() {
  const ready = useAuthGuard()
  const currentUser = useAuthStore((state) => state.currentUser)
  const signOut = useAuthStore((state) => state.signOut)

  if (!ready || !currentUser) {
    return null
  }

  return (
    <PageShell title="我的" description="把账号、入口和偏好设置收进这一页，减少跳转。">
      <GlassCard>
        <View style={{ display: 'flex', flexDirection: 'row', gap: '14px', alignItems: 'center' }}>
          <View
            style={{
              width: '64px',
              height: '64px',
              borderRadius: '32px',
              background: 'linear-gradient(180deg, rgba(32,40,60,0.92), rgba(69,84,124,0.88))',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text style={{ color: '#FFFFFF', fontSize: '20px', fontWeight: 800 }}>
              {currentUser.displayName.slice(0, 1)}
            </Text>
          </View>
          <View style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <Text style={{ color: colors.text, fontSize: '22px', fontWeight: 800 }}>{currentUser.displayName}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: '13px', lineHeight: '20px' }}>
              {currentUser.username} · {currentUser.activeDepartmentName} · {currentUser.activePostName}
            </Text>
          </View>
        </View>
        <Text style={{ color: colors.textSecondary, fontSize: '14px', lineHeight: '22px' }}>
          今天还有 2 条审批待确认，AI 助手和流程回顾可以随时进入。
        </Text>
      </GlassCard>

      <GlassCard title="常用入口" description="把最常用的入口放在首页下方，不需要进多层菜单。">
        <OptionRow title="我的待办" detail="直接回到工作台待办视图" />
        <OptionRow title="AI Copilot" detail="继续问答、图片识别与发起申请" />
        <OptionRow title="系统设置" detail="预留给通知、外观和权限说明" />
      </GlassCard>

      <View
        onClick={async () => {
          await signOut()
          await Taro.reLaunch({ url: '/pages/sign-in/index' })
        }}
        style={{
          ...cardStyle({
            padding: '16px 18px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: 'rgba(255,255,255,0.82)',
          }),
        }}
      >
        <Text style={{ color: colors.danger, fontSize: '15px', fontWeight: 700 }}>退出登录</Text>
      </View>
    </PageShell>
  )
}

function OptionRow({ title, detail }: { title: string; detail: string }) {
  return (
    <View style={{ ...cardStyle({ borderRadius: '20px', padding: '14px' }) }}>
      <Text style={{ color: colors.text, fontSize: '15px', fontWeight: 700 }}>{title}</Text>
      <Text style={{ color: colors.textSecondary, fontSize: '13px', lineHeight: '20px', marginTop: '6px' }}>
        {detail}
      </Text>
    </View>
  )
}
