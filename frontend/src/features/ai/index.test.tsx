import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const aiCopilotApiMocks = vi.hoisted(() => ({
  listAICopilotSessions: vi.fn(),
  getAICopilotSession: vi.fn(),
  createAICopilotSession: vi.fn(),
  sendAICopilotMessage: vi.fn(),
  confirmAICopilotConfirmation: vi.fn(),
}))

vi.mock('@/lib/api/ai-copilot', () => aiCopilotApiMocks)

vi.mock('@/components/layout/main', () => ({
  Main: ({ children }: { children: React.ReactNode }) => <main>{children}</main>,
}))

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  )
}

function buildSession(overrides: Record<string, unknown> = {}) {
  const sessionId =
    typeof overrides.sessionId === 'string' ? overrides.sessionId : 'session_001'
  const title =
    typeof overrides.title === 'string' ? overrides.title : '待办分流建议'

  return {
    sessionId,
    title,
    preview:
      typeof overrides.preview === 'string'
        ? overrides.preview
        : '我已经梳理出 3 个可直接执行的确认动作。',
    status: 'active',
    updatedAt: '2026-03-23T08:00:00.000Z',
    messageCount: 3,
    contextTags:
      (overrides.contextTags as string[] | undefined) ?? ['工作台', '待办'],
    history: [
      {
        messageId: `${sessionId}_msg_001`,
        role: 'user',
        authorName: '你',
        createdAt: '2026-03-23T08:00:00.000Z',
        content: '请帮我看看这个待办。',
      },
      {
        messageId: `${sessionId}_msg_002`,
        role: 'assistant',
        authorName: 'AI Copilot',
        createdAt: '2026-03-23T08:00:10.000Z',
        content: '我先给出一个确认卡。',
        blocks: [
          {
            type: 'confirm',
            confirmationId: `${sessionId}_confirm_001`,
            title: '是否立即执行？',
            summary: '确认后将记录审计并继续后续流程。',
            confirmLabel: '确认处理',
            cancelLabel: '稍后再说',
            status:
              (overrides.confirmationStatus as string | undefined) ?? 'pending',
          },
        ],
      },
    ],
  }
}

describe('AICopilotPage', () => {
  beforeEach(() => {
    aiCopilotApiMocks.listAICopilotSessions.mockResolvedValue([
      {
        sessionId: 'session_001',
        title: '待办分流建议',
        preview: '我已经梳理出 3 个可直接执行的确认动作。',
        status: 'active',
        updatedAt: '2026-03-23T08:00:00.000Z',
        messageCount: 3,
        contextTags: ['工作台', '待办'],
      },
      {
        sessionId: 'session_002',
        title: '流程解释草稿',
        preview: '在这里持续收敛消息模板和展示策略。',
        status: 'archived',
        updatedAt: '2026-03-22T10:12:00.000Z',
        messageCount: 2,
        contextTags: ['历史', '归档'],
      },
    ])
    aiCopilotApiMocks.getAICopilotSession.mockImplementation(
      async (sessionId: string) =>
        sessionId === 'session_001'
          ? buildSession({ sessionId: 'session_001' })
          : buildSession({
              sessionId: 'session_002',
              title: '流程解释草稿',
              preview: '在这里持续收敛消息模板和展示策略。',
              contextTags: ['历史', '归档'],
            })
    )
    aiCopilotApiMocks.createAICopilotSession.mockResolvedValue(
      buildSession({
        sessionId: 'session_003',
        title: '新建 Copilot 会话',
        preview: '刚创建的会话可以立刻开始对话。',
        contextTags: ['AI Copilot'],
      })
    )
    aiCopilotApiMocks.sendAICopilotMessage.mockResolvedValue(
      buildSession({
        sessionId: 'session_001',
        title: '待办分流建议',
        preview: '请生成一个确认卡和统计卡',
        confirmationStatus: 'pending',
      })
    )
    aiCopilotApiMocks.confirmAICopilotConfirmation.mockResolvedValue(
      buildSession({
        sessionId: 'session_001',
        title: '待办分流建议',
        preview: '请生成一个确认卡和统计卡',
        confirmationStatus: 'confirmed',
      })
    )
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('loads session list and detail from the HTTP-backed API', async () => {
    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    expect(await screen.findByText('待办分流建议')).toBeInTheDocument()
    expect(screen.getByText('会话工位')).toBeInTheDocument()
    await waitFor(() =>
      expect(aiCopilotApiMocks.getAICopilotSession).toHaveBeenCalledWith(
        'session_001'
      )
    )

    fireEvent.click(screen.getByRole('button', { name: /流程解释草稿/ }))

    await waitFor(() =>
      expect(aiCopilotApiMocks.getAICopilotSession).toHaveBeenCalledWith(
        'session_002'
      )
    )
    expect(screen.getByText('流程解释草稿')).toBeInTheDocument()
  })

  it('sends a message and resolves a confirmation card state flow', async () => {
    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    await screen.findByText('待办分流建议')

    fireEvent.change(screen.getByPlaceholderText(/输入一条 Copilot 指令/), {
      target: { value: '请生成一个确认卡和统计卡' },
    })
    await waitFor(() =>
      expect(
        screen.getByDisplayValue('请生成一个确认卡和统计卡')
      ).toBeInTheDocument()
    )
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '发送' })).toBeEnabled()
    )
    fireEvent.click(screen.getByRole('button', { name: '发送' }))

    await waitFor(() =>
      expect(aiCopilotApiMocks.sendAICopilotMessage).toHaveBeenCalledWith(
        {
          sessionId: 'session_001',
          content: '请生成一个确认卡和统计卡',
        },
        expect.any(Object)
      )
    )

    const confirmButtons = await screen.findAllByRole('button', {
      name: '确认处理',
    })
    fireEvent.click(confirmButtons[0])

    await waitFor(() =>
      expect(
        aiCopilotApiMocks.confirmAICopilotConfirmation
      ).toHaveBeenCalledWith(
        {
          sessionId: 'session_001',
          confirmationId: 'session_001_confirm_001',
          decision: 'confirm',
        },
        expect.any(Object)
      )
    )

    await waitFor(() =>
      expect(screen.getAllByText('已确认').length).toBeGreaterThan(0)
    )
  })

  it('creates a contextual session when opened from a business route', async () => {
    aiCopilotApiMocks.listAICopilotSessions.mockResolvedValueOnce([])
    aiCopilotApiMocks.createAICopilotSession.mockResolvedValueOnce(
      buildSession({
        sessionId: 'session_route_001',
        title: '当前 PLM 单据 Copilot',
        contextTags: ['AI Copilot', 'route:/plm/ecr/create'],
      })
    )

    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage sourceRoute='/plm/ecr/create' />)

    await waitFor(() =>
      expect(aiCopilotApiMocks.createAICopilotSession).toHaveBeenCalledWith(
        {
          title: '当前 PLM 单据 Copilot',
          contextTags: ['AI Copilot', 'route:/plm/ecr/create'],
        },
        expect.any(Object)
      )
    )
  })
})
