import { create } from 'zustand'

type AICopilotShellState = {
  open: boolean
  sourceRoute: string
  openCopilot: (sourceRoute?: string) => void
  closeCopilot: () => void
}

export const useAICopilotShellStore = create<AICopilotShellState>()((set) => ({
  open: false,
  sourceRoute: '',
  openCopilot: (sourceRoute = '') =>
    set({
      open: true,
      sourceRoute,
    }),
  closeCopilot: () =>
    set({
      open: false,
    }),
}))
