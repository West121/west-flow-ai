import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AiConfirmationDetailPage,
  AiToolCallDetailPage,
} from './record-pages'
import {
  AiAgentDetailPage,
  AiMcpDetailPage,
  AiSkillDetailPage,
  AiToolDetailPage,
} from './registry-pages'

const {
  navigateMock,
  aiAdminApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  aiAdminApiMocks: {
    getAiAgentDetail: vi.fn(),
    getAiAgentFormOptions: vi.fn(),
    getAiConfirmationDetail: vi.fn(),
    getAiConversationDetail: vi.fn(),
    getAiMcpDetail: vi.fn(),
    getAiMcpDiagnosticDetail: vi.fn(),
    getAiMcpFormOptions: vi.fn(),
    getAiSkillDetail: vi.fn(),
    getAiSkillFormOptions: vi.fn(),
    getAiToolCallDetail: vi.fn(),
    getAiToolDetail: vi.fn(),
    getAiToolFormOptions: vi.fn(),
    listAiAgents: vi.fn(),
    listAiConfirmations: vi.fn(),
    listAiConversations: vi.fn(),
    listAiMcps: vi.fn(),
    listAiMcpDiagnostics: vi.fn(),
    listAiSkills: vi.fn(),
    listAiToolCalls: vi.fn(),
    listAiTools: vi.fn(),
    createAiAgent: vi.fn(),
    createAiMcp: vi.fn(),
    createAiSkill: vi.fn(),
    createAiTool: vi.fn(),
    updateAiAgent: vi.fn(),
    updateAiMcp: vi.fn(),
    updateAiSkill: vi.fn(),
    updateAiTool: vi.fn(),
    recheckAiMcpDiagnostic: vi.fn(),
  },
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    to,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to?: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => navigateMock,
  getRouteApi: () => ({
    useSearch: () => ({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    }),
    useNavigate: () => navigateMock,
  }),
}))

vi.mock('@/features/shared/page-shell', () => ({
  PageShell: ({
    title,
    description,
    actions,
    children,
  }: {
    title: string
    description?: string
    actions?: React.ReactNode
    children: React.ReactNode
  }) => (
    <section>
      <h1>{title}</h1>
      {description ? <p>{description}</p> : null}
      {actions}
      {children}
    </section>
  ),
}))

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: ({ title }: { title: string }) => <section>{title}</section>,
}))

vi.mock('@/lib/api/ai-admin', () => aiAdminApiMocks)
vi.mock('@/lib/handle-server-error', () => ({
  handleServerError: vi.fn(),
}))
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  )
}

