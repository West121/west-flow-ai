import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { WorkflowDefinitionsListPage } from './pages'

const { listProcessDefinitionsMock } = vi.hoisted(() => ({
  listProcessDefinitionsMock: vi.fn(),
}))

let latestResourceListPageProps: Record<string, unknown> | null = null

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a {...props}>{children}</a>
  ),
  getRouteApi: () => ({
    useSearch: () => ({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    }),
    useNavigate: () => vi.fn(),
  }),
}))

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: (props: Record<string, unknown>) => {
    latestResourceListPageProps = props
    return <div data-testid='resource-list-page' />
  },
}))

vi.mock('@/lib/api/workflow', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api/workflow')>()

  return {
    ...actual,
    listProcessDefinitions: listProcessDefinitionsMock,
  }
})

describe('WorkflowDefinitionsListPage', () => {
  beforeEach(() => {
    latestResourceListPageProps = null
    listProcessDefinitionsMock.mockReset()
    listProcessDefinitionsMock.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 20,
      pages: 1,
      records: [
        {
          processDefinitionId: 'oa_collaboration_modes_e2e_1774776497433:1',
          processKey: 'oa_collaboration_modes_e2e_1774776497433',
          processName: '协同审批模式前端E2E-1774776497433',
          category: 'OA',
          version: 1,
          status: 'PUBLISHED',
          createdAt: '2026-03-29T21:37:07+08:00',
        },
      ],
      groups: [],
    })
  })

  it('passes server total to the shared list shell and truncates long workflow text', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <WorkflowDefinitionsListPage />
      </QueryClientProvider>
    )

    await waitFor(() => {
      expect(latestResourceListPageProps?.total).toBe(20)
    })

    const columns = latestResourceListPageProps?.columns as Array<{
      cell?: (context: {
        row: {
          original: {
            processDefinitionId: string
            processKey: string
            processName: string
            category: string
          }
        }
      }) => React.ReactElement
    }>

    const processNameCell = columns?.[0]?.cell?.({
      row: {
        original: {
          processDefinitionId: 'oa_collaboration_modes_e2e_1774776497433:1',
          processKey: 'oa_collaboration_modes_e2e_1774776497433',
          processName: '协同审批模式前端E2E-1774776497433',
          category: 'OA',
        },
      },
    })
    const processKeyCell = columns?.[1]?.cell?.({
      row: {
        original: {
          processDefinitionId: 'oa_collaboration_modes_e2e_1774776497433:1',
          processKey: 'oa_collaboration_modes_e2e_1774776497433',
          processName: '协同审批模式前端E2E-1774776497433',
          category: 'OA',
        },
      },
    })
    const createdAtCell = columns?.[4]?.cell?.({
      row: {
        original: {
          processDefinitionId: 'oa_collaboration_modes_e2e_1774776497433:1',
          processKey: 'oa_collaboration_modes_e2e_1774776497433',
          processName: '协同审批模式前端E2E-1774776497433',
          category: 'OA',
          createdAt: '2026-03-29T21:37:07+08:00',
        },
      },
    })

    if (!processNameCell || !processKeyCell || !createdAtCell) {
      throw new Error('workflow definition cells were not created')
    }

    const { container } = render(
      <div>
        {processNameCell}
        {processKeyCell}
        {createdAtCell}
      </div>
    )

    const wrappers = Array.from(container.querySelectorAll('div.truncate.max-w-full'))
    expect(wrappers).toHaveLength(4)
    expect(wrappers.some((element) => element.textContent?.includes('协同审批模式前端E2E-1774776497433'))).toBe(
      true
    )
    expect(
      wrappers.some((element) => element.textContent?.includes('oa_collaboration_modes_e2e_1774776497433'))
    ).toBe(true)
    expect(
      wrappers.some((element) =>
        element.textContent?.includes(
          new Intl.DateTimeFormat('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false,
          }).format(new Date('2026-03-29T21:37:07+08:00'))
        )
      )
    ).toBe(true)
  })
})
