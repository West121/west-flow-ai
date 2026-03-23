import { Link } from '@tanstack/react-router'
import { Bot, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/auth-store'

// 页面内嵌 Copilot 入口，始终把当前页面路由透传为上下文来源。
export function ContextualCopilotEntry({
  sourceRoute,
  label = '用 AI 解读当前页面',
  variant = 'outline',
}: {
  sourceRoute: string
  label?: string
  variant?: 'default' | 'outline'
}) {
  const aiCapabilities = useAuthStore(
    (state) => state.currentUser?.aiCapabilities ?? []
  )

  if (!aiCapabilities.includes('ai:copilot:open')) {
    return null
  }

  return (
    <Button asChild variant={variant}>
      <Link
        to='/ai'
        search={{
          sourceRoute,
        }}
      >
        {variant === 'default' ? <Bot /> : <Sparkles />}
        {label}
      </Link>
    </Button>
  )
}