describe('ai admin pages', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    Object.values(aiAdminApiMocks).forEach((mock) => mock.mockReset())
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders toolcall and confirmation diagnostic chains', async () => {
    aiAdminApiMocks.getAiToolCallDetail.mockResolvedValue({
      toolCallId: 'call_001',
      conversationId: 'conv_001',
      toolKey: 'workflow.trace.summary',
      toolType: 'READ',
      toolSource: 'MCP',
      hitSource: 'MCP',
      status: 'FAILED',
      requiresConfirmation: true,
      summary: '查询流程轨迹失败',
      confirmationId: 'confirm_001',
      operatorUserId: 'usr_admin',
      createdAt: '2026-03-23T09:00:00+08:00',
      completedAt: '2026-03-23T09:00:03+08:00',
      executionDurationMillis: 3000,
      failureReason: '远端 MCP 请求超时',
      argumentsJson: '{"processKey":"oa_leave"}',
      resultJson: '{"error":"MCP_TIMEOUT"}',
      failureCode: 'MCP_TIMEOUT',
      conversationTitle: '流程设计助手对话',
      confirmationStatus: 'REJECTED',
      confirmationApproved: false,
      confirmationResolvedBy: 'usr_reviewer',
      confirmationComment: '风险较高，暂不执行',
      linkedTool: {
        entityType: 'TOOL',
        entityId: 'tool_001',
        entityCode: 'workflow.trace.summary',
        entityName: '流程轨迹摘要',
        capabilityCode: 'ai:task:handle',
        status: 'ENABLED',
      },
      linkedSkill: {
        entityType: 'SKILL',
        entityId: 'skill_001',
        entityCode: 'workflow.trace.summary',
        entityName: '流程轨迹技能',
        capabilityCode: 'ai:task:handle',
        status: 'ENABLED',
      },
      linkedMcp: {
        entityType: 'MCP',
        entityId: 'mcp_001',
        entityCode: 'workflow',
        entityName: '流程中心 MCP',
        capabilityCode: 'ai:task:handle',
        status: 'ENABLED',
      },
      linkedAgents: [
        {
          entityType: 'AGENT',
          entityId: 'agent_001',
          entityCode: 'task-handle-agent',
          entityName: '待办处理智能体',
          capabilityCode: 'ai:task:handle',
          status: 'ENABLED',
        },
      ],
    })

    renderWithQuery(<AiToolCallDetailPage toolCallId='call_001' />)

    await waitFor(() => {
      expect(aiAdminApiMocks.getAiToolCallDetail).toHaveBeenCalledWith('call_001')
    })
    expect(await screen.findByText('MCP_TIMEOUT')).toBeInTheDocument()
    expect((await screen.findAllByText('workflow.trace.summary')).length).toBeGreaterThan(0)
    expect(await screen.findByText('task-handle-agent')).toBeInTheDocument()
    expect(await screen.findByText('风险较高，暂不执行')).toBeInTheDocument()

    aiAdminApiMocks.getAiConfirmationDetail.mockResolvedValue({
      confirmationId: 'confirm_001',
      toolCallId: 'call_002',
      status: 'REJECTED',
      approved: false,
      comment: '人工拒绝执行',
      resolvedBy: 'usr_reviewer',
      createdAt: '2026-03-23T10:00:00+08:00',
      resolvedAt: '2026-03-23T10:05:00+08:00',
      updatedAt: '2026-03-23T10:05:00+08:00',
      toolKey: 'plm.change.execute',
      toolType: 'WRITE',
      toolSource: 'PLATFORM',
      hitSource: 'PLATFORM',
      toolCallStatus: 'REJECTED',
      conversationTitle: 'PLM 助手对话',
      failureReason: '人工拒绝执行',
      linkedTool: {
        entityType: 'TOOL',
        entityId: 'tool_002',
        entityCode: 'plm.change.execute',
        entityName: 'PLM 变更执行',
        capabilityCode: 'ai:plm:change',
        status: 'ENABLED',
      },
      linkedAgents: [
        {
          entityType: 'AGENT',
          entityId: 'agent_002',
          entityCode: 'plm-assistant-agent',
          entityName: 'PLM 智能体',
          capabilityCode: 'ai:plm:change',
          status: 'ENABLED',
        },
      ],
    })

    renderWithQuery(<AiConfirmationDetailPage confirmationId='confirm_001' />)

    await waitFor(() => {
      expect(aiAdminApiMocks.getAiConfirmationDetail).toHaveBeenCalledWith('confirm_001')
    })
    expect((await screen.findAllByText('plm.change.execute')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('人工拒绝执行')).length).toBeGreaterThan(0)
    expect(await screen.findByText('plm-assistant-agent')).toBeInTheDocument()
  })

  it('renders registry observability cards and linked resources', async () => {
    const observability = {
      totalToolCalls: 12,
      successfulToolCalls: 8,
      failedToolCalls: 3,
      pendingConfirmations: 1,
      averageDurationMillis: 820,
      latestToolCallId: 'call_latest',
      latestToolCallAt: '2026-03-23T11:00:00+08:00',
      latestFailureReason: '最近一次 MCP 超时',
    }

    aiAdminApiMocks.getAiAgentDetail.mockResolvedValue({
      agentId: 'agent_001',
      agentCode: 'task-handle-agent',
      agentName: '待办处理智能体',
      capabilityCode: 'ai:task:handle',
      routeMode: 'SUPERVISOR',
      supervisor: true,
      priority: 100,
      status: 'ENABLED',
      contextTags: ['OA', 'Task'],
      systemPrompt: '负责处理待办',
      metadataJson: '{"routeMode":"SUPERVISOR"}',
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
      description: '待办链路总控 Agent',
      observability,
      linkedTools: [{
        entityType: 'TOOL',
        entityId: 'tool_001',
        entityCode: 'workflow.trace.summary',
        entityName: '流程轨迹摘要',
        capabilityCode: 'ai:task:handle',
        status: 'ENABLED',
      }],
      linkedSkills: [],
      linkedMcps: [],
    })

    renderWithQuery(<AiAgentDetailPage agentId='agent_001' />)

    await waitFor(() => {
      expect(aiAdminApiMocks.getAiAgentDetail).toHaveBeenCalledWith('agent_001')
    })
    expect(await screen.findByText('累计调用')).toBeInTheDocument()
    expect(await screen.findByText('workflow.trace.summary')).toBeInTheDocument()

    aiAdminApiMocks.getAiToolDetail.mockResolvedValue({
      toolId: 'tool_001',
      toolCode: 'workflow.trace.summary',
      toolName: '流程轨迹摘要',
      toolCategory: 'MCP',
      actionMode: 'READ',
      requiredCapabilityCode: 'ai:task:handle',
      status: 'ENABLED',
      metadataJson: '{}',
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
      description: '查看流程轨迹',
      observability,
      linkedAgents: [],
      linkedSkill: null,
      linkedMcp: {
        entityType: 'MCP',
        entityId: 'mcp_001',
        entityCode: 'workflow',
        entityName: '流程中心 MCP',
        capabilityCode: 'ai:task:handle',
        status: 'ENABLED',
      },
    })

    renderWithQuery(<AiToolDetailPage toolId='tool_001' />)
    await waitFor(() => {
      expect(aiAdminApiMocks.getAiToolDetail).toHaveBeenCalledWith('tool_001')
    })
    expect(await screen.findByText('查看流程轨迹')).toBeInTheDocument()

    aiAdminApiMocks.getAiSkillDetail.mockResolvedValue({
      skillId: 'skill_001',
      skillCode: 'workflow.trace.summary',
      skillName: '流程轨迹技能',
      skillPath: '/opt/skills/workflow-trace/SKILL.md',
      requiredCapabilityCode: 'ai:task:handle',
      status: 'ENABLED',
      metadataJson: '{}',
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
      description: '流程轨迹 Skill',
      observability,
      linkedAgents: [],
      linkedTool: null,
      linkedMcp: null,
    })

    renderWithQuery(<AiSkillDetailPage skillId='skill_001' />)
    await waitFor(() => {
      expect(aiAdminApiMocks.getAiSkillDetail).toHaveBeenCalledWith('skill_001')
    })
    expect(await screen.findByText('流程轨迹 Skill')).toBeInTheDocument()

    aiAdminApiMocks.getAiMcpDetail.mockResolvedValue({
      mcpId: 'mcp_001',
      mcpCode: 'workflow',
      mcpName: '流程中心 MCP',
      endpointUrl: 'http://localhost:18080/mcp',
      transportType: 'STREAMABLE_HTTP',
      requiredCapabilityCode: 'ai:task:handle',
      status: 'ENABLED',
      metadataJson: '{}',
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
      description: '流程中心连通入口',
      observability,
      linkedAgents: [],
      linkedTools: [],
      linkedSkills: [],
    })
    aiAdminApiMocks.getAiMcpDiagnosticDetail.mockResolvedValue({
      mcpId: 'mcp_001',
      mcpCode: 'workflow',
      mcpName: '流程中心 MCP',
      endpointUrl: 'http://localhost:18080/mcp',
      transportType: 'STREAMABLE_HTTP',
      requiredCapabilityCode: 'ai:task:handle',
      enabled: true,
      registryStatus: 'ENABLED',
      connectionStatus: 'UP',
      responseTimeMillis: 360,
      toolCount: 4,
      failureReason: null,
      failureDetail: null,
      failureStage: null,
      diagnosticSteps: [],
      observability,
      checkedAt: '2026-03-23T11:00:00+08:00',
      metadataJson: '{}',
    })

    renderWithQuery(<AiMcpDetailPage mcpId='mcp_001' />)
    await waitFor(() => {
      expect(aiAdminApiMocks.getAiMcpDetail).toHaveBeenCalledWith('mcp_001')
      expect(aiAdminApiMocks.getAiMcpDiagnosticDetail).toHaveBeenCalledWith('mcp_001')
    })
    expect((await screen.findAllByText('最近一次 MCP 超时')).length).toBeGreaterThan(0)
  })
})
