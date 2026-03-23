import { Children } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NotificationChannelDetailPage } from './notification-channel-pages'

const {
  navigateMock,
  useSearchMock,
  routeNavigateMock,
  notificationChannelApiMocks,
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
      {actions ? <div>{Children.toArray(actions)}</div> : null}
      {children}
    </section>
  ),
}))

vi.mock('@/lib/api/notification-channels', () => notificationChannelApiMocks)
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

  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('notification channel pages smoke', () => {
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
  })

  it('shows the diagnostic card on the notification channel detail page', async () => {
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

    renderWithQuery(<NotificationChannelDetailPage channelId='chn_001' />)

    await waitFor(() => {
      expect(notificationChannelApiMocks.getNotificationChannelDetail).toHaveBeenCalledWith('chn_001')
      expect(notificationChannelApiMocks.getNotificationChannelDiagnostic).toHaveBeenCalledWith('chn_001')
    })

    expect(await screen.findByText('企业微信通知')).toBeInTheDocument()
    expect(screen.getByText('通知渠道详情')).toBeInTheDocument()
    expect(screen.getByText('渠道诊断')).toBeInTheDocument()
    expect(screen.getByText('HEALTHY')).toBeInTheDocument()
    expect(screen.getByText('WechatWorkNotificationProvider')).toBeInTheDocument()
    expect(screen.getByText('ok')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '查看健康监控详情' })).toHaveAttribute(
      'href',
      '/system/monitor/notification-channels/health/$channelId'
    )
  })
})
