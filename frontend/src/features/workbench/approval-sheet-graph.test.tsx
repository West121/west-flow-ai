import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { vi, describe, it, expect } from 'vitest'
import type {
  WorkbenchFlowEdge,
  WorkbenchFlowNode,
  WorkbenchProcessInstanceEvent,
  WorkbenchTaskTraceItem,
} from '@/lib/api/workbench'
import { ApprovalSheetGraph } from './approval-sheet-graph'

vi.mock('@xyflow/react', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@xyflow/react')>()
  return {
    ...actual,
    Background: () => <div data-testid='reactflow-background' />,
    Controls: () => <div data-testid='reactflow-controls' />,
    Handle: () => <div data-testid='reactflow-handle' />,
    ReactFlow: ({ children }: { children?: React.ReactNode }) => (
      <div data-testid='reactflow-canvas'>{children}</div>
    ),
    ReactFlowProvider: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
  }
})

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

describe('ApprovalSheetGraph', () => {
  it('renders review graph with readonly ticket detail data', () => {
    const flowNodes: WorkbenchFlowNode[] = [
      {
        id: 'start_1',
        type: 'start',
        name: '开始',
        position: { x: 253, y: 32 },
        config: {},
        ui: { width: 240, height: 88 },
      },
      {
        id: 'approver_1',
        type: 'approver',
        name: '部门经理审批',
        position: { x: 253, y: 204 },
        config: {},
        ui: { width: 240, height: 88 },
      },
      {
        id: 'end_1',
        type: 'end',
        name: '结束',
        position: { x: 253, y: 376 },
        config: {},
        ui: { width: 240, height: 88 },
      },
    ]

    const flowEdges: WorkbenchFlowEdge[] = [
      { id: 'edge_1', source: 'start_1', target: 'approver_1' },
      { id: 'edge_2', source: 'approver_1', target: 'end_1' },
    ]

    const taskTrace: WorkbenchTaskTraceItem[] = [
      {
        taskId: 'task_1',
        nodeId: 'approver_1',
        nodeName: '部门经理审批',
        status: 'PENDING',
        candidateUserIds: ['usr_002'],
        candidateGroupIds: [],
        receiveTime: '2026-04-03T09:00:00Z',
      },
    ]

    const instanceEvents: WorkbenchProcessInstanceEvent[] = [
      {
        eventId: 'evt_start',
        instanceId: 'ins_1',
        eventType: 'START_PROCESS',
        eventName: '发起流程',
        occurredAt: '2026-04-03T08:59:00Z',
      },
    ]

    renderWithQuery(
      <ApprovalSheetGraph
        flowNodes={flowNodes}
        flowEdges={flowEdges}
        taskTrace={taskTrace}
        instanceEvents={instanceEvents}
        instanceStatus='RUNNING'
        userDisplayNames={{ usr_002: '李四' }}
      />
    )

    expect(screen.getByText('流程图回顾')).toBeInTheDocument()
    expect(screen.getByTestId('reactflow-canvas')).toBeInTheDocument()
    expect(screen.getByText('部门经理审批')).toBeInTheDocument()
  })
})
