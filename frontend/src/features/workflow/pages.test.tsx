import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { WorkflowDesignerPage } from './pages'
import { useWorkflowDesignerStore } from './designer/store'

const { navigateMock, fitViewMock, setViewportMock, reactFlowMock, routeSearchMock } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  fitViewMock: vi.fn(),
  setViewportMock: vi.fn(),
  routeSearchMock: {} as { processDefinitionId?: string; mode?: 'edit' | 'view' },
  reactFlowMock: {
    fitView: undefined as unknown,
    setViewport: undefined as unknown,
    toObject: () => ({ nodes: [], edges: [] }),
    screenToFlowPosition: ({ x, y }: { x: number; y: number }) => ({ x, y }),
  },
}))
const {
  saveProcessDefinitionMock,
  publishProcessDefinitionMock,
  getProcessDefinitionDetailMock,
  listProcessDefinitionsMock,
} = vi.hoisted(() => ({
  saveProcessDefinitionMock: vi.fn(),
  publishProcessDefinitionMock: vi.fn(),
  getProcessDefinitionDetailMock: vi.fn(),
  listProcessDefinitionsMock: vi.fn(),
}))
// eslint-disable-next-line no-console
const originalConsoleError = console.error
const consoleErrorSpy = vi.spyOn(console, 'error')

reactFlowMock.fitView = fitViewMock
reactFlowMock.setViewport = setViewportMock

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a {...props}>{children}</a>
  ),
  getRouteApi: () => ({
    useSearch: () => routeSearchMock,
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
    description: string
    actions?: React.ReactNode
    children: React.ReactNode
  }) => (
    <div>
      <h1>{title}</h1>
      <p>{description}</p>
      <div data-testid='page-actions'>{actions}</div>
      {children}
    </div>
  ),
}))

vi.mock('@/features/forms/runtime/form-component-registry', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@/features/forms/runtime/form-component-registry')>()

  return {
    ...actual,
    findProcessRuntimeFormByProcessKey: () => ({
      formKey: 'oa-leave-form',
      formVersion: '1.0.0',
    }),
  }
})

vi.mock('./designer/node-config-panel', () => ({
  NodeConfigPanel: ({ node }: { node: { id: string } | null }) => (
    <div>{node ? '节点属性面板' : ''}</div>
  ),
}))

vi.mock('@/components/ui/scroll-area', () => ({
  ScrollArea: ({
    children,
    className,
  }: {
    children?: React.ReactNode
    className?: string
  }) => <div className={className}>{children}</div>,
  ScrollBar: () => null,
}))

vi.mock('./designer-collab/provider', () => ({
  createWorkflowDesignerCollaborationProvider: () => {
    const awareness = {
      getStates: () => new Map(),
      on: () => undefined,
      off: () => undefined,
    }

    return {
      awareness,
      mode: 'broadcast',
      status: 'local',
      setLocalState: () => undefined,
      onStatusChange: () => () => undefined,
      reconnect: () => undefined,
      destroy: () => undefined,
    }
  },
}))

vi.mock('./designer-collab/bindings', () => ({
  createWorkflowDesignerCollaborationBinding: () => ({
    syncLocalState: () => undefined,
    destroy: () => undefined,
  }),
}))

vi.mock('./designer-collab/awareness', () => ({
  resolveWorkflowDesignerPeers: () => [],
  resolveWorkflowDesignerPeerColor: () => '#3b82f6',
}))

vi.mock('./designer-collab/ydoc', () => ({
  createWorkflowDesignerYDoc: () => ({
    destroy: () => undefined,
  }),
}))

vi.mock('@/lib/api/workflow', () => ({
  listProcessDefinitions: (...args: unknown[]) => listProcessDefinitionsMock(...args),
  getProcessDefinitionDetail: (...args: unknown[]) => getProcessDefinitionDetailMock(...args),
  saveProcessDefinition: (...args: unknown[]) => saveProcessDefinitionMock(...args),
  publishProcessDefinition: (...args: unknown[]) => publishProcessDefinitionMock(...args),
}))

