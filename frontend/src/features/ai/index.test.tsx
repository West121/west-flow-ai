import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const aiCopilotApiMocks = vi.hoisted(() => ({
  listAICopilotSessions: vi.fn(),
  getAICopilotSession: vi.fn(),
  createAICopilotSession: vi.fn(),
  clearAICopilotSessions: vi.fn(),
  deleteAICopilotSession: vi.fn(),
  sendAICopilotMessage: vi.fn(),
  uploadAICopilotAsset: vi.fn(),
  downloadAICopilotAssetPreview: vi.fn(),
  transcribeAICopilotAudio: vi.fn(),
  confirmAICopilotConfirmation: vi.fn(),
}))

const systemUserApiMocks = vi.hoisted(() => ({
  listSystemUsers: vi.fn(),
  getSystemUserDetail: vi.fn(),
}))

vi.mock('@/lib/api/ai-copilot', () => aiCopilotApiMocks)
vi.mock('@/lib/api/system-users', () => systemUserApiMocks)

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
    systemUserApiMocks.listSystemUsers.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 2,
      pages: 1,
      groups: [],
      records: [
        {
          userId: 'usr_002',
          displayName: '李四',
          username: 'lisi',
          mobile: '13800000002',
          email: 'lisi@westflow.cn',
          departmentName: '人力资源部',
          postName: '部门负责人',
          status: 'ENABLED',
          createdAt: '2026-03-22T09:00:00+08:00',
        },
        {
          userId: 'usr_005',
          displayName: '王主管',
          username: 'wangzhuguan',
          mobile: '13800000005',
          email: 'wangzhuguan@westflow.cn',
          departmentName: '运营中心',
          postName: '总监',
          status: 'ENABLED',
          createdAt: '2026-03-22T09:00:00+08:00',
        },
      ],
    })
    systemUserApiMocks.getSystemUserDetail.mockResolvedValue({
      userId: 'usr_002',
      displayName: '李四',
      username: 'lisi',
      mobile: '13800000002',
      email: 'lisi@westflow.cn',
      companyId: 'comp_001',
      companyName: '西流科技',
      departmentId: 'dept_002',
      departmentName: '人力资源部',
      postId: 'post_002',
      postName: '部门负责人',
      roleIds: [],
      enabled: true,
      primaryAssignment: {
        userPostId: 'up_usr_002',
        companyId: 'comp_001',
        companyName: '西流科技',
        departmentId: 'dept_002',
        departmentName: '人力资源部',
        postId: 'post_002',
        postName: '部门负责人',
        roleIds: [],
        roleNames: [],
        primary: true,
        enabled: true,
      },
      partTimeAssignments: [],
    })
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
    aiCopilotApiMocks.deleteAICopilotSession.mockResolvedValue(undefined)
    aiCopilotApiMocks.uploadAICopilotAsset.mockResolvedValue({
      fileId: 'file_001',
      displayName: '请假材料.png',
      contentType: 'image/png',
      previewUrl: 'https://example.com/leave.png',
    })
    aiCopilotApiMocks.downloadAICopilotAssetPreview.mockResolvedValue(
      new Blob(['preview'], { type: 'image/png' })
    )
    aiCopilotApiMocks.transcribeAICopilotAudio.mockResolvedValue({
      text: '请帮我发起一个 5 天的事假',
    })
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
    expect(
      screen.getAllByText(/\d{2}\/\d{2} \d{2}:\d{2}:\d{2}/).length
    ).toBeGreaterThan(0)
    expect(screen.getByText('会话')).toBeInTheDocument()
    await waitFor(() =>
      expect(aiCopilotApiMocks.getAICopilotSession).toHaveBeenCalledWith(
        'session_001'
      )
    )
    expect(screen.queryByText('上下文摘要')).not.toBeInTheDocument()
    expect(screen.queryByText('工具命中')).not.toBeInTheDocument()
    expect(screen.queryByText('审计轨迹')).not.toBeInTheDocument()
    expect(screen.queryByText('workflow.todo.list')).not.toBeInTheDocument()
    expect(screen.queryByText('已完成执行')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /流程解释草稿/ }))

    await waitFor(() =>
      expect(aiCopilotApiMocks.getAICopilotSession).toHaveBeenCalledWith(
        'session_002'
      )
    )
    expect(screen.getByText('流程解释草稿')).toBeInTheDocument()
  })

  it('resolves persisted attachment previews from file ids for historical sessions', async () => {
    aiCopilotApiMocks.getAICopilotSession.mockResolvedValueOnce(
      buildSession({
        sessionId: 'session_001',
        history: [
          {
            messageId: 'session_001_msg_001',
            role: 'user',
            authorName: '你',
            createdAt: '2026-03-23T08:00:00.000Z',
            content: '已上传 1 张图片',
            blocks: [
              {
                type: 'attachments',
                items: [
                  {
                    fileId: 'file_history_001',
                    displayName: '请假材料.png',
                    contentType: 'image/png',
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

    await waitFor(() =>
      expect(aiCopilotApiMocks.downloadAICopilotAssetPreview).toHaveBeenCalledWith(
        'file_history_001'
      )
    )
    expect(await screen.findByAltText('请假材料.png')).toBeInTheDocument()
  })

  it('supports deleting a session from the context menu', async () => {
    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    const sessionTitle = await screen.findByText('待办分流建议')
    const sessionCard = sessionTitle.closest('button')
    expect(sessionCard).not.toBeNull()
    fireEvent.contextMenu(sessionCard as HTMLButtonElement)

    expect(await screen.findByText('删除会话')).toBeInTheDocument()
    fireEvent.click(screen.getByText('删除会话'))

    expect(await screen.findByText('确认删除')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    await waitFor(() =>
      expect(aiCopilotApiMocks.deleteAICopilotSession.mock.calls[0]?.[0]).toBe(
        'session_001'
      )
    )
  })

  it('sends a message and resolves a confirmation card state flow', async () => {
    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    await screen.findByText('待办分流建议')

    fireEvent.change(screen.getByPlaceholderText(/输入你的问题或指令/), {
      target: { value: '请生成一个确认卡和统计卡' },
    })
    await waitFor(() =>
      expect(
        screen.getByDisplayValue('请生成一个确认卡和统计卡')
      ).toBeInTheDocument()
    )
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '发送消息' })).toBeEnabled()
    )
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    await waitFor(() =>
      expect(aiCopilotApiMocks.sendAICopilotMessage).toHaveBeenCalledWith(
        {
          sessionId: 'session_001',
          content: '请生成一个确认卡和统计卡',
          attachments: [],
        },
        expect.any(Object)
      )
    )
    await waitFor(() =>
      expect(
        screen.queryByDisplayValue('请生成一个确认卡和统计卡')
      ).not.toBeInTheDocument()
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
      expect(
        screen.queryByRole('button', { name: '确认处理' })
      ).not.toBeInTheDocument()
    )
    expect(screen.queryByText('已确认')).not.toBeInTheDocument()
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

  it('shows contextual route hints and supports Enter to send quickly', async () => {
    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage sourceRoute='/plm/ecr/create' />)

    expect(await screen.findByText('待办分流建议')).toBeInTheDocument()
    expect(screen.getByText('上下文：PLM / ECR 新建')).toBeInTheDocument()
    expect(screen.getByText('Enter 发送，Shift+Enter 换行')).toBeInTheDocument()

    const composer = screen.getByPlaceholderText(/输入你的问题或指令/)
    fireEvent.change(composer, {
      target: { value: '请快速整理当前 ECR 的摘要' },
    })
    fireEvent.keyDown(composer, {
      key: 'Enter',
    })

    await waitFor(() =>
      expect(aiCopilotApiMocks.sendAICopilotMessage).toHaveBeenCalledWith(
        {
          sessionId: 'session_003',
          content: '请快速整理当前 ECR 的摘要',
          attachments: [],
        },
        expect.any(Object)
      )
    )
  })

  it('keeps Shift+Enter as a newline without sending', async () => {
    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage sourceRoute='/plm/ecr/create' />)

    await screen.findByText('待办分流建议')

    const composer = screen.getByPlaceholderText(/输入你的问题或指令/)
    fireEvent.change(composer, {
      target: { value: '第一行' },
    })
    fireEvent.keyDown(composer, {
      key: 'Enter',
      shiftKey: true,
    })

    await waitFor(() =>
      expect(aiCopilotApiMocks.sendAICopilotMessage).not.toHaveBeenCalled()
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
    expect(screen.queryByText('命中轨迹')).not.toBeInTheDocument()
    expect(screen.queryByText('命中：PLM 助手')).not.toBeInTheDocument()
    expect(screen.queryByText('执行结果')).not.toBeInTheDocument()
    expect(screen.getAllByText('执行失败').length).toBeGreaterThan(0)
    expect(screen.getAllByText('MCP 请求超时').length).toBeGreaterThan(0)
    expect(screen.queryByText('命中：外部知识库检索')).not.toBeInTheDocument()
    expect(screen.queryByText('来源：SKILL')).not.toBeInTheDocument()
    expect(screen.queryByText('来源：MCP')).not.toBeInTheDocument()
    expect(screen.queryByText('工具命中')).not.toBeInTheDocument()
    expect(screen.queryByText('审计轨迹')).not.toBeInTheDocument()
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
                  processDefinitionId: 'oa_leave:1',
                  processName: '请假审批',
                  processFormKey: 'oa-leave-start-form',
                  processFormVersion: '1.1.0',
                  businessType: 'OA_LEAVE',
                  sceneCode: 'default',
                  formData: {
                    leaveType: 'ANNUAL',
                    days: '1',
                    managerUserId: 'usr_002',
                    reason: '默认原因',
                    urgent: false,
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
    expect(screen.getAllByText('直属负责人').length).toBeGreaterThan(0)
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

  it('blocks AI process preview confirmation when runtime form is not registered', async () => {
    aiCopilotApiMocks.listAICopilotSessions.mockResolvedValueOnce([
      {
        sessionId: 'session_form_unsupported_001',
        title: 'AI 发起 PLM 流程',
        preview: '未找到运行时表单组件。',
        status: 'active',
        updatedAt: '2026-03-23T09:10:00.000Z',
        messageCount: 1,
        contextTags: ['AI Copilot', 'route:/plm/ecr/create'],
      },
    ])
    aiCopilotApiMocks.getAICopilotSession.mockImplementationOnce(async () =>
      buildSession({
        sessionId: 'session_form_unsupported_001',
        title: 'AI 发起 PLM 流程',
        contextTags: ['AI Copilot', 'route:/plm/ecr/create'],
        history: [
          {
            messageId: 'session_form_unsupported_001_msg_001',
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
                  confirmationId: 'session_form_unsupported_001_confirm_001',
                  processKey: 'plm_ecr',
                  processDefinitionId: 'plm_ecr:1',
                  processName: 'ECR 变更申请',
                  processFormKey: 'plm-ecr-start-form',
                  processFormVersion: '1.0.0',
                  businessType: 'PLM_ECR',
                  sceneCode: 'default',
                  formData: {
                    changeTitle: '测试变更',
                  },
                },
                fields: [],
                trace: [],
              },
              {
                type: 'confirm',
                confirmationId: 'session_form_unsupported_001_confirm_001',
                title: '请确认是否继续执行',
                summary: '确认后将发起真实业务流程。',
                confirmLabel: '确认处理',
                cancelLabel: '暂不执行',
                status: 'pending',
              },
            ],
          },
        ],
      })
    )

    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    expect(await screen.findByText('AI 发起 PLM 流程')).toBeInTheDocument()
    expect(await screen.findByTestId('ai-form-preview-submit')).toBeDisabled()
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
    expect(screen.queryByText('调用：session_retry_001_tool_001')).not.toBeInTheDocument()
    expect(screen.queryByText('确认单：session_retry_001_confirm_001')).not.toBeInTheDocument()

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
    expect(screen.queryByText('待办动作')).not.toBeInTheDocument()
    expect(screen.queryByText('完成待办')).not.toBeInTheDocument()
    expect(screen.queryByText('下一步建议')).not.toBeInTheDocument()
    expect(
      screen.queryByText('返回工作台刷新状态或继续追问流程轨迹')
    ).not.toBeInTheDocument()
    expect((await screen.findAllByText('执行失败')).length).toBeGreaterThan(0)

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

  it('renders chart blocks for structured statistics replies', async () => {
    aiCopilotApiMocks.listAICopilotSessions.mockResolvedValueOnce([
      {
        sessionId: 'session_stats_chart_001',
        title: '用户统计图表',
        preview: '已按部门汇总用户并生成图表。',
        status: 'active',
        updatedAt: '2026-03-27T09:25:00.000Z',
        messageCount: 1,
        contextTags: ['AI Copilot', 'route:/system/users/list'],
      },
    ])
    aiCopilotApiMocks.getAICopilotSession.mockImplementationOnce(async () =>
      buildSession({
        sessionId: 'session_stats_chart_001',
        title: '用户统计图表',
        contextTags: ['AI Copilot', 'route:/system/users/list'],
        history: [
          {
            messageId: 'session_stats_chart_001_msg_001',
            role: 'assistant',
            authorName: 'AI Copilot',
            createdAt: '2026-03-27T09:25:10.000Z',
            content: '当前共 13 名启用用户，主要集中在 PLM产品组 和 人力资源部。',
            blocks: [
              {
                type: 'stats',
                title: '按部门统计用户',
                description: '已按部门汇总当前启用用户分布。',
                metrics: [
                  { label: '用户总数', value: '13', tone: 'positive' },
                  { label: '部门数', value: '5', tone: 'neutral' },
                ],
              },
              {
                type: 'chart',
                title: '部门用户分布',
                summary: '当前共 13 名启用用户，主要集中在 PLM产品组 和 人力资源部。',
                detail: '按部门统计当前启用用户数量。',
                result: {
                  chart: {
                    type: 'bar',
                    xField: 'departmentName',
                    yField: 'userCount',
                    series: [{ dataKey: 'userCount', name: '用户数' }],
                  },
                  data: [
                    { departmentName: 'PLM产品组', userCount: 4 },
                    { departmentName: '人力资源部', userCount: 3 },
                    { departmentName: '财务部', userCount: 2 },
                  ],
                },
                metrics: [{ label: '用户总数', value: '13', tone: 'positive' }],
              },
            ],
          },
        ],
      })
    )

    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    expect(await screen.findByText('用户统计图表')).toBeInTheDocument()
    expect(await screen.findByText('部门用户分布')).toBeInTheDocument()
    expect(await screen.findByText('按部门统计用户')).toBeInTheDocument()
    expect(screen.getAllByText('用户总数').length).toBeGreaterThan(0)
  })

  it('renders table and metric presentations for structured statistics replies', async () => {
    aiCopilotApiMocks.listAICopilotSessions.mockResolvedValueOnce([
      {
        sessionId: 'session_stats_modes_001',
        title: '统计展示模式',
        preview: '支持表格和指标卡展示。',
        status: 'active',
        updatedAt: '2026-03-27T10:00:00.000Z',
        messageCount: 1,
        contextTags: ['AI Copilot', 'route:/system/users/list'],
      },
    ])
    aiCopilotApiMocks.getAICopilotSession.mockImplementationOnce(async () =>
      buildSession({
        sessionId: 'session_stats_modes_001',
        title: '统计展示模式',
        contextTags: ['AI Copilot', 'route:/system/users/list'],
        history: [
          {
            messageId: 'session_stats_modes_001_msg_001',
            role: 'assistant',
            authorName: 'AI Copilot',
            createdAt: '2026-03-27T10:00:10.000Z',
            content: '当前共 0 名停用用户。',
            blocks: [
              {
                type: 'chart',
                title: '停用用户统计',
                summary: '当前共 0 名停用用户。',
                result: {
                  chart: {
                    type: 'metric',
                    metricLabel: '用户总数',
                    valueLabel: '停用用户',
                    value: 0,
                  },
                  data: [{ label: '停用用户', value: 0 }],
                },
                metrics: [{ label: '用户总数', value: '0', tone: 'warning' }],
              },
              {
                type: 'chart',
                title: '按部门统计用户',
                summary: '按表格展示部门用户分布。',
                result: {
                  chart: {
                    type: 'table',
                    columns: [
                      { key: 'departmentName', label: '部门' },
                      { key: 'userCount', label: '用户数' },
                    ],
                  },
                  data: [
                    { departmentName: 'PLM产品组', userCount: 4 },
                    { departmentName: '人力资源部', userCount: 3 },
                  ],
                },
              },
            ],
          },
        ],
      })
    )

    const { AICopilotPage } = await import('./index')

    renderWithQuery(<AICopilotPage />)

    expect(await screen.findByText('停用用户统计')).toBeInTheDocument()
    expect(await screen.findByText('停用用户')).toBeInTheDocument()
    expect(screen.getAllByText('0').length).toBeGreaterThan(0)
    expect(await screen.findByText('部门')).toBeInTheDocument()
    expect(await screen.findByText('PLM产品组')).toBeInTheDocument()
  })
})
