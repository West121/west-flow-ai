import { Sheet, SheetContent, SheetDescription, SheetTitle } from '@/components/ui/sheet'
import { AICopilotWorkspace } from '@/features/ai'
import { useAICopilotShellStore } from '@/stores/ai-copilot-shell-store'

export function AICopilotDrawer() {
  const open = useAICopilotShellStore((state) => state.open)
  const sourceRoute = useAICopilotShellStore((state) => state.sourceRoute)
  const closeCopilot = useAICopilotShellStore((state) => state.closeCopilot)

  return (
    <Sheet open={open} onOpenChange={(nextOpen) => !nextOpen && closeCopilot()}>
      <SheetContent
        side='right'
        className='!w-screen !max-w-none gap-0 overflow-hidden border-white/10 bg-slate-950 p-0 text-slate-50 sm:!w-[min(100vw,1120px)] sm:!max-w-[1120px]'
      >
        <SheetTitle className='sr-only'>AI Copilot</SheetTitle>
        <SheetDescription className='sr-only'>
          在右侧抽屉中查看会话、消息、确认卡和工具调用。
        </SheetDescription>
        <div className='flex h-full min-h-0 flex-col overflow-hidden'>
          <AICopilotWorkspace sourceRoute={sourceRoute} mode='drawer' />
        </div>
      </SheetContent>
    </Sheet>
  )
}
