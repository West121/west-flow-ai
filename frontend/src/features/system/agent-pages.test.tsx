import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AgentCreatePage,
  AgentDetailPage,
  AgentEditPage,
  AgentsListPage,
  SystemHandoverExecutePage,
} from './agent-pages'

const {
  navigateMock,
  useSearchMock,
  routeNavigateMock,
  systemAgentApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  useSearchMock: vi.fn(),
  routeNavigateMock: vi.fn(),
  systemAgentApiMocks: {
    createSystemAgent: vi.fn(),
    executeSystemHandover: vi.fn(),
    getSystemAgentDetail: vi.fn(),
    getSystemAgentFormOptions: vi.fn(),
    listSystemAgents: vi.fn(),
    previewSystemHandover: vi.fn(),
    updateSystemAgent: vi.fn(),
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
    useSearch: useSearchMock,
    useNavigate: () => routeNavigateMock,
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
  ResourceListPage: ({
    title,
    data,
    total,
    summaries,
    createAction,
  }: {
    title: string
    data: Array<{
      agentId: string
      sourceUserName: string
      targetUserName: string
      status: string
      description?: string | null
    }>
    total?: number
    summaries: Array<{ label: string; value: string; hint: string }>
    createAction?: { label: string; href: string }
  }) => (
    <section>
      <h2>{title}</h2>
      {createAction ? <a href={createAction.href}>{createAction.label}</a> : null}
      <p data-testid='resource-total'>{String(total ?? data.length)}</p>
      <div data-testid='resource-summaries'>
        {summaries.map((item) => (
          <span key={item.label}>
            {item.label}:{item.value}
          </span>
        ))}
      </div>
      <ul>
        {data.map((item) => (
          <li key={item.agentId}>
            {item.sourceUserName}-{item.targetUserName}-{item.status}
          </li>
        ))}
      </ul>
    </section>
  ),
}))

vi.mock('@/lib/api/system-agents', () => systemAgentApiMocks)

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

function makeUsers() {
  return [
    {
      id: 'usr_001',
      displayName: '张三',
      username: 'zhangsan',
      departmentName: '财务部',
      postName: '报销审核岗',
      enabled: true,
    },
    {
      id: 'usr_002',
      displayName: '李四',
      username: 'lisi',
      departmentName: '人事部',
      postName: '流程管理员',
      enabled: true,
    },
    {
      id: 'usr_003',
      displayName: '王五',
      username: 'wangwu',
      departmentName: '行政部',
      postName: '综合岗',
      enabled: true,
    },
  ]
}

