import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { WorkflowDesignerPage } from './pages'
import { useWorkflowDesignerStore } from './designer/store'

const { navigateMock, fitViewMock, setViewportMock, reactFlowMock } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  fitViewMock: vi.fn(),
  setViewportMock: vi.fn(),
  reactFlowMock: {
    fitView: undefined as unknown,
    setViewport: undefined as unknown,
    toObject: () => ({ nodes: [], edges: [] }),
    screenToFlowPosition: ({ x, y }: { x: number; y: number }) => ({ x, y }),
  },
}))

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
    useSearch: () => ({}),
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

vi.mock('@/features/forms/runtime/form-component-registry', () => ({
  findProcessRuntimeFormByProcessKey: () => ({
    formKey: 'oa-leave-form',
    formVersion: '1.0.0',
  }),
}))

vi.mock('./designer/form-selection', () => ({
  ProcessFormSelector: ({ label }: { label: string }) => (
    <div>{label}</div>
  ),
}))

vi.mock('./designer/node-config-panel', () => ({
  NodeConfigPanel: ({ node }: { node: { id: string } | null }) => (
    <div>{node ? '节点属性面板' : '未选中节点'}</div>
  ),
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

afterEach(() => {
  navigateMock.mockClear()
  fitViewMock.mockClear()
  setViewportMock.mockClear()
  reactFlowMock.fitView = fitViewMock
  reactFlowMock.setViewport = setViewportMock
  window.sessionStorage.clear()
  useWorkflowDesignerStore.getState().resetDesigner()
})

describe('workflow designer page', () => {
  it('renders three-column layout with tabs and builtin controls only', async () => {
    renderWithQuery(<WorkflowDesignerPage />)

    expect(screen.getByTestId('workflow-designer-layout')).toBeInTheDocument()
    expect(screen.getByText('节点面板')).toBeInTheDocument()
    expect(screen.getByText('高级结构')).toBeInTheDocument()
    expect(screen.getByText('子流程')).toBeInTheDocument()
    expect(screen.getByText('主子流程模板')).toBeInTheDocument()
    expect(screen.getByText('动态构建模板')).toBeInTheDocument()
    expect(screen.getByText('包容分支模板')).toBeInTheDocument()
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

    const nodeTab = screen.getByRole('tab', { name: '节点属性' })
    fireEvent.mouseDown(nodeTab)
    fireEvent.click(nodeTab)

    expect(screen.getByTestId('workflow-designer-node-panel')).toBeInTheDocument()
  })
})