vi.mock('@xyflow/react', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@xyflow/react')>()

  return {
    ...actual,
    Background: ({ children }: { children?: React.ReactNode }) => (
      <div data-testid='reactflow-background'>{children}</div>
    ),
    Controls: () => <div data-testid='reactflow-controls'>Controls</div>,
    MiniMap: () => <div data-testid='reactflow-minimap'>MiniMap</div>,
    Panel: ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
    ReactFlow: ({ children }: { children?: React.ReactNode }) => (
      <div data-testid='reactflow-canvas'>{children}</div>
    ),
    ReactFlowProvider: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
    useReactFlow: () => reactFlowMock,
    useViewport: () => ({ zoom: 1, x: 0, y: 0 }),
  }
})

async function renderWithQuery(ui: React.ReactNode) {
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

  let renderResult: ReturnType<typeof render> | undefined

  await act(async () => {
    renderResult = render(
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    )
    await Promise.resolve()
  })

  return renderResult!
}

beforeAll(() => {
  consoleErrorSpy.mockImplementation((message?: unknown, ...rest: unknown[]) => {
    if (
      typeof message === 'string' &&
      message.includes('not wrapped in act')
    ) {
      return
    }
    Reflect.apply(originalConsoleError, console, [message, ...rest])
  })
})

afterEach(() => {
  routeSearchMock.processDefinitionId = undefined
  routeSearchMock.mode = undefined
  navigateMock.mockClear()
  fitViewMock.mockClear()
  setViewportMock.mockClear()
  reactFlowMock.fitView = fitViewMock
  reactFlowMock.setViewport = setViewportMock
  window.sessionStorage.clear()
  useWorkflowDesignerStore.getState().resetDesigner()
  saveProcessDefinitionMock.mockReset()
  publishProcessDefinitionMock.mockReset()
  getProcessDefinitionDetailMock.mockReset()
  listProcessDefinitionsMock.mockReset()
})

afterAll(() => {
  consoleErrorSpy.mockRestore()
})

