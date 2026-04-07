import { useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Input, Text, View } from '@tarojs/components'
import { PageShell, cardStyle } from '../../components/PageShell'
import { colors } from '../../styles/theme'
import { useAuthStore } from '../../stores/auth-store'
import { getApiErrorMessage } from '../../lib/api/client'

export default function SignInPage() {
  const hydrated = useAuthStore((state) => state.hydrated)
  const accessToken = useAuthStore((state) => state.accessToken)
  const signIn = useAuthStore((state) => state.signIn)
  const isSigningIn = useAuthStore((state) => state.isSigningIn)
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (hydrated && accessToken) {
      void Taro.switchTab({ url: '/pages/workbench/index' })
    }
  }, [accessToken, hydrated])

  return (
    <PageShell
      title="登录"
      description="用企业账号继续你的审批、AI 与流程回顾体验。"
      scroll={false}
    >
      <View style={cardStyle({ gap: '16px' })}>
        <View style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <Text style={{ fontSize: '28px', fontWeight: 800, color: colors.text }}>企业统一登录</Text>
          <Text style={{ fontSize: '13px', lineHeight: '20px', color: colors.textSecondary }}>
            在手机里查看待办、审批详情、AI Copilot 与流程回顾。
          </Text>
        </View>

        <View style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <Field label="账号" value={username} onInput={(value) => setUsername(value)} />
          <Field label="密码" value={password} password onInput={(value) => setPassword(value)} />
        </View>

        {errorMessage ? (
          <Text style={{ fontSize: '13px', color: colors.danger, lineHeight: '20px' }}>{errorMessage}</Text>
        ) : null}

        <View
          onClick={async () => {
            setErrorMessage(null)
            try {
              await signIn(username.trim(), password)
              await Taro.switchTab({ url: '/pages/workbench/index' })
            } catch (error) {
              setErrorMessage(getApiErrorMessage(error, '登录失败，请稍后重试。'))
            }
          }}
          style={{
            background: colors.primary,
            color: '#FFFFFF',
            textAlign: 'center',
            borderRadius: '20px',
            padding: '16px 18px',
          }}
        >
          <Text style={{ color: '#FFFFFF', fontSize: '16px', fontWeight: 700 }}>
            {isSigningIn ? '进入中…' : '进入工作台'}
          </Text>
        </View>
      </View>
      <Text style={{ fontSize: '12px', color: colors.textMuted, lineHeight: '18px' }}>
        登录即表示你同意移动端继续使用企业账号与平台安全策略。
      </Text>
    </PageShell>
  )
}

function Field({
  label,
  value,
  password = false,
  onInput,
}: {
  label: string
  value: string
  password?: boolean
  onInput: (value: string) => void
}) {
  return (
    <View style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <Text style={{ fontSize: '13px', fontWeight: 600, color: colors.textSecondary }}>{label}</Text>
      <View
        style={{
          background: colors.cardStrong,
          border: `1px solid ${colors.cardBorder}`,
          borderRadius: '18px',
          padding: '4px 16px',
        }}
      >
        <Input
          type="text"
          password={password}
          value={value}
          onInput={(event) => onInput(event.detail.value)}
          placeholder={`请输入${label}`}
          style={{ height: '44px', fontSize: '16px', color: colors.text }}
        />
      </View>
    </View>
  )
}
