import Taro from '@tarojs/taro'
import { Text, View } from '@tarojs/components'
import { colors } from '../styles/theme'

const tabs = [
  { pagePath: '/pages/workbench/index', label: '工作台', icon: '◉' },
  { pagePath: '/pages/ai/index', label: 'AI', icon: '✦' },
  { pagePath: '/pages/me/index', label: '我的', icon: '◎' },
] as const

export default function CustomTabBar() {
  const pages = Taro.getCurrentPages()
  const route = pages[pages.length - 1]?.route
  const currentRoute = route ? `/${route}` : '/pages/workbench/index'

  return (
    <View
      style={{
        position: 'fixed',
        left: '20px',
        right: '20px',
        bottom: '22px',
        zIndex: 999,
        pointerEvents: 'auto',
      }}
    >
      <View
        style={{
          position: 'relative',
          display: 'flex',
          flexDirection: 'row',
          gap: '8px',
          padding: '8px',
          borderRadius: '999px',
          background: colors.tabGlass,
          border: `1px solid ${colors.tabGlassBorder}`,
          backdropFilter: 'blur(18px)',
          boxShadow: '0 18px 42px rgba(91, 108, 138, 0.18)',
          overflow: 'hidden',
        }}
      >
        <View
          style={{
            position: 'absolute',
            inset: '0',
            background:
              'linear-gradient(180deg, rgba(255,255,255,0.34) 0%, rgba(255,255,255,0.04) 100%)',
          }}
        />
        {tabs.map((tab) => {
          const active = currentRoute === tab.pagePath
          return (
            <View
              key={tab.pagePath}
              onClick={() => {
                if (active) return
                void Taro.switchTab({ url: tab.pagePath })
              }}
              style={{
                position: 'relative',
                flex: 1,
                height: '56px',
                borderRadius: '999px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                overflow: 'hidden',
                background: active ? colors.tabActive : 'transparent',
                boxShadow: active ? '0 10px 24px rgba(137, 156, 196, 0.18)' : 'none',
                transition: 'all 220ms cubic-bezier(0.22, 1, 0.36, 1)',
              }}
            >
              {active ? (
                <>
                  <View
                    style={{
                      position: 'absolute',
                      width: '72px',
                      height: '72px',
                      left: '10px',
                      top: '-22px',
                      borderRadius: '999px',
                      background: colors.liquidA,
                      filter: 'blur(18px)',
                      opacity: 0.72,
                    }}
                  />
                  <View
                    style={{
                      position: 'absolute',
                      width: '64px',
                      height: '64px',
                      right: '4px',
                      bottom: '-20px',
                      borderRadius: '999px',
                      background: colors.liquidB,
                      filter: 'blur(14px)',
                      opacity: 0.88,
                    }}
                  />
                </>
              ) : null}
              <View
                style={{
                  position: 'relative',
                  display: 'flex',
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: '8px',
                }}
              >
                <Text
                  style={{
                    color: active ? colors.text : colors.textMuted,
                    fontSize: '14px',
                    lineHeight: 1,
                    fontWeight: 700,
                  }}
                >
                  {tab.icon}
                </Text>
                <Text
                  style={{
                    color: active ? colors.text : colors.textSecondary,
                    fontSize: '14px',
                    fontWeight: active ? 700 : 600,
                  }}
                >
                  {tab.label}
                </Text>
              </View>
            </View>
          )
        })}
      </View>
    </View>
  )
}
