import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const {
  navigateMock,
  useSearchMock,
  routeNavigateMock,
  notificationChannelApiMocks,
  triggerApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  useSearchMock: vi.fn(),
  routeNavigateMock: vi.fn(),
  notificationChannelApiMocks: {
    createNotificationChannel: vi.fn(),
    getNotificationChannelDiagnostic: vi.fn(),
    getNotificationChannelDetail: vi.fn(),
    getNotificationChannelFormOptions: vi.fn(),
    listNotificationChannels: vi.fn(),
    updateNotificationChannel: vi.fn(),
  },
  triggerApiMocks: {
    createTrigger: vi.fn(),
    getTriggerDetail: vi.fn(),
    getTriggerFormOptions: vi.fn(),
    listTriggers: vi.fn(),
    updateTrigger: vi.fn(),
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
    actions,
    children,
  }: {
    title: string
    actions?: React.ReactNode
    children: React.ReactNode
  }) => (
    <div>
      <h1>{title}</h1>
      {actions}
      {children}
    </div>
  ),
}))

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: ({
    title,
    createAction,
    data,
    total,
    columns,
  }: {
    title: string
    total?: number
    createAction?: { label: string; href: string }
    data: Array<Record<string, unknown>>
    columns: Array<{ header?: React.ReactNode }>
  }) => (
    <div>
      <h2>{title}</h2>
      {createAction ? <a href={createAction.href}>{createAction.label}</a> : null}
      <span>total:{total ?? data.length}</span>
      <div>
        {columns.map((column, index) => (
          <span key={index}>{String(column.header ?? '')}</span>
        ))}
      </div>
      {data.map((item, index) => (
        <div key={String(item.channelId ?? item.triggerId ?? index)}>
          <span>{String(item.channelName ?? item.triggerName ?? '')}</span>
          <span>{String(item.status ?? item.automationStatus ?? '')}</span>
        </div>
      ))}
    </div>
  ),
}))

vi.mock('@/lib/api/notification-channels', () => notificationChannelApiMocks)
vi.mock('@/lib/api/triggers', () => triggerApiMocks)

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

