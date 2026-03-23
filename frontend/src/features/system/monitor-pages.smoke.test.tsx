import { Children } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  SystemMonitorNotificationChannelHealthDetailPage,
  SystemMonitorNotificationChannelHealthListPage,
} from './monitor-pages'

const {
  navigateMock,
  useSearchMock,
  systemMonitorApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  useSearchMock: vi.fn(),
  systemMonitorApiMocks: {
    listNotificationChannelHealths: vi.fn(),
    getNotificationChannelHealthDetail: vi.fn(),
    listOrchestratorScans: vi.fn(),
    getOrchestratorScanDetail: vi.fn(),
    listTriggerExecutions: vi.fn(),
    getTriggerExecutionDetail: vi.fn(),
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
  getRouteApi: (path: string) => ({
    useSearch: useSearchMock,
    useNavigate: () => navigateMock,
    useParams: () =>
      path.includes('$channelId')
        ? { channelId: 'chn_001' }
        : { executionId: 'exec_001' },
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

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: ({
    title,
    total,
    data,
  }: {
    title: string
    total?: number
    data: Array<Record<string, unknown>>
  }) => (
    <section>
      <h2>{title}</h2>
      <span>total:{total ?? data.length}</span>
      {data.map((item) => (
        <div key={String(item.channelId ?? item.executionId ?? item.runId)}>
          <span>{String(item.channelName ?? '')}</span>
          <span>{String(item.channelCode ?? '')}</span>
          <span>{String(item.latestStatus ?? '')}</span>
        </div>
      ))}
    </section>
  ),
}))

vi.mock('@/lib/api/system-monitor', () => systemMonitorApiMocks)

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('monitor pages smoke', () => {
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

  it('shows notification channel health list visibility', async () => {
    systemMonitorApiMocks.listNotificationChannelHealths.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          channelId: 'chn_001',
          channelCode: 'wechat_ops',
          channelName: '企业微信通知',
          channelType: 'WECHAT_WORK',
          latestStatus: 'SUCCESS',
          successRate: 98.5,
          lastSentAt: '2026-03-23T10:00:00+08:00',
          updatedAt: '2026-03-23T10:05:00+08:00',
        },
      ],
    })

    renderWithQuery(<SystemMonitorNotificationChannelHealthListPage />)

    expect(await screen.findByText('通知渠道健康监控')).toBeInTheDocument()
    expect(await screen.findByText('企业微信通知')).toBeInTheDocument()
    expect(screen.getByText('wechat_ops')).toBeInTheDocument()
    expect(screen.getByText('total:1')).toBeInTheDocument()
  })

  it('shows notification channel health detail visibility', async () => {
    systemMonitorApiMocks.getNotificationChannelHealthDetail.mockResolvedValue({
      channelId: 'chn_001',
      channelCode: 'wechat_ops',
      channelName: '企业微信通知',
      channelType: 'WECHAT_WORK',
      status: 'ACTIVE',
      enabled: true,
      latestStatus: 'SUCCESS',
      lastSentAt: '2026-03-23T10:00:00+08:00',
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T10:05:00+08:00',
      channelEndpoint: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send',
      latestResponseMessage: 'ok',
      totalAttempts: 12,
      successAttempts: 11,
      failedAttempts: 1,
      successRate: 91.67,
      remark: '企业微信主通道',
    })

    renderWithQuery(<SystemMonitorNotificationChannelHealthDetailPage />)

    await waitFor(() => {
      expect(systemMonitorApiMocks.getNotificationChannelHealthDetail).toHaveBeenCalledWith('chn_001')
    })

    expect(await screen.findByText('企业微信通知')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '渠道健康详情' })).toBeInTheDocument()
    expect(screen.getByText('wechat_ops')).toBeInTheDocument()
    expect(screen.getByText('https://qyapi.weixin.qq.com/cgi-bin/webhook/send')).toBeInTheDocument()
    expect(screen.getByText('91.67%')).toBeInTheDocument()
    expect(screen.getByText('ok')).toBeInTheDocument()
  })
})
