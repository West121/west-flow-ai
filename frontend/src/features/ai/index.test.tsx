import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
    toolCalls:
      (overrides.toolCalls as unknown[] | undefined) ?? [
        {
          toolCallId: `${sessionId}_tool_001`,
          toolKey: 'workflow.todo.list',
          toolType: 'READ',
          toolSource: 'PLATFORM',
          status: 'SUCCEEDED',
          requiresConfirmation: false,
          summary: '读取当前用户待办列表',
          createdAt: '2026-03-23T08:00:05.000Z',
          completedAt: '2026-03-23T08:00:06.000Z',
        },
      ],
    audit:
      (overrides.audit as unknown[] | undefined) ?? [
        {
          auditId: `${sessionId}_audit_001`,
          conversationId: sessionId,
          toolCallId: `${sessionId}_tool_001`,
          actionType: 'TOOL_CALL_COMPLETED',
          summary: '已完成读取当前待办列表',
          occurredAt: '2026-03-23T08:00:06.000Z',
        },
      ],
    history:
      (overrides.history as unknown[] | undefined) ?? [
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
    expect(screen.getByText('会话')).toBeInTheDocument()
    await waitFor(() =>
      expect(aiCopilotApiMocks.getAICopilotSession).toHaveBeenCalledWith(
        'session_001'
      )
    )
    expect(screen.getByText('上下文摘要')).toBeInTheDocument()
    expect(screen.getByText('工具命中')).toBeInTheDocument()
    expect(screen.getByText('审计轨迹')).toBeInTheDocument()
    expect(screen.getByText('workflow.todo.list')).toBeInTheDocument()
    expect(screen.getByText('已完成执行')).toBeInTheDocument()

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
          argumentsOverride: undefined,
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

  it.each([
    {
      sourceRoute: '/plm/ecr/create',
      title: '当前 PLM 单据 Copilot',
      contextTags: ['AI Copilot', 'route:/plm/ecr/create'],
    },
    {
      sourceRoute: '/oa/leave/create',
      title: '当前 OA 单据 Copilot',
      contextTags: ['AI Copilot', 'route:/oa/leave/create'],
    },
  ])(
    'creates a contextual Copilot session for $sourceRoute as a smoke path',
    async ({ sourceRoute, title, contextTags }) => {
      aiCopilotApiMocks.listAICopilotSessions.mockResolvedValueOnce([])
      aiCopilotApiMocks.createAICopilotSession.mockResolvedValueOnce(
        buildSession({
          sessionId: `session_${sourceRoute.replace(/\//g, '_')}`,
          title,
          contextTags,
        })
      )

      const { AICopilotPage } = await import('./index')

      renderWithQuery(<AICopilotPage sourceRoute={sourceRoute} />)

      await waitFor(() =>
        expect(aiCopilotApiMocks.createAICopilotSession).toHaveBeenCalledWith(
          {
            title,
            contextTags,
          },
          expect.any(Object)
        )
      )
    }
  )

  it('shows contextual route hints and supports Ctrl+Enter to send quickly', async () => {
    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage sourceRoute='/plm/ecr/create' />)

    expect(await screen.findByText('待办分流建议')).toBeInTheDocument()
    expect(screen.getByText('上下文：PLM / ECR 新建')).toBeInTheDocument()
    expect(screen.getByText('Ctrl/Cmd + Enter 快速发送')).toBeInTheDocument()

    const composer = screen.getByPlaceholderText(/输入一条 Copilot 指令/)
    fireEvent.change(composer, {
      target: { value: '请快速整理当前 ECR 的摘要' },
    })
    fireEvent.keyDown(composer, {
      key: 'Enter',
      ctrlKey: true,
    })

    await waitFor(() =>
      expect(aiCopilotApiMocks.sendAICopilotMessage).toHaveBeenCalledWith(
        {
          sessionId: 'session_003',
          content: '请快速整理当前 ECR 的摘要',
        },
        expect.any(Object)
      )
    )
  })

  it('renders result, failure, and trace blocks with richer tool hit metadata', async () => {
    aiCopilotApiMocks.listAICopilotSessions.mockResolvedValueOnce([
      {
        sessionId: 'session_rich_001',
        title: 'AI 执行轨迹演示',
        preview: '我已经整理出本次执行结果与失败信息。',
        status: 'active',
        updatedAt: '2026-03-23T09:00:10.000Z',
        messageCount: 1,
        contextTags: ['PLM', 'AI Copilot', 'route:/plm/ecr/query'],
      },
    ])
    aiCopilotApiMocks.getAICopilotSession.mockImplementationOnce(
      async () =>
        buildSession({
          sessionId: 'session_rich_001',
          title: 'AI 执行轨迹演示',
          contextTags: ['PLM', 'AI Copilot', 'route:/plm/ecr/query'],
          toolCalls: [
            {
              toolCallId: 'tool_skill_001',
              toolKey: 'plm.change.summary',
              toolType: 'READ',
              toolSource: 'SKILL',
              status: 'SUCCEEDED',
              requiresConfirmation: false,
              summary: '通过技能链路汇总当前变更影响范围',
              createdAt: '2026-03-23T09:00:00.000Z',
              completedAt: '2026-03-23T09:00:02.000Z',
            },
            {
              toolCallId: 'tool_mcp_001',
              toolKey: 'external.mcp.search',
              toolType: 'READ',
              toolSource: 'MCP',
              status: 'FAILED',
              requiresConfirmation: false,
              summary: '访问外部 MCP 检索服务失败',
              createdAt: '2026-03-23T09:00:03.000Z',
              completedAt: '2026-03-23T09:00:05.000Z',
            },
          ],
          history: [
            {
              messageId: 'session_rich_001_msg_001',
              role: 'assistant',
              authorName: 'AI Copilot',
              createdAt: '2026-03-23T09:00:10.000Z',
              content: '我已经整理出本次执行结果与失败信息。',
              blocks: [
                {
                  type: 'trace',
                  title: '命中轨迹',
                  summary: 'Supervisor -> Routing -> PLM Skill',
                  detail: '本次优先命中 PLM 技能，再回到平台聚合结果。',
                  status: 'SUCCEEDED',
                  sourceType: 'SKILL',
                  sourceKey: 'plm-assistant',
                  sourceName: 'PLM 助手',
                  trace: [
                    {
                      stage: 'SUPERVISOR',
                      label: 'Supervisor',
                      detail: '判定为只读 PLM 分析请求',
                      status: 'SUCCEEDED',
                    },
                    {
                      stage: 'SKILL',
                      label: 'PLM Skill',
                      detail: '读取 ECR 与 ECO 关联数据',
                      status: 'SUCCEEDED',
                    },
                  ],
                },
                {
                  type: 'result',
                  title: '执行结果',
                  summary: '已生成本次 PLM 变更摘要。',
                  detail: '结果来自技能链路与平台工具聚合。',
                  sourceType: 'SKILL',
                  sourceKey: 'plm.change.summary',
                  sourceName: 'PLM 变更摘要',
                  toolType: 'READ',
                  result: {
                    billNo: 'ECR-20260323-001',
                    impactedCount: 4,
                  },
                  fields: [
                    {
                      label: '变更单号',
                      value: 'ECR-20260323-001',
                    },
                  ],
                  metrics: [
                    {
                      label: '影响对象数',
                      value: '4',
                      tone: 'warning',
                    },
                  ],
                  trace: [
                    {
                      stage: 'PLM',
                      label: '业务聚合',
                      detail: '已整合 ECR 与 ECO 影响范围',
                      status: 'SUCCEEDED',
                    },
                  ],
                },
                {
                  type: 'failure',
                  title: '执行失败',
                  summary: '外部 MCP 检索失败。',
                  detail: '请检查 MCP 连通性或稍后重试。',
                  sourceType: 'MCP',
                  sourceKey: 'external.mcp.search',
                  sourceName: '外部知识库检索',
                  toolType: 'READ',
                  failure: {
                    code: 'MCP_TIMEOUT',
                    message: 'MCP 请求超时',
                    detail: '远端 MCP 服务 5 秒未返回。',
                  },
                  trace: [
                    {
                      stage: 'MCP',
                      label: '外部 MCP',
                      detail: '连接外部检索端点超时',
                      status: 'FAILED',
                    },
                  ],
                },
              ],
            },
          ],
        })
    )

    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    expect(await screen.findByText('AI 执行轨迹演示')).toBeInTheDocument()
    await waitFor(() =>
      expect(aiCopilotApiMocks.getAICopilotSession).toHaveBeenCalledWith(
        'session_rich_001'
      )
    )
    expect(screen.getAllByText('命中轨迹').length).toBeGreaterThan(0)
    expect(screen.getAllByText('命中：PLM 助手').length).toBeGreaterThan(0)
    expect(screen.getAllByText('执行结果').length).toBeGreaterThan(0)
    expect(screen.getAllByText('变更单号').length).toBeGreaterThan(0)
    expect(screen.getAllByText('ECR-20260323-001').length).toBeGreaterThan(0)
    expect(screen.getAllByText('影响对象数').length).toBeGreaterThan(0)
    expect(screen.getAllByText('执行失败').length).toBeGreaterThan(0)
    expect(screen.getAllByText('MCP 请求超时').length).toBeGreaterThan(0)
    expect(screen.getAllByText('命中：外部知识库检索').length).toBeGreaterThan(0)
    expect(screen.getAllByText('来源：SKILL').length).toBeGreaterThan(0)
    expect(screen.getAllByText('来源：MCP').length).toBeGreaterThan(0)
  })

  it('submits editable form preview arguments and retries failure blocks', async () => {
    aiCopilotApiMocks.listAICopilotSessions.mockResolvedValueOnce([
      {
        sessionId: 'session_form_001',
        title: 'AI 发起流程',
        preview: '已生成可编辑表单卡。',
        status: 'active',
        updatedAt: '2026-03-23T09:10:00.000Z',
        messageCount: 1,
        contextTags: ['AI Copilot', 'route:/oa/leave/create'],
      },
    ])
    aiCopilotApiMocks.getAICopilotSession.mockImplementationOnce(async () =>
      buildSession({
        sessionId: 'session_form_001',
        title: 'AI 发起流程',
        contextTags: ['AI Copilot', 'route:/oa/leave/create'],
        history: [
          {
            messageId: 'session_form_001_msg_001',
            role: 'assistant',
            authorName: 'AI Copilot',
            createdAt: '2026-03-23T09:10:10.000Z',
            content: '我已经整理好发起参数，请确认。',
            blocks: [
              {
                type: 'form-preview',
                title: '拟发起流程预览',
                description: '确认前可直接修改表单字段。',
                sourceType: 'PLATFORM',
                sourceKey: 'process.start',
                sourceName: 'process.start',
                toolType: 'WRITE',
                result: {
                  editable: true,
                  confirmationId: 'session_form_001_confirm_001',
                  processKey: 'oa_leave',
                  businessType: 'OA_LEAVE',
                  sceneCode: 'default',
                  formData: {
                    days: '1',
                    reason: '默认原因',
                  },
                },
                fields: [
                  { label: '流程编码', value: 'oa_leave' },
                  { label: '业务类型', value: 'OA_LEAVE' },
                ],
                trace: [],
              },
              {
                type: 'confirm',
                confirmationId: 'session_form_001_confirm_001',
                title: '请确认是否继续执行',
                summary: '确认后将发起真实业务流程。',
                confirmLabel: '确认处理',
                cancelLabel: '暂不执行',
                status: 'pending',
              },
              {
                type: 'failure',
                title: '执行失败',
                summary: '上次执行失败，可直接重试。',
                sourceType: 'PLATFORM',
                sourceKey: 'task.handle',
                sourceName: 'task.handle',
                toolType: 'WRITE',
                result: {
                  retryable: true,
                  confirmationId: 'session_form_001_confirm_002',
                },
                failure: {
                  code: 'AI.TOOL_EXECUTE_FAILED',
                  message: '执行失败',
                  detail: '请重试',
                },
                trace: [],
              },
            ],
          },
        ],
      })
    )
    aiCopilotApiMocks.confirmAICopilotConfirmation.mockResolvedValue(
      buildSession({
        sessionId: 'session_form_001',
        title: 'AI 发起流程',
        contextTags: ['AI Copilot', 'route:/oa/leave/create'],
      })
    )

    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    expect(await screen.findByText('AI 发起流程')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryAllByTestId('ai-form-preview-editor').length).toBeGreaterThan(0)
    )
    expect(screen.getAllByText('请假天数').length).toBeGreaterThan(0)
    expect(screen.getAllByText('请假原因').length).toBeGreaterThan(0)
    const editableFormSection = screen
      .queryAllByTestId('ai-form-preview-editor')
      .find((section) => !within(section).getByTestId('ai-form-preview-submit').hasAttribute('disabled'))!
    const reasonInput = within(editableFormSection).getByLabelText('请假原因')
    fireEvent.change(reasonInput, {
      target: { value: '参加客户评审' },
    })
    expect(reasonInput).toHaveValue('参加客户评审')
    expect(
      screen
        .queryAllByTestId('ai-form-preview-submit')
        .some((button) => !button.hasAttribute('disabled'))
    ).toBe(true)

    fireEvent.click(
      screen
        .getAllByRole('button', { name: '重试处理待办' })
        .find((button) => !button.hasAttribute('disabled'))!
    )

    await waitFor(() =>
      expect(aiCopilotApiMocks.confirmAICopilotConfirmation).toHaveBeenCalledWith(
        {
          sessionId: 'session_form_001',
          confirmationId: 'session_form_001_confirm_002',
          decision: 'confirm',
          argumentsOverride: undefined,
        },
        expect.any(Object)
      )
    )
  })

  it('renders retry blocks with confirmation and tool call context', async () => {
    aiCopilotApiMocks.listAICopilotSessions.mockResolvedValueOnce([
      {
        sessionId: 'session_retry_001',
        title: 'AI 重试链路',
        preview: '失败后给出明确重试建议。',
        status: 'active',
        updatedAt: '2026-03-23T10:10:00.000Z',
        messageCount: 1,
        contextTags: ['AI Copilot', 'route:/workbench/todos/task_001'],
      },
    ])
    aiCopilotApiMocks.getAICopilotSession.mockImplementationOnce(async () =>
      buildSession({
        sessionId: 'session_retry_001',
        title: 'AI 重试链路',
        contextTags: ['AI Copilot', 'route:/workbench/todos/task_001'],
        history: [
          {
            messageId: 'session_retry_001_msg_001',
            role: 'assistant',
            authorName: 'AI Copilot',
            createdAt: '2026-03-23T10:10:10.000Z',
            content: '本次写操作失败，但已经给出可直接重试的建议。',
            blocks: [
              {
                type: 'retry',
                title: '重试建议',
                summary: '当前写操作执行失败，可以在修正参数后重新确认并重试。',
                detail: '建议优先检查处理意见和当前任务状态。',
                sourceType: 'PLATFORM',
                sourceKey: 'task.handle',
                sourceName: 'task.handle',
                toolType: 'WRITE',
                result: {
                  confirmationId: 'session_retry_001_confirm_001',
                  toolCallId: 'session_retry_001_tool_001',
                  retryable: true,
                },
                fields: [
                  { label: '工具名称', value: 'task.handle' },
                  { label: '确认单编号', value: 'session_retry_001_confirm_001' },
                ],
                metrics: [
                  { label: '可重试', value: '是', tone: 'warning' },
                ],
                trace: [
                  {
                    stage: 'TASK',
                    label: '任务处理',
                    detail: '执行处理动作失败，可修正参数后重试。',
                    status: 'FAILED',
                  },
                ],
              },
            ],
          },
        ],
      })
    )
    aiCopilotApiMocks.confirmAICopilotConfirmation.mockResolvedValue(
      buildSession({
        sessionId: 'session_retry_001',
        title: 'AI 重试链路',
        contextTags: ['AI Copilot', 'route:/workbench/todos/task_001'],
      })
    )

    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    expect(await screen.findByText('AI 重试链路')).toBeInTheDocument()
    const retryButtons = await screen.findAllByRole('button', {
      name: '重试处理待办',
    })
    expect(screen.getAllByText('调用：session_retry_001_tool_001').length).toBeGreaterThan(0)
    expect(screen.getAllByText('确认单：session_retry_001_confirm_001').length).toBeGreaterThan(0)

    fireEvent.click(retryButtons[0])

    await waitFor(() =>
      expect(aiCopilotApiMocks.confirmAICopilotConfirmation).toHaveBeenCalledWith(
        {
          sessionId: 'session_retry_001',
          confirmationId: 'session_retry_001_confirm_001',
          decision: 'confirm',
          argumentsOverride: undefined,
        },
        expect.any(Object)
      )
    )
  })

  it('renders task.handle cards with business action, next step, and retry wording', async () => {
    aiCopilotApiMocks.listAICopilotSessions.mockResolvedValueOnce([
      {
        sessionId: 'session_task_handle_001',
        title: '待办处理闭环',
        preview: '处理结果已经回写。',
        status: 'active',
        updatedAt: '2026-03-23T10:20:00.000Z',
        messageCount: 1,
        contextTags: ['AI Copilot', 'route:/workbench/todos/task_001'],
      },
    ])
    aiCopilotApiMocks.getAICopilotSession.mockImplementationOnce(async () =>
      buildSession({
        sessionId: 'session_task_handle_001',
        title: '待办处理闭环',
        contextTags: ['AI Copilot', 'route:/workbench/todos/task_001'],
        history: [
          {
            messageId: 'session_task_handle_001_msg_001',
            role: 'assistant',
            authorName: 'AI Copilot',
            createdAt: '2026-03-23T10:20:10.000Z',
            content: '待办处理已经返回结构化结果。',
            blocks: [
              {
                type: 'result',
                title: '执行结果',
                summary: '待办处理成功。',
                sourceType: 'PLATFORM',
                sourceKey: 'task.handle',
                sourceName: 'task.handle',
                toolType: 'WRITE',
                result: {
                  toolCallId: 'session_task_handle_001_tool_001',
                  confirmationId: 'session_task_handle_001_confirm_001',
                  arguments: {
                    taskId: 'task_001',
                    action: 'COMPLETE',
                    comment: '审批通过',
                  },
                },
                fields: [
                  { label: '待办动作', value: '完成待办' },
                  { label: '待办编号', value: 'task_001' },
                  {
                    label: '下一步建议',
                    value: '返回工作台刷新状态或继续追问流程轨迹',
                  },
                ],
              },
              {
                type: 'failure',
                title: '执行失败',
                summary: '待办处理失败。',
                sourceType: 'PLATFORM',
                sourceKey: 'task.handle',
                sourceName: 'task.handle',
                toolType: 'WRITE',
                result: {
                  toolCallId: 'session_task_handle_001_tool_002',
                  confirmationId: 'session_task_handle_001_confirm_002',
                  retryable: true,
                  arguments: {
                    taskId: 'task_002',
                    action: 'REJECT',
                  },
                },
                failure: {
                  code: 'AI.TOOL_EXECUTE_FAILED',
                  message: '执行失败',
                  detail: '当前任务状态已变化。',
                },
                fields: [
                  { label: '待办动作', value: '驳回待办' },
                  { label: '失败原因', value: '执行失败' },
                ],
              },
              {
                type: 'retry',
                title: '重试建议',
                summary: '可以沿用原动作快速重试。',
                sourceType: 'PLATFORM',
                sourceKey: 'task.handle',
                sourceName: 'task.handle',
                toolType: 'WRITE',
                result: {
                  toolCallId: 'session_task_handle_001_tool_002',
                  confirmationId: 'session_task_handle_001_confirm_002',
                  retryable: true,
                  arguments: {
                    taskId: 'task_002',
                    action: 'REJECT',
                  },
                },
                fields: [
                  { label: '待办动作', value: '驳回待办' },
                  {
                    label: '下一步建议',
                    value: '修正意见后直接再次确认执行',
                  },
                ],
              },
            ],
          },
        ],
      })
    )
    aiCopilotApiMocks.confirmAICopilotConfirmation.mockResolvedValue(
      buildSession({
        sessionId: 'session_task_handle_001',
        title: '待办处理闭环',
        contextTags: ['AI Copilot', 'route:/workbench/todos/task_001'],
      })
    )

    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    expect(await screen.findByText('待办处理闭环')).toBeInTheDocument()
    expect(await screen.findAllByText('待办动作')).not.toHaveLength(0)
    expect(screen.getAllByText('完成待办').length).toBeGreaterThan(0)
    expect(screen.getAllByText('下一步建议').length).toBeGreaterThan(0)
    expect(screen.getAllByText('返回工作台刷新状态或继续追问流程轨迹').length).toBeGreaterThan(0)

    const retryButtons = await screen.findAllByRole('button', {
      name: '重试驳回待办',
    })
    fireEvent.click(retryButtons[0])

    await waitFor(() =>
      expect(aiCopilotApiMocks.confirmAICopilotConfirmation).toHaveBeenCalledWith(
        {
          sessionId: 'session_task_handle_001',
          confirmationId: 'session_task_handle_001_confirm_002',
          decision: 'confirm',
          argumentsOverride: undefined,
        },
        expect.any(Object)
      )
    )
  })
})