describe('workflow designer page', () => {
  it('renders three-column layout with tabs and builtin controls only', async () => {
    await renderWithQuery(<WorkflowDesignerPage />)

    expect(screen.getByTestId('workflow-designer-layout')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '返回流程定义' })).toBeInTheDocument()
    expect(screen.getByText('节点面板')).toBeInTheDocument()
    expect(screen.getByText('节点')).toBeInTheDocument()
    expect(screen.getAllByText('子流程').length).toBeGreaterThan(0)
    expect(screen.getByText('主子流程模板')).toBeInTheDocument()
    expect(screen.getByText('动态构建模板')).toBeInTheDocument()
    expect(screen.getByText('包容分支模板')).toBeInTheDocument()
    expect(screen.getAllByText('模板').length).toBeGreaterThan(0)
    expect(screen.getByText('属性面板')).toBeInTheDocument()
    expect(
      screen.queryByText('从左侧拖入节点，双击节点模板可快速追加')
    ).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '流程属性' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '节点属性' })).toBeInTheDocument()
    expect(screen.getByTestId('reactflow-controls')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '适配视图' })
    ).not.toBeInTheDocument()
    expect(screen.queryByText('当前节点数')).not.toBeInTheDocument()
    expect(screen.queryByText('当前连线数')).not.toBeInTheDocument()

    const nodeTab = screen.getByRole('tab', { name: '节点属性' })
    fireEvent.mouseDown(nodeTab)
    fireEvent.click(nodeTab)

    expect(screen.getByTestId('workflow-designer-node-panel')).toBeInTheDocument()
  })

  it('saves the latest auto-layout positions instead of stale snapshot coordinates', async () => {
    saveProcessDefinitionMock.mockImplementation(async (payload) => ({
      processDefinitionId: 'pd_001',
      processKey: payload.processKey,
      processName: payload.processName,
      category: payload.category,
      version: 1,
      status: 'DRAFT',
      createdAt: '2026-03-24T00:00:00Z',
      dsl: payload,
    }))

    await renderWithQuery(<WorkflowDesignerPage />)

    const beforeLayout = useWorkflowDesignerStore.getState().history.present.nodes.map((node) => ({
      id: node.id,
      x: node.position.x,
      y: node.position.y,
    }))

    fireEvent.click(screen.getByRole('button', { name: '自动整理' }))
    fireEvent.click(screen.getByRole('button', { name: '保存草稿' }))

    await waitFor(() => {
      expect(saveProcessDefinitionMock).toHaveBeenCalledTimes(1)
    })

    const savedDsl = saveProcessDefinitionMock.mock.calls[0][0]
    const afterLayout = useWorkflowDesignerStore.getState().history.present.nodes.map((node) => ({
      id: node.id,
      x: node.position.x,
      y: node.position.y,
    }))

    expect(afterLayout).not.toEqual(beforeLayout)
    expect(savedDsl.nodes.map((node: { id: string; position: { x: number; y: number } }) => ({
      id: node.id,
      x: node.position.x,
      y: node.position.y,
    }))).toEqual(afterLayout)
  })

  it('hydrates an existing definition from server detail instead of reusing the leave template', async () => {
    routeSearchMock.processDefinitionId = 'plm_ecr:draft'
    getProcessDefinitionDetailMock.mockResolvedValue({
      processDefinitionId: 'plm_ecr:draft',
      processKey: 'plm_ecr',
      processName: 'ECR 变更申请',
      category: 'PLM',
      version: 0,
      status: 'DRAFT',
      createdAt: '2026-03-24T00:00:00Z',
      updatedAt: '2026-03-24T00:00:00Z',
      bpmnXml: '',
      dsl: {
        dslVersion: '1.0.0',
        processKey: 'plm_ecr',
        processName: 'ECR 变更申请',
        category: 'PLM',
        processFormKey: 'plm-ecr-start-form',
        processFormVersion: '1.0.0',
        formFields: [],
        settings: {
          allowWithdraw: true,
          allowUrge: true,
          allowTransfer: true,
        },
        nodes: [
          {
            id: 'start_1',
            type: 'start',
            name: '开始',
            description: '开始',
            position: { x: 100, y: 100 },
            config: { initiatorEditable: true },
            ui: { width: 220, height: 96 },
          },
          {
            id: 'approve_manager',
            type: 'approver',
            name: 'PLM 负责人审批',
            description: 'PLM 负责人审批',
            position: { x: 320, y: 100 },
            config: {
              assignment: {
                mode: 'USER',
                userIds: ['usr_002'],
                roleCodes: [],
                departmentRef: '',
                formFieldKey: '',
                formulaExpression: '',
              },
            },
            ui: { width: 220, height: 96 },
          },
          {
            id: 'end_1',
            type: 'end',
            name: '结束',
            description: '结束',
            position: { x: 540, y: 100 },
            config: {},
            ui: { width: 220, height: 96 },
          },
        ],
        edges: [
          { id: 'edge_1', source: 'start_1', target: 'approve_manager', priority: 10, label: '提交' },
          { id: 'edge_2', source: 'approve_manager', target: 'end_1', priority: 20, label: '通过' },
        ],
      },
    })

    await renderWithQuery(<WorkflowDesignerPage />)

    await waitFor(() => {
      expect(getProcessDefinitionDetailMock).toHaveBeenCalledWith('plm_ecr:draft')
    })

    await waitFor(() => {
      const snapshot = useWorkflowDesignerStore.getState().history.present
      expect(snapshot.nodes).toHaveLength(3)
      expect(snapshot.nodes[0]?.data.label).toBe('开始')
    })

    expect(screen.getByDisplayValue('plm_ecr')).toBeInTheDocument()
    expect(screen.getByDisplayValue('ECR 变更申请')).toBeInTheDocument()
    expect(screen.getByDisplayValue('PLM')).toBeInTheDocument()
  })

  it('hides editing actions in readonly spectator mode', async () => {
    routeSearchMock.mode = 'view'

    await renderWithQuery(<WorkflowDesignerPage />)

    expect(screen.getByText('只读观摩')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存草稿' })).toHaveClass('hidden')
    expect(screen.getByRole('button', { name: '发布流程' })).toHaveClass('hidden')
    expect(screen.getByRole('button', { name: '自动整理' })).toHaveClass('hidden')
    expect(screen.getByRole('button', { name: '撤销' })).toHaveClass('hidden')
  })
})