describe('automation pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSearchMock.mockReturnValue({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })

    notificationChannelApiMocks.listNotificationChannels.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          channelId: 'chn_001',
          channelName: '企业微信通知',
          channelType: 'WECHAT_WORK',
          endpoint: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send',
          status: 'ENABLED',
          createdAt: '2026-03-22T09:00:00+08:00',
        },
      ],
    })
    notificationChannelApiMocks.getNotificationChannelDetail.mockResolvedValue({
      channelId: 'chn_001',
      channelName: '企业微信通知',
      channelType: 'WECHAT_WORK',
      endpoint: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send',
      secret: 'sec_001',
      remark: '流程消息通知',
      status: 'ENABLED',
      createdAt: '2026-03-22T09:00:00+08:00',
      updatedAt: '2026-03-22T09:10:00+08:00',
    })
    notificationChannelApiMocks.getNotificationChannelDiagnostic.mockResolvedValue({
      channelId: 'chn_001',
      channelCode: 'wechat_ops',
      channelType: 'WECHAT_WORK',
      channelName: '企业微信通知',
      enabled: true,
      mockMode: false,
      configurationComplete: true,
      missingConfigFields: [],
      healthStatus: 'HEALTHY',
      lastSentAt: '2026-03-23T09:20:00+08:00',
      lastDispatchSuccess: true,
      lastDispatchStatus: 'SUCCESS',
      lastProviderName: 'WechatWorkNotificationProvider',
      lastResponseMessage: 'ok',
      lastDispatchAt: '2026-03-23T09:20:00+08:00',
      lastFailureAt: null,
      lastFailureMessage: null,
    })
    notificationChannelApiMocks.getNotificationChannelFormOptions.mockResolvedValue({
      channelTypes: [
        { value: 'EMAIL', label: '邮件' },
        { value: 'WEBHOOK', label: 'Webhook' },
      ],
    })
    notificationChannelApiMocks.createNotificationChannel.mockResolvedValue({
      channelId: 'chn_new',
    })
    notificationChannelApiMocks.updateNotificationChannel.mockResolvedValue({
      channelId: 'chn_new',
    })

    triggerApiMocks.listTriggers.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          triggerId: 'trg_001',
          triggerName: '请假审批完成通知',
          triggerKey: 'LEAVE_DONE_NOTIFY',
          triggerEvent: 'TASK_COMPLETED',
          automationStatus: 'ACTIVE',
          createdAt: '2026-03-22T09:00:00+08:00',
        },
      ],
    })
    triggerApiMocks.getTriggerDetail.mockResolvedValue({
      triggerId: 'trg_001',
      triggerName: '请假审批完成通知',
      triggerKey: 'LEAVE_DONE_NOTIFY',
      triggerEvent: 'TASK_COMPLETED',
      businessType: 'OA_LEAVE',
      channelIds: ['chn_001'],
      conditionExpression: 'status == "COMPLETED"',
      description: '审批完成后通知发起人',
      enabled: true,
      createdAt: '2026-03-22T09:00:00+08:00',
      updatedAt: '2026-03-22T09:10:00+08:00',
    })
    triggerApiMocks.getTriggerFormOptions.mockResolvedValue({
      triggerEvents: [
        { value: 'TASK_CREATED', label: '任务创建' },
        { value: 'TASK_COMPLETED', label: '任务完成' },
      ],
    })
    triggerApiMocks.createTrigger.mockResolvedValue({
      triggerId: 'trg_new',
    })
    triggerApiMocks.updateTrigger.mockResolvedValue({
      triggerId: 'trg_new',
    })
  })

  it('renders notification channels pages in Chinese and submits the create form', async () => {
    const {
      NotificationChannelCreatePage,
      NotificationChannelDetailPage,
      NotificationChannelsListPage,
    } = await import('./notification-channel-pages')

    renderWithQuery(<NotificationChannelsListPage />)

    await screen.findByText('通知渠道管理')
    await screen.findByText('企业微信通知')

    expect(await screen.findByText('通知渠道管理')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '新建通知渠道' })).toHaveAttribute(
      'href',
      '/system/notification-channels/create'
    )
    expect(screen.getByText('状态')).toBeInTheDocument()

    renderWithQuery(<NotificationChannelDetailPage channelId='chn_001' />)
    expect(await screen.findByText('通知渠道详情')).toBeInTheDocument()
    expect(screen.getByText('企业微信通知')).toBeInTheDocument()
    expect(await screen.findByText('渠道诊断')).toBeInTheDocument()
    expect(screen.getByText('HEALTHY')).toBeInTheDocument()
    expect(screen.getByText('WechatWorkNotificationProvider')).toBeInTheDocument()
    expect(screen.getByText('ok')).toBeInTheDocument()

    renderWithQuery(<NotificationChannelCreatePage />)
    fireEvent.change(await screen.findByLabelText('渠道名称'), {
      target: { value: '邮件通知' },
    })
    fireEvent.change(screen.getByLabelText('渠道类型'), {
      target: { value: 'EMAIL' },
    })
    fireEvent.change(screen.getByLabelText('通知地址'), {
      target: { value: 'ops@westflow.cn' },
    })
    fireEvent.click(screen.getByRole('button', { name: '保存并返回列表' }))

    await waitFor(() => {
      expect(notificationChannelApiMocks.createNotificationChannel).toHaveBeenCalled()
    })
  })

  it('renders trigger pages in Chinese and submits the create form', async () => {
    const { TriggerCreatePage, TriggerDetailPage, TriggersListPage } = await import(
      './trigger-pages'
    )

    renderWithQuery(<TriggersListPage />)

    await screen.findByText('触发器管理')
    await screen.findByText('请假审批完成通知')

    expect(await screen.findByText('触发器管理')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '新建触发器' })).toHaveAttribute(
      'href',
      '/system/triggers/create'
    )
    expect(screen.getByText('自动化状态')).toBeInTheDocument()

    renderWithQuery(<TriggerDetailPage triggerId='trg_001' />)
    expect(await screen.findByText('触发器详情')).toBeInTheDocument()
    expect(screen.getByText('请假审批完成通知')).toBeInTheDocument()

    renderWithQuery(<TriggerCreatePage />)
    fireEvent.change(await screen.findByLabelText('触发器名称'), {
      target: { value: '报销通知' },
    })
    fireEvent.change(screen.getByLabelText('触发器编码'), {
      target: { value: 'EXPENSE_DONE_NOTIFY' },
    })
    fireEvent.change(screen.getByLabelText('触发事件'), {
      target: { value: 'TASK_COMPLETED' },
    })
    fireEvent.click(screen.getByRole('button', { name: '保存并返回列表' }))

    await waitFor(() => {
      expect(triggerApiMocks.createTrigger).toHaveBeenCalled()
    })
  })
})
