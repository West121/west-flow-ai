import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SystemMonitorNotificationChannelHealthDetailPage } from './monitor-pages'

const {
  navigateMock,
  routeSearchMock,
  routeParamsMock,
  systemMonitorApiMocks,
  toastSuccessMock,
  toastErrorMock,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  routeSearchMock: vi.fn(),
  routeParamsMock: vi.fn(),
  systemMonitorApiMocks: {
    getNotificationChannelHealthDetail: vi.fn(),
    listNotificationChannelHealths: vi.fn(),
    getOrchestratorScanDetail: vi.fn(),
    listOrchestratorScans: vi.fn(),
    getTriggerExecutionDetail: vi.fn(),
    listTriggerExecutions: vi.fn(),
    recheckNotificationChannelHealth: vi.fn(),
  },
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
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
  getRouteApi: () => ({
    useSearch: routeSearchMock,
    useNavigate: () => navigateMock,
    useParams: routeParamsMock,
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
    description: string
    actions?: React.ReactNode
    children: React.ReactNode
  }) => (
    <div>
      <h1>{title}</h1>
      <p>{description}</p>
      {actions ? <div>{actions}</div> : null}
      {children}
    </div>
  ),
}))

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: ({
    title,
    total,
  }: {
    title: string
    total?: number
  }) => (
    <div>
      <h2>{title}</h2>
      <span>total:{total ?? 0}</span>
    </div>
  ),
}))

vi.mock('sonner', () => ({
  toast: {
    success: toastSuccessMock,
    error: toastErrorMock,
  },
}))

vi.mock('@/lib/api/system-monitor', () => systemMonitorApiMocks)

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

function createChannelHealthDetail(overrides: Record<string, unknown> = {}) {
  return {
    channelId: 'chn_001',
    channelCode: 'wechat_ops',
    channelName: '企业微信运维通道',
    channelType: 'WECHAT',
    status: 'HEALTHY',
    latestStatus: 'SUCCESS',
    totalAttempts: 12,
    successAttempts: 11,
    failedAttempts: 1,
    successRate: 91.7,
    lastSentAt: '2026-03-23T10:05:00+08:00',
    latestResponseMessage: '最近一次回执正常',
    enabled: true,
    createdAt: '2026-03-22T09:00:00+08:00',
    updatedAt: '2026-03-23T10:06:00+08:00',
    remark: '用于运维告警',
    channelEndpoint: 'https://notify.example.com',
    ...overrides,
  }
}

describe('system monitor notification health detail page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeSearchMock.mockReturnValue({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })
    routeParamsMock.mockReturnValue({ channelId: 'chn_001' })
  })

  it('rechecks notification channel health and refreshes detail on success', async () => {
    const initialDetail = createChannelHealthDetail({
      latestStatus: 'FAILED',
      latestResponseMessage: '旧回执：连接超时',
    })
    const refreshedDetail = createChannelHealthDetail({
      latestStatus: 'SUCCESS',
      latestResponseMessage: '重检成功，通道恢复正常',
      updatedAt: '2026-03-23T10:10:00+08:00',
    })

    systemMonitorApiMocks.getNotificationChannelHealthDetail
      .mockResolvedValueOnce(initialDetail)
      .mockResolvedValueOnce(refreshedDetail)
    systemMonitorApiMocks.recheckNotificationChannelHealth.mockResolvedValue(
      refreshedDetail
    )

    renderWithQuery(<SystemMonitorNotificationChannelHealthDetailPage />)

    expect(await screen.findByText('旧回执：连接超时')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '重检渠道健康' }))

    await waitFor(() => {
      expect(
        systemMonitorApiMocks.recheckNotificationChannelHealth
      ).toHaveBeenCalledWith('chn_001')
    })
    await waitFor(() => {
      expect(toastSuccessMock).toHaveBeenCalledWith('渠道健康已重新检查')
    })
    await waitFor(() => {
      expect(
        systemMonitorApiMocks.getNotificationChannelHealthDetail
      ).toHaveBeenCalledTimes(2)
    })
    await waitFor(() => {
      expect(screen.getByText('重检成功，通道恢复正常')).toBeInTheDocument()
    })
  })

  it('shows failure feedback and refreshes detail after recheck failure', async () => {
    const initialDetail = createChannelHealthDetail({
      latestStatus: 'FAILED',
      latestResponseMessage: '旧回执：网关超时',
    })
    const refreshedDetail = createChannelHealthDetail({
      latestStatus: 'FAILED',
      latestResponseMessage: '最近失败：上游网关仍未恢复',
      updatedAt: '2026-03-23T10:12:00+08:00',
    })

    systemMonitorApiMocks.getNotificationChannelHealthDetail
      .mockResolvedValueOnce(initialDetail)
      .mockResolvedValueOnce(refreshedDetail)
    systemMonitorApiMocks.recheckNotificationChannelHealth.mockRejectedValue(
      new Error('重检请求失败，请稍后重试。')
    )

    renderWithQuery(<SystemMonitorNotificationChannelHealthDetailPage />)

    expect(await screen.findByText('旧回执：网关超时')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '重检渠道健康' }))

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalledWith('重检请求失败，请稍后重试。')
    })
    await waitFor(() => {
      expect(
        systemMonitorApiMocks.getNotificationChannelHealthDetail
      ).toHaveBeenCalledTimes(2)
    })
    await waitFor(() => {
      expect(screen.getByText('最近失败：上游网关仍未恢复')).toBeInTheDocument()
    })
  })
})
