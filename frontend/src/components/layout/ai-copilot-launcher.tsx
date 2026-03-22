import { Link, useRouterState } from '@tanstack/react-router'
import { Bot, Sparkles } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/auth-store'

// 全局唯一的 AI Copilot 入口按钮，固定在已登录区域右下角。
export function AICopilotLauncher() {
  const routerState = useRouterState()
  const aiCapabilities = useAuthStore(
    (state) => state.currentUser?.aiCapabilities ?? []
  )
  const isActive = routerState.location.pathname.startsWith('/ai')
  const canOpenCopilot = aiCapabilities.includes('ai:copilot:open')

  if (!canOpenCopilot) {
    return null
  }

  return (
    <Button
      asChild
      size='lg'
      className={cn(
        'fixed right-5 bottom-5 z-40 h-14 rounded-full border shadow-2xl backdrop-blur-xl transition-all duration-200',
        isActive
          ? 'border-cyan-200/50 bg-cyan-300 text-slate-950 hover:bg-cyan-200'
          : 'border-white/10 bg-slate-950/90 text-slate-50 hover:-translate-y-0.5 hover:bg-slate-900'
      )}
    >
      <Link to='/ai' search={{}}>
        <span className='flex items-center gap-2'>
          <span
            className={cn(
              'flex size-8 items-center justify-center rounded-full border',
              isActive
                ? 'border-slate-950/10 bg-slate-950/10'
                : 'border-white/10 bg-white/5'
            )}
          >
            {isActive ? <Bot className='size-4' /> : <Sparkles className='size-4' />}
          </span>
          <span className='flex flex-col items-start leading-tight'>
            <span className='text-sm font-semibold'>AI Copilot</span>
            <span
              className={cn(
                'text-[11px] tracking-[0.18em] uppercase',
                isActive ? 'text-slate-700' : 'text-slate-300'
              )}
            >
              Open Assistant
            </span>
          </span>
        </span>
      </Link>
    </Button>
  )
}
