import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { WorkbenchTodoDetailPage, WorkbenchTodoListPage } from './pages'

const {
  navigateMock,
  useSearchMock,
  routeNavigateMock,
  workbenchApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  useSearchMock: vi.fn(),
  routeNavigateMock: vi.fn(),
  workbenchApiMocks: {
    listWorkbenchTasks: vi.fn(),
    getWorkbenchTaskDetail: vi.fn(),
    getWorkbenchTaskActions: vi.fn(),
    startWorkbenchProcess: vi.fn(),
    completeWorkbenchTask: vi.fn(),
    claimWorkbenchTask: vi.fn(),
    transferWorkbenchTask: vi.fn(),
    returnWorkbenchTask: vi.fn(),
  },
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => <a {...props}>{children}</a>,
  useNavigate: () => navigateMock,
  getRouteApi: () => ({
    useSearch: useSearchMock,
    useNavigate: () => routeNavigateMock,
  }),
}))

vi.mock('@/features/shared/page-shell', () => ({
  PageShell: ({
    title,
    children,
  }: {
    title: string
    children: React.ReactNode
  }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}))

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: ({
    data,
  }: {
    data: Array<{ taskId: string; status: string; processName?: string }>
  }) => (
    <div>
      {data.map((item) => (
        <div key={item.taskId}>
          <span>{item.processName}</span>
          <span>
            {item.status === 'PENDING_CLAIM'
              ? '待认领'
              : item.status === 'PENDING'
                ? '待处理'
                : item.status}
          </span>
        </div>
      ))}
    </div>
  ),
}))

vi.mock('@/lib/api/workbench', () => workbenchApiMocks)

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

describe('workbench pages', () => {
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
    workbenchApiMocks.listWorkbenchTasks.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          taskId: 'task_claim_001',
          instanceId: 'pi_001',
          processDefinitionId: 'pd_001',
          processKey: 'oa_leave',
          processName: '公共认领请假审批',
          businessKey: 'biz_001',
          applicantUserId: 'usr_001',
          nodeId: 'approve_manager',
          nodeName: '共享审批池',
          status: 'PENDING_CLAIM',
          assignmentMode: 'USER',
          candidateUserIds: ['usr_001', 'usr_002'],
          assigneeUserId: null,
          createdAt: '2026-03-22T09:00:00+08:00',
          updatedAt: '2026-03-22T09:00:00+08:00',
          completedAt: null,
        },
      ],
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders public tasks as 待认领 in the todo list', async () => {
    renderWithQuery(<WorkbenchTodoListPage />)

    expect(await screen.findByText('公共认领请假审批')).toBeInTheDocument()
    expect(screen.getByText('待认领')).toBeInTheDocument()
  })

  it('shows claim and return actions conditionally in the task detail page', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue({
      taskId: 'task_claim_001',
      instanceId: 'pi_001',
      processDefinitionId: 'pd_001',
      processKey: 'oa_leave',
      processName: '公共认领请假审批',
      businessKey: 'biz_001',
      applicantUserId: 'usr_001',
      nodeId: 'approve_manager',
      nodeName: '共享审批池',
      status: 'PENDING_CLAIM',
      assignmentMode: 'USER',
      candidateUserIds: ['usr_001', 'usr_002'],
      assigneeUserId: null,
      action: null,
      operatorUserId: null,
      comment: null,
      createdAt: '2026-03-22T09:00:00+08:00',
      updatedAt: '2026-03-22T09:00:00+08:00',
      completedAt: null,
      instanceStatus: 'RUNNING',
      formData: { days: 2 },
      activeTaskIds: ['task_claim_001'],
    })
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: true,
      canApprove: false,
      canReject: false,
      canTransfer: false,
      canReturn: true,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_claim_001' />)

    expect(await screen.findByRole('button', { name: '认领任务' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '退回上一步' })).toBeInTheDocument()
  })

  it('submits the transfer dialog with target user id', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue({
      taskId: 'task_pending_001',
      instanceId: 'pi_001',
      processDefinitionId: 'pd_001',
      processKey: 'oa_leave',
      processName: '请假审批',
      businessKey: 'biz_001',
      applicantUserId: 'usr_001',
      nodeId: 'approve_manager',
      nodeName: '部门负责人审批',
      status: 'PENDING',
      assignmentMode: 'USER',
      candidateUserIds: ['usr_002'],
      assigneeUserId: 'usr_002',
      action: null,
      operatorUserId: null,
      comment: null,
      createdAt: '2026-03-22T09:00:00+08:00',
      updatedAt: '2026-03-22T09:00:00+08:00',
      completedAt: null,
      instanceStatus: 'RUNNING',
      formData: { days: 2 },
      activeTaskIds: ['task_pending_001'],
    })
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canTransfer: true,
      canReturn: false,
    })
    workbenchApiMocks.transferWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_pending_001',
      status: 'RUNNING',
      nextTasks: [
        {
          taskId: 'task_pending_002',
          nodeId: 'approve_manager',
          nodeName: '部门负责人审批',
          status: 'PENDING',
          assignmentMode: 'USER',
          candidateUserIds: ['usr_003'],
          assigneeUserId: 'usr_003',
        },
      ],
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_pending_001' />)

    fireEvent.click(await screen.findByRole('button', { name: '转办任务' }))
    fireEvent.change(screen.getByLabelText('目标用户编码'), {
      target: { value: 'usr_003' },
    })
    fireEvent.change(screen.getByLabelText('转办说明'), {
      target: { value: '转给王五处理' },
    })
    fireEvent.click(screen.getByRole('button', { name: '确认转办' }))

    await waitFor(() => {
      expect(workbenchApiMocks.transferWorkbenchTask).toHaveBeenCalledWith(
        'task_pending_001',
        {
          targetUserId: 'usr_003',
          comment: '转给王五处理',
        }
      )
    })
  })
})
