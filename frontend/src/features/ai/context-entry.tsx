import { Bot, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/auth-store'
import { useAICopilotShellStore } from '@/stores/ai-copilot-shell-store'

const EMPTY_AI_CAPABILITIES: string[] = []

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
    (state) => state.currentUser?.aiCapabilities ?? EMPTY_AI_CAPABILITIES
  )
  const openCopilot = useAICopilotShellStore((state) => state.openCopilot)

  if (!aiCapabilities.includes('ai:copilot:open')) {
    return null
  }

  return (
    <Button
      type='button'
      variant={variant}
      onClick={() => openCopilot(sourceRoute)}
      data-source-route={sourceRoute}
    >
      {variant === 'default' ? <Bot /> : <Sparkles />}
      {label}
    </Button>
  )
}