describe('system agent pages', () => {
  beforeEach(() => {
    vi.resetModules()
    navigateMock.mockReset()
    useSearchMock.mockReset()
    routeNavigateMock.mockReset()
    Object.values(systemAgentApiMocks).forEach((mock) => {
      mock.mockReset()
    })
    useSearchMock.mockReturnValue({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('shows agent list and updates status filter in url search state', async () => {
    // 这个用例确认列表页能接真实数据，并且状态筛选会写回 URL 搜索条件。
    systemAgentApiMocks.listSystemAgents.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 2,
      pages: 1,
      groups: [],
      records: [
        {
          agentId: 'agent_001',
          sourceUserId: 'usr_001',
          sourceUserName: '张三',
          targetUserId: 'usr_002',
          targetUserName: '李四',
          description: '代理请假审批',
          status: 'ACTIVE',
          createdAt: '2026-03-22T09:00:00+08:00',
          updatedAt: '2026-03-22T09:10:00+08:00',
        },
        {
          agentId: 'agent_002',
          sourceUserId: 'usr_003',
          sourceUserName: '王五',
          targetUserId: 'usr_002',
          targetUserName: '李四',
          description: '停用代理关系',
          status: 'DISABLED',
          createdAt: '2026-03-22T10:00:00+08:00',
          updatedAt: '2026-03-22T10:10:00+08:00',
        },
      ],
    })

    renderWithQuery(<AgentsListPage />)

    await waitFor(() => {
      expect(systemAgentApiMocks.listSystemAgents).toHaveBeenCalled()
    })

    expect(screen.getByRole('heading', { name: '代理关系管理' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '新建代理关系' })).toHaveAttribute(
      'href',
      '/system/agents/create'
    )
    await screen.findByText('张三-李四-ACTIVE')

    fireEvent.click(screen.getByRole('button', { name: '停用' }))

    expect(routeNavigateMock).toHaveBeenCalled()
    const navigateCall = routeNavigateMock.mock.calls[routeNavigateMock.mock.calls.length - 1]?.[0]
    const nextSearch = navigateCall.search({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })
    expect(nextSearch.filters).toEqual([
      {
        field: 'status',
        operator: 'eq',
        value: 'DISABLED',
      },
    ])
  })

  it('creates a new agent relation with simple chinese form labels', async () => {
    // 这个用例确认新建页能通过中文字段完成提交。
    systemAgentApiMocks.getSystemAgentFormOptions.mockResolvedValue({
      users: makeUsers(),
    })
    systemAgentApiMocks.createSystemAgent.mockResolvedValue({
      agentId: 'agent_new',
    })

    renderWithQuery(<AgentCreatePage />)

    await screen.findByLabelText('委托人用户')

    fireEvent.change(screen.getByLabelText('委托人用户'), {
      target: { value: 'usr_001' },
    })
    fireEvent.change(screen.getByLabelText('代理人用户'), {
      target: { value: 'usr_002' },
    })
    fireEvent.change(screen.getByLabelText('生效说明'), {
      target: { value: '代理请假审批' },
    })

    fireEvent.click(screen.getByRole('button', { name: '保存并返回列表' }))

    await waitFor(() => {
      expect(systemAgentApiMocks.createSystemAgent).toHaveBeenCalledWith({
        sourceUserId: 'usr_001',
        targetUserId: 'usr_002',
        description: '代理请假审批',
        enabled: true,
      })
    })
    expect(navigateMock).toHaveBeenCalledWith({ to: '/system/agents/list' })
  })

  it('loads detail and supports edit submit in place', async () => {
    // 这个用例确认详情页能回显数据，编辑页能在原地保存。
    systemAgentApiMocks.getSystemAgentFormOptions.mockResolvedValue({
      users: makeUsers(),
    })
    systemAgentApiMocks.getSystemAgentDetail.mockResolvedValue({
      agentId: 'agent_001',
      sourceUserId: 'usr_001',
      sourceUserName: '张三',
      targetUserId: 'usr_002',
      targetUserName: '李四',
      description: '代理请假审批',
      status: 'ACTIVE',
      createdAt: '2026-03-22T09:00:00+08:00',
      updatedAt: '2026-03-22T09:10:00+08:00',
    })
    systemAgentApiMocks.updateSystemAgent.mockResolvedValue({
      agentId: 'agent_001',
    })

    renderWithQuery(<AgentEditPage agentId='agent_001' />)

    expect(screen.getByRole('heading', { name: '编辑代理关系' })).toBeInTheDocument()
    await screen.findByLabelText('委托人用户')
    expect(screen.getByLabelText('委托人用户')).toHaveValue('usr_001')
    expect(screen.getByLabelText('代理人用户')).toHaveValue('usr_002')

    fireEvent.change(screen.getByLabelText('生效说明'), {
      target: { value: '更新后的代理说明' },
    })
    fireEvent.click(screen.getByRole('button', { name: '保存并继续编辑' }))

    await waitFor(() => {
      expect(systemAgentApiMocks.updateSystemAgent).toHaveBeenCalledWith(
        'agent_001',
        expect.objectContaining({
          sourceUserId: 'usr_001',
          targetUserId: 'usr_002',
          description: '更新后的代理说明',
          enabled: true,
        })
      )
    })
  })

  it('shows agent detail page with edit entry', async () => {
    // 这个用例确认详情页独立存在，并且提供跳转编辑的入口。
    systemAgentApiMocks.getSystemAgentDetail.mockResolvedValue({
      agentId: 'agent_001',
      sourceUserId: 'usr_001',
      sourceUserName: '张三',
      targetUserId: 'usr_002',
      targetUserName: '李四',
      description: '代理请假审批',
      status: 'ACTIVE',
      createdAt: '2026-03-22T09:00:00+08:00',
      updatedAt: '2026-03-22T09:10:00+08:00',
    })

    renderWithQuery(<AgentDetailPage agentId='agent_001' />)

    await waitFor(() => {
      expect(systemAgentApiMocks.getSystemAgentDetail).toHaveBeenCalledWith('agent_001')
    })

    expect(screen.getByRole('heading', { name: '代理关系详情' })).toBeInTheDocument()
    expect(await screen.findByText('张三（usr_001）')).toBeInTheDocument()
    expect(screen.getByText('李四（usr_002）')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '编辑' })).toHaveAttribute(
      'href',
      '/system/agents/$agentId/edit'
    )
    expect(screen.getByText('代理请假审批')).toBeInTheDocument()
  })

  it('previews and executes handover with result details', async () => {
    // 这个用例确认离职转办页遵循“先预览、后执行”的闭环。
    systemAgentApiMocks.getSystemAgentFormOptions.mockResolvedValue({
      users: makeUsers(),
    })
    systemAgentApiMocks.previewSystemHandover.mockResolvedValue({
      sourceUserId: 'usr_001',
      targetUserId: 'usr_002',
      transferableCount: 2,
      tasks: [
        {
          taskId: 'task_001',
          instanceId: 'pi_001',
          processName: '请假审批',
          businessTitle: '张三请假',
          billNo: 'LEAVE-001',
          currentNodeName: '部门负责人审批',
          currentTaskStatus: 'PENDING',
          assigneeUserId: 'usr_001',
          createdAt: '2026-03-22T09:00:00+08:00',
          canTransfer: true,
          reason: null,
        },
      ],
    })
    systemAgentApiMocks.executeSystemHandover.mockResolvedValue({
      sourceUserId: 'usr_001',
      targetUserId: 'usr_002',
      transferredCount: 2,
      transferredTaskIds: ['task_001', 'task_002'],
      tasks: [
        {
          taskId: 'task_001',
          instanceId: 'pi_001',
          processName: '请假审批',
          businessTitle: '张三请假',
          billNo: 'LEAVE-001',
          currentNodeName: '部门负责人审批',
          currentTaskStatus: 'PENDING',
          assigneeUserId: 'usr_001',
          createdAt: '2026-03-22T09:00:00+08:00',
          canTransfer: true,
          reason: null,
        },
      ],
    })

    renderWithQuery(<SystemHandoverExecutePage />)

    await screen.findByLabelText('来源用户')

    fireEvent.change(screen.getByLabelText('来源用户'), {
      target: { value: 'usr_001' },
    })
    fireEvent.change(screen.getByLabelText('目标用户'), {
      target: { value: 'usr_002' },
    })
    fireEvent.change(screen.getByLabelText('转办说明'), {
      target: { value: '离职转办预览' },
    })

    fireEvent.click(screen.getByRole('button', { name: '预览结果' }))

    await waitFor(() => {
      expect(systemAgentApiMocks.previewSystemHandover).toHaveBeenCalledWith({
        sourceUserId: 'usr_001',
        targetUserId: 'usr_002',
        comment: '离职转办预览',
      })
    })
    expect(screen.getByText('共预览到 2 条可迁移任务。')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '确认执行' }))

    await waitFor(() => {
      expect(systemAgentApiMocks.executeSystemHandover).toHaveBeenCalledWith({
        sourceUserId: 'usr_001',
        targetUserId: 'usr_002',
        comment: '离职转办预览',
      })
    })
    expect(screen.getByText('执行成功')).toBeInTheDocument()
    expect(screen.getByText('请假审批')).toBeInTheDocument()
  })
})
