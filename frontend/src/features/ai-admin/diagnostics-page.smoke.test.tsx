import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AiMcpDiagnosticsPage } from './diagnostics-page'

const { aiAdminApiMocks, navigateMock } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  aiAdminApiMocks: {
    listAiMcpDiagnostics: vi.fn(),
  },
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
      <h1>{title}</h1>
      <span>total:{total ?? data.length}</span>
      {data.map((item) => (
        <div key={String(item.mcpCode ?? item.mcpName)}>
          <span>{String(item.mcpCode ?? '')}</span>
          <span>{String(item.mcpName ?? '')}</span>
          <span>{String(item.connectionStatus ?? '')}</span>
          <span>{String(item.failureReason ?? '')}</span>
        </div>
      ))}
    </section>
  ),
}))

vi.mock('@/lib/api/ai-admin', () => aiAdminApiMocks)

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('ai admin diagnostics smoke', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows MCP diagnostic page basics', async () => {
    const search = {
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    }

    aiAdminApiMocks.listAiMcpDiagnostics.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          mcpCode: 'workflow',
          mcpName: '流程中心 MCP',
          transportType: 'STREAMABLE_HTTP',
          connectionStatus: 'DOWN',
          toolCount: 12,
          responseTimeMillis: 1820,
          failureStage: 'CONNECT',
          failureReason: '远端 MCP 请求超时',
          observability: {
            totalToolCalls: 23,
            latestFailureReason: '最近一次 MCP 超时',
          },
          checkedAt: '2026-03-23T10:10:00+08:00',
        },
      ],
    })

    renderWithQuery(<AiMcpDiagnosticsPage search={search} navigate={navigateMock} />)

    await waitFor(() => {
      expect(aiAdminApiMocks.listAiMcpDiagnostics).toHaveBeenCalledWith(search)
    })

    expect(await screen.findByText('MCP 连通性诊断')).toBeInTheDocument()
    expect(screen.getByText('workflow')).toBeInTheDocument()
    expect(screen.getByText('流程中心 MCP')).toBeInTheDocument()
    expect(screen.getByText('DOWN')).toBeInTheDocument()
    expect(screen.getByText('远端 MCP 请求超时')).toBeInTheDocument()
    expect(screen.getByText('total:1')).toBeInTheDocument()
  })
})
