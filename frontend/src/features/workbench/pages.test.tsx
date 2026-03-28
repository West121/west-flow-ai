import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  Dashboard,
  WorkbenchCopiedListPage,
  WorkbenchDoneListPage,
  WorkbenchInitiatedListPage,
  WorkbenchStartPage,
  WorkbenchTodoDetailPage,
  WorkbenchTodoListPage,
} from './pages'

const {
  navigateMock,
  useSearchMock,
  routeNavigateMock,
  workbenchApiMocks,
  advancedRuntimeApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  useSearchMock: vi.fn(),
  routeNavigateMock: vi.fn(),
    workbenchApiMocks: {
      addSignWorkbenchTask: vi.fn(),
      claimWorkbenchTask: vi.fn(),
      completeWorkbenchTask: vi.fn(),
      delegateWorkbenchTask: vi.fn(),
      getWorkbenchDashboardSummary: vi.fn(),
      getApprovalSheetDetailByBusiness: vi.fn(),
      getWorkbenchTaskActions: vi.fn(),
      getWorkbenchTaskDetail: vi.fn(),
      handoverWorkbenchTasks: vi.fn(),
      listWorkbenchTasks: vi.fn(),
    listApprovalSheets: vi.fn(),
      readWorkbenchTask: vi.fn(),
      removeSignWorkbenchTask: vi.fn(),
      revokeWorkbenchTask: vi.fn(),
      returnWorkbenchTask: vi.fn(),
      signWorkbenchTask: vi.fn(),
      startWorkbenchProcess: vi.fn(),
      transferWorkbenchTask: vi.fn(),
      urgeWorkbenchTask: vi.fn(),
  },
  advancedRuntimeApiMocks: {
    createProcessCollaborationEvent: vi.fn(),
    getProcessTerminationSnapshot: vi.fn(),
    listProcessCollaborationTrace: vi.fn(),
    listProcessTerminationAuditTrail: vi.fn(),
    listProcessTimeTravelTrace: vi.fn(),
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
    extraActions,
    topContent,
    data,
    total,
  }: {
    title: string
    total?: number
    createAction?: { label: string; href: string }
    extraActions?: React.ReactNode
    topContent?: React.ReactNode
    data: Array<{
      taskId?: string
      instanceId?: string
      status?: string
      processName?: string
      businessTitle?: string
      billNo?: string
      instanceStatus?: string
    }>
  }) => (
    <div>
      <h2>{title}</h2>
      {topContent}
      {extraActions}
      {createAction ? <a href={createAction.href}>{createAction.label}</a> : null}
      <span>total:{total ?? data.length}</span>
      {data.map((item) => (
        <div key={item.taskId ?? item.instanceId}>
          <span>{item.processName}</span>
          <span>{item.businessTitle ?? item.billNo}</span>
          <span>
            {item.instanceStatus ??
              (item.status === 'PENDING_CLAIM'
                ? '待认领'
                : item.status === 'PENDING'
                  ? '待处理'
                  : item.status)}
          </span>
        </div>
      ))}
    </div>
  ),
}))

vi.mock('@/lib/api/workbench', () => ({
  ...workbenchApiMocks,
  WORKBENCH_RUNTIME_ENDPOINTS: {
    approvalSheetsPage: '/process-runtime/approval-sheets/page',
    tasksPage: '/process-runtime/tasks/page',
  },
}))
vi.mock('@/lib/api/process-runtime-advanced', () => ({
  ...advancedRuntimeApiMocks,
}))
vi.mock('@/stores/auth-store', () => ({
  useAuthStore: (selector: (state: { currentUser: { userId: string; aiCapabilities: string[] } }) => unknown) =>
    selector({
      currentUser: {
        userId: 'usr_001',
        aiCapabilities: ['ai:copilot:open'],
      },
    }),
}))

vi.mock('@/features/shared/pro-form', () => ({
  UserPickerField: ({
    value,
    onChange,
    ariaLabel,
  }: {
    value?: string
    onChange: (value: string) => void
    ariaLabel?: string
  }) => (
    <input
      aria-label={ariaLabel}
      value={value ?? ''}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
}))

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

function createWorkbenchTaskDetail(
  overrides: Record<string, unknown> = {}
) {
  const taskId = typeof overrides.taskId === 'string' ? overrides.taskId : 'task_001'
  const nodeId = typeof overrides.nodeId === 'string' ? overrides.nodeId : 'approve_manager'
  const nodeName = typeof overrides.nodeName === 'string' ? overrides.nodeName : '部门负责人审批'

  return {
    taskId,
    instanceId: 'pi_001',
    processDefinitionId: 'pd_001',
    processKey: 'oa_leave',
    processName: '请假审批',
    businessKey: 'leave_001',
    businessType: 'OA_LEAVE',
    applicantUserId: 'usr_001',
    nodeId,
    nodeName,
    status: 'PENDING',
    assignmentMode: 'USER',
    candidateUserIds: ['usr_002'],
    candidateGroupIds: [],
    assigneeUserId: 'usr_002',
    action: null,
    operatorUserId: null,
    comment: null,
    processFormKey: 'oa-leave-start-form',
    processFormVersion: '1.0.0',
    effectiveFormKey: 'oa-leave-approve-form',
    effectiveFormVersion: '1.0.0',
    nodeFormKey: 'oa-leave-approve-form',
    nodeFormVersion: '1.0.0',
    fieldBindings: [
      {
        source: 'PROCESS_FORM',
        sourceFieldKey: 'days',
        targetFieldKey: 'approvedDays',
      },
    ],
    taskFormData: {
      approved: true,
      comment: '同意',
    },
    createdAt: '2026-03-22T09:00:00+08:00',
    updatedAt: '2026-03-22T09:00:00+08:00',
    completedAt: null,
    receiveTime: '2026-03-22T09:00:00+08:00',
    readTime: '2026-03-22T09:02:00+08:00',
    handleStartTime: '2026-03-22T09:03:00+08:00',
    handleEndTime: null,
    handleDurationSeconds: null,
    instanceStatus: 'RUNNING',
    formData: { days: 2, reason: '外出处理事务' },
    businessData: {
      billId: 'leave_001',
      billNo: 'LEAVE-001',
      sceneCode: 'default',
      days: 2,
      reason: '外出处理事务',
      status: 'RUNNING',
      creatorUserId: 'usr_001',
    },
    flowNodes: [
      {
        id: 'start_1',
        type: 'start',
        name: '流程发起',
        position: { x: 100, y: 100 },
      },
      {
        id: 'approve_manager',
        type: 'approver',
        name: '部门负责人审批',
        position: { x: 340, y: 100 },
      },
      {
        id: 'end_1',
        type: 'end',
        name: '流程结束',
        position: { x: 580, y: 100 },
      },
    ],
    flowEdges: [
      {
        id: 'edge_1',
        source: 'start_1',
        target: 'approve_manager',
        label: '提交',
      },
      {
        id: 'edge_2',
        source: 'approve_manager',
        target: 'end_1',
        label: '通过',
      },
    ],
    instanceEvents: [
      {
        eventId: 'evt_001',
        instanceId: 'pi_001',
        nodeId: 'start_1',
        taskId: null,
        eventType: 'INSTANCE_STARTED',
        eventName: '流程实例已发起',
        operatorUserId: 'usr_001',
        occurredAt: '2026-03-22T09:00:00+08:00',
        details: {},
      },
      {
        eventId: 'evt_002',
        instanceId: 'pi_001',
        nodeId: nodeId,
        taskId,
        eventType: 'TASK_CREATED',
        eventName: '任务已创建',
        operatorUserId: 'usr_001',
        occurredAt: '2026-03-22T09:10:00+08:00',
        details: {},
      },
    ],
    taskTrace: [
      {
        taskId,
        nodeId,
        nodeName,
        status: 'PENDING',
        assigneeUserId: 'usr_002',
        candidateUserIds: ['usr_002'],
        candidateGroupIds: [],
        action: null,
        operatorUserId: null,
        comment: '待处理',
        receiveTime: '2026-03-22T09:10:00+08:00',
        readTime: '2026-03-22T09:12:00+08:00',
        handleStartTime: '2026-03-22T09:13:00+08:00',
        handleEndTime: null,
        handleDurationSeconds: null,
      },
    ],
    countersignGroups: [],
    inclusiveGatewayHits: [],
    activeTaskIds: [taskId],
    ...overrides,
  }
}

function createApprovalSheetPage(overrides: Record<string, unknown> = {}) {
  return {
    page: 1,
    pageSize: 20,
    total: 1,
    pages: 1,
    groups: [],
    records: [
      {
        instanceId: 'pi_sheet_001',
        processDefinitionId: 'pd_001',
        processKey: 'oa_leave',
        processName: '请假审批',
        businessId: 'leave_001',
        businessType: 'OA_LEAVE',
        billNo: 'LEAVE-001',
        businessTitle: '请假申请 · 外出处理事务',
        initiatorUserId: 'usr_001',
        currentNodeName: '部门负责人审批',
        currentTaskId: 'task_approval_001',
        currentTaskStatus: 'COMPLETED',
        currentAssigneeUserId: 'usr_002',
        instanceStatus: 'COMPLETED',
        latestAction: 'APPROVE',
        latestOperatorUserId: 'usr_002',
        createdAt: '2026-03-22T09:00:00+08:00',
        updatedAt: '2026-03-22T09:20:00+08:00',
        completedAt: '2026-03-22T09:20:00+08:00',
        ...overrides,
      },
    ],
  }
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
    advancedRuntimeApiMocks.getProcessTerminationSnapshot.mockResolvedValue(null)
    advancedRuntimeApiMocks.createProcessCollaborationEvent.mockResolvedValue({
      eventId: 'collab_001',
      instanceId: 'pi_001',
      eventType: 'COMMENT',
      eventName: '批注',
      subject: '默认协同',
      content: '默认协同内容',
      operatorUserId: 'usr_001',
      occurredAt: '2026-03-22T09:00:00+08:00',
    })
    advancedRuntimeApiMocks.listProcessTerminationAuditTrail.mockResolvedValue([])
    advancedRuntimeApiMocks.listProcessCollaborationTrace.mockResolvedValue([])
    advancedRuntimeApiMocks.listProcessTimeTravelTrace.mockResolvedValue([])
    workbenchApiMocks.listApprovalSheets.mockResolvedValue(
      createApprovalSheetPage()
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: true,
      canReturn: true,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
    })
    workbenchApiMocks.addSignWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_001',
      status: 'RUNNING',
      nextTasks: [],
    })
    workbenchApiMocks.readWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_001',
      status: 'RUNNING',
      nextTasks: [],
    })
    workbenchApiMocks.removeSignWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_001',
      status: 'RUNNING',
      nextTasks: [],
    })
    workbenchApiMocks.signWorkbenchTask.mockResolvedValue({
      taskId: 'task_001',
      instanceId: 'pi_001',
      nodeId: 'approve_manager',
      signatureType: 'PERSONAL_SEAL',
      signatureStatus: 'SIGNED',
      signatureComment: '已完成电子签章',
      signatureAt: '2026-03-22T09:16:00+08:00',
      operatorUserId: 'usr_002',
    })
    workbenchApiMocks.revokeWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_001',
      status: 'REVOKED',
      nextTasks: [],
    })
    workbenchApiMocks.urgeWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_001',
      status: 'RUNNING',
      nextTasks: [],
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders public tasks as 待认领 in the todo list', async () => {
    renderWithQuery(<WorkbenchTodoListPage />)

    expect(await screen.findByText('公共认领请假审批')).toBeInTheDocument()
    expect(screen.getAllByText('待认领').length).toBeGreaterThan(0)
    expect(
      screen.getByRole('button', { name: '用 AI 解读当前待办' })
    ).toHaveAttribute('data-source-route', '/workbench/todos/list')
  })

  it('renders real overview metrics on the dashboard', async () => {
    workbenchApiMocks.getWorkbenchDashboardSummary.mockResolvedValue({
      todoTodayCount: 7,
      doneApprovalCount: 12,
    })

    renderWithQuery(<Dashboard />)

    expect(await screen.findByText('平台总览')).toBeInTheDocument()
    expect(await screen.findByText('7')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(workbenchApiMocks.getWorkbenchDashboardSummary).toHaveBeenCalled()
  })

  it('shows the handover toolbar entry on the todo list', async () => {
    renderWithQuery(<WorkbenchTodoListPage />)

    expect(
      await screen.findByRole('button', { name: '离职转办' })
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '离职转办' }))
    expect(
      await screen.findByText('选择来源用户和目标用户，系统会批量转移该来源用户的当前待办。')
    ).toBeInTheDocument()
  })

  it('shows business entry cards instead of the legacy processKey form', async () => {
    renderWithQuery(<WorkbenchStartPage />)

    expect(screen.queryByLabelText('流程标识')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('业务单号')).not.toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '用 AI 推荐发起入口' })
    ).toHaveAttribute('data-source-route', '/workbench/start')
    expect(
      screen.getByRole('link', { name: '请假申请' })
    ).toHaveAttribute('href', '/oa/leave/create')
    expect(
      screen.getByRole('link', { name: '报销申请' })
    ).toHaveAttribute('href', '/oa/expense/create')
    expect(
      screen.getByRole('link', { name: '通用申请' })
    ).toHaveAttribute('href', '/oa/common/create')
    expect(
      screen.getByRole('link', { name: 'OA 流程查询' })
    ).toHaveAttribute('href', '/oa/query')
  })

  it('loads the done list through approval-sheet paging', async () => {
    renderWithQuery(<WorkbenchDoneListPage />)

    await waitFor(() => {
      expect(workbenchApiMocks.listApprovalSheets).toHaveBeenCalledWith({
        page: 1,
        pageSize: 20,
        keyword: '',
        filters: [],
        sorts: [],
        groups: [],
        view: 'DONE',
      })
    })

    expect(await screen.findByText('流程中心已办')).toBeInTheDocument()
    expect(screen.getByText('请假申请 · 外出处理事务')).toBeInTheDocument()
  })

  it('loads the initiated list through approval-sheet paging', async () => {
    workbenchApiMocks.listApprovalSheets.mockResolvedValue(
      createApprovalSheetPage({
        instanceId: 'pi_sheet_002',
        billNo: 'EXPENSE-001',
        businessTitle: '报销申请 · 客户接待',
        businessType: 'OA_EXPENSE',
        processName: '报销审批',
        instanceStatus: 'RUNNING',
      })
    )

    renderWithQuery(<WorkbenchInitiatedListPage />)

    await waitFor(() => {
      expect(workbenchApiMocks.listApprovalSheets).toHaveBeenCalledWith({
        page: 1,
        pageSize: 20,
        keyword: '',
        filters: [],
        sorts: [],
        groups: [],
        view: 'INITIATED',
      })
    })

    expect(await screen.findByText('流程中心我发起')).toBeInTheDocument()
    expect(screen.getByText('报销申请 · 客户接待')).toBeInTheDocument()
  })

  it('loads the copied list with the cc view', async () => {
    workbenchApiMocks.listApprovalSheets.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 3,
      pages: 0,
      groups: [],
      records: [
        {
          instanceId: 'pi_cc_001',
          processDefinitionId: 'pd_001',
          processKey: 'oa_leave',
          processName: '请假审批',
          businessId: 'leave_001',
          businessType: 'OA_LEAVE',
          billNo: 'LEAVE-001',
          businessTitle: '请假申请 · 外出处理事务',
          initiatorUserId: 'usr_001',
          currentNodeName: '部门负责人审批',
          currentTaskId: 'task_cc_001',
          currentTaskStatus: 'RUNNING',
          currentAssigneeUserId: 'usr_002',
          instanceStatus: 'RUNNING',
          latestAction: 'CC',
          latestOperatorUserId: 'usr_001',
          createdAt: '2026-03-22T09:00:00+08:00',
          updatedAt: '2026-03-22T09:10:00+08:00',
          completedAt: null,
        },
        {
          instanceId: 'pi_cc_002',
          processDefinitionId: 'pd_002',
          processKey: 'oa_expense',
          processName: '报销审批',
          businessId: 'expense_001',
          businessType: 'OA_EXPENSE',
          billNo: 'EXPENSE-001',
          businessTitle: '报销申请 · 客户接待',
          initiatorUserId: 'usr_002',
          currentNodeName: '财务复核',
          currentTaskId: 'task_cc_002',
          currentTaskStatus: 'RUNNING',
          currentAssigneeUserId: 'usr_003',
          instanceStatus: 'RUNNING',
          latestAction: 'READ',
          latestOperatorUserId: 'usr_002',
          createdAt: '2026-03-22T10:00:00+08:00',
          updatedAt: '2026-03-22T10:20:00+08:00',
          completedAt: null,
        },
        {
          instanceId: 'pi_cc_003',
          processDefinitionId: 'pd_003',
          processKey: 'oa_common',
          processName: '通用申请',
          businessId: 'common_001',
          businessType: 'OA_COMMON',
          billNo: 'COMMON-001',
          businessTitle: '通用申请 · 资产借用',
          initiatorUserId: 'usr_003',
          currentNodeName: '流程结束',
          currentTaskId: null,
          currentTaskStatus: 'COMPLETED',
          currentAssigneeUserId: null,
          instanceStatus: 'COMPLETED',
          latestAction: 'COMPLETED',
          latestOperatorUserId: 'usr_003',
          createdAt: '2026-03-22T11:00:00+08:00',
          updatedAt: '2026-03-22T11:20:00+08:00',
          completedAt: '2026-03-22T11:20:00+08:00',
        },
      ],
    })

    renderWithQuery(<WorkbenchCopiedListPage />)

    await waitFor(() => {
      expect(workbenchApiMocks.listApprovalSheets).toHaveBeenCalledWith({
        page: 1,
        pageSize: 20,
        keyword: '',
        filters: [],
        sorts: [],
        groups: [],
        view: 'CC',
      })
    })

    expect(await screen.findByText('流程中心抄送我')).toBeInTheDocument()
    expect(screen.getByText('抄送中')).toBeInTheDocument()
    expect(screen.getByText('已阅')).toBeInTheDocument()
    expect(screen.getAllByText('已完成').length).toBeGreaterThan(1)
    expect(screen.getByText('total:3')).toBeInTheDocument()
  })

  it('updates process-center advanced filters from the workbench list wrapper', async () => {
    renderWithQuery(<WorkbenchDoneListPage />)

    fireEvent.click(await screen.findByRole('button', { name: '已完成' }))

    await waitFor(() => {
      expect(routeNavigateMock).toHaveBeenCalled()
    })

    const latestCall =
      routeNavigateMock.mock.calls[routeNavigateMock.mock.calls.length - 1] ?? []
    const [navArg] = latestCall
    expect(typeof navArg?.search).toBe('function')

    expect(
      navArg.search({
        page: 1,
        pageSize: 20,
        keyword: '',
        filters: [],
        sorts: [],
        groups: [],
      })
    ).toMatchObject({
      page: undefined,
      filters: [
        {
          field: 'instanceStatus',
          operator: 'eq',
          value: 'COMPLETED',
        },
      ],
    })
  })

  it('shows claim and return actions conditionally in the task detail page', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(createWorkbenchTaskDetail({
      taskId: 'task_claim_001',
      instanceId: 'pi_001',
      processDefinitionId: 'pd_001',
      processKey: 'oa_leave',
      processName: '公共认领请假审批',
      businessKey: 'leave_001',
      businessType: 'OA_LEAVE',
      applicantUserId: 'usr_001',
      nodeId: 'approve_manager',
      nodeName: '共享审批池',
      status: 'PENDING_CLAIM',
      assignmentMode: 'DEPARTMENT',
      candidateUserIds: [],
      candidateGroupIds: ['dept_002'],
      assigneeUserId: null,
      businessData: {
        billId: 'leave_001',
        billNo: 'LEAVE-001',
        sceneCode: 'default',
        days: 2,
        reason: '外出处理事务',
        status: 'RUNNING',
        creatorUserId: 'usr_001',
      },
      instanceEvents: [
        {
          eventId: 'evt_001',
          instanceId: 'pi_001',
          nodeId: 'start_1',
          taskId: null,
          eventType: 'INSTANCE_STARTED',
          eventName: '流程实例已发起',
          operatorUserId: 'usr_001',
          occurredAt: '2026-03-22T09:00:00+08:00',
          details: {},
        },
        {
          eventId: 'evt_002',
          instanceId: 'pi_001',
          nodeId: 'approve_manager',
          taskId: 'task_claim_001',
          eventType: 'TASK_CREATED',
          eventName: '任务已创建',
          operatorUserId: 'usr_001',
          occurredAt: '2026-03-22T09:10:00+08:00',
          details: {},
        },
      ],
      taskTrace: [
        {
          taskId: 'task_claim_001',
          nodeId: 'approve_manager',
          nodeName: '共享审批池',
          status: 'PENDING_CLAIM',
          assigneeUserId: null,
          candidateUserIds: [],
          candidateGroupIds: ['dept_002'],
          action: null,
          operatorUserId: null,
          comment: '待认领',
          receiveTime: '2026-03-22T09:10:00+08:00',
          readTime: '2026-03-22T09:12:00+08:00',
          handleStartTime: '2026-03-22T09:13:00+08:00',
          handleEndTime: null,
          handleDurationSeconds: null,
        },
      ],
      activeTaskIds: ['task_claim_001'],
    }))
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: true,
      canApprove: false,
      canReject: false,
      canRejectRoute: false,
      canTransfer: false,
      canReturn: false,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_claim_001' />)

    expect(await screen.findByText('业务正文')).toBeInTheDocument()
    expect(screen.getByText('业务表单正文')).toBeInTheDocument()
    expect(screen.getAllByText('请假申请').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '播放回顾' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '暂停回顾' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '继续回顾' })).toBeInTheDocument()
    expect(screen.getAllByText('办理人').length).toBeGreaterThan(0)
    expect(screen.getAllByText(/读取：/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/开始：/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/完成：/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/时长：/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/超时：/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/审批意见：/).length).toBeGreaterThan(0)
    expect(screen.getAllByText('待认领').length).toBeGreaterThan(0)
    expect(screen.getByText('dept_002')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '用 AI 解读当前审批单' })
    ).toHaveAttribute('data-source-route', '/workbench/todos/task_claim_001')
    expect(await screen.findByRole('button', { name: '认领任务' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '退回' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '驳回' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '跳转' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '拿回' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '唤醒' })).not.toBeInTheDocument()
  })

  it('shows signature action and trace items in the task detail page', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_sign_001',
        instanceId: 'pi_sign_001',
        status: 'PENDING',
        instanceStatus: 'RUNNING',
        assigneeUserId: 'usr_002',
        activeTaskIds: ['task_sign_001'],
        instanceEvents: [
          {
            eventId: 'evt_sign_001',
            instanceId: 'pi_sign_001',
            nodeId: 'approve_manager',
            taskId: 'task_sign_001',
            eventType: 'TASK_SIGNATURE',
            eventName: '任务已签章',
            actionCategory: 'SIGNATURE',
            operatorUserId: 'usr_002',
            occurredAt: '2026-03-22T09:16:00+08:00',
            signatureType: 'PERSONAL_SEAL',
            signatureStatus: 'SIGNED',
            signatureComment: '已完成电子签章',
            signatureAt: '2026-03-22T09:16:00+08:00',
            details: {
              signatureType: 'PERSONAL_SEAL',
              signatureStatus: 'SIGNED',
              signatureComment: '已完成电子签章',
              signatureAt: '2026-03-22T09:16:00+08:00',
            },
          },
        ],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: false,
      canReturn: false,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
      canJump: false,
      canTakeBack: false,
      canWakeUp: false,
      canDelegate: false,
      canHandover: false,
      canSign: true,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_sign_001' />)

    expect(await screen.findByRole('button', { name: '签章' })).toBeInTheDocument()
    const traceTab = await screen.findByRole('tab', { name: '轨迹' })
    fireEvent.mouseDown(traceTab)
    fireEvent.click(traceTab)
    expect(await screen.findByText('电子签章')).toBeInTheDocument()
    expect(screen.getByText('个人印章')).toBeInTheDocument()
    expect(screen.getByText('已签章')).toBeInTheDocument()
    expect(
      screen.getAllByText((_, element) =>
        element?.textContent?.includes('签章说明：已完成电子签章') ?? false
      ).length
    ).toBeGreaterThan(0)
  })

  it('merges instance events and task trace for playback on completed approval sheets', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_playback_001',
        status: 'COMPLETED',
        instanceStatus: 'COMPLETED',
        activeTaskIds: [],
        instanceEvents: [
          {
            eventId: 'evt_001',
            eventType: 'START_PROCESS',
            eventName: '发起流程',
            nodeId: 'approve_manager',
            taskId: 'task_manager',
            operatorUserId: 'usr_001',
            occurredAt: '2026-03-22T09:00:00+08:00',
          },
        ],
        taskTrace: [
          {
            taskId: 'task_manager',
            nodeId: 'approve_manager',
            nodeName: '部门负责人审批',
            taskKind: 'NORMAL',
            status: 'COMPLETED',
            assigneeUserId: 'usr_002',
            candidateUserIds: [],
            candidateGroupIds: [],
            action: null,
            operatorUserId: 'usr_002',
            comment: '同意',
            receiveTime: '2026-03-22T09:01:00+08:00',
            readTime: null,
            handleStartTime: '2026-03-22T09:01:00+08:00',
            handleEndTime: '2026-03-22T09:05:00+08:00',
            handleDurationSeconds: 240,
          },
          {
            taskId: 'task_hr',
            nodeId: 'approve_hr',
            nodeName: 'HR 备案',
            taskKind: 'NORMAL',
            status: 'COMPLETED',
            assigneeUserId: 'usr_004',
            candidateUserIds: [],
            candidateGroupIds: [],
            action: null,
            operatorUserId: 'usr_004',
            comment: '已备案',
            receiveTime: '2026-03-22T09:05:30+08:00',
            readTime: null,
            handleStartTime: '2026-03-22T09:05:30+08:00',
            handleEndTime: '2026-03-22T09:06:00+08:00',
            handleDurationSeconds: 30,
          },
          {
            taskId: 'task_director',
            nodeId: 'approve_director',
            nodeName: '总监确认',
            taskKind: 'NORMAL',
            status: 'COMPLETED',
            assigneeUserId: 'usr_005',
            candidateUserIds: [],
            candidateGroupIds: [],
            action: null,
            operatorUserId: 'usr_005',
            comment: '同意',
            receiveTime: '2026-03-22T09:06:10+08:00',
            readTime: null,
            handleStartTime: '2026-03-22T09:06:10+08:00',
            handleEndTime: '2026-03-22T09:08:00+08:00',
            handleDurationSeconds: 110,
          },
        ],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: false,
      canReject: false,
      canRejectRoute: false,
      canTransfer: false,
      canReturn: false,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_playback_001' />)

    expect(await screen.findByText('流程图回顾')).toBeInTheDocument()
    expect(screen.getByText('事件数 5')).toBeInTheDocument()
  })

  it('shows automation trace, notification records and automation status in the workflow center', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_auto_001',
        automationStatus: 'SUCCESS',
        automationActionTrace: [
          {
            traceId: 'auto_trace_001',
            traceType: 'CONDITION_MATCHED',
            traceName: '命中请假完成规则',
            status: 'SUCCESS',
            operatorUserId: 'system',
            occurredAt: '2026-03-22T09:14:00+08:00',
            detail: '状态为 COMPLETED，继续发送通知',
          },
        ],
        notificationSendRecords: [
          {
            recordId: 'notify_001',
            channelName: '企业微信通知',
            channelType: 'WECHAT_WORK',
            target: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send',
            status: 'SUCCESS',
            attemptCount: 1,
            sentAt: '2026-03-22T09:15:00+08:00',
            errorMessage: null,
          },
        ],
        activeTaskIds: ['task_auto_001'],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: false,
      canReject: false,
      canRejectRoute: false,
      canTransfer: false,
      canReturn: false,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
      canJump: false,
      canTakeBack: false,
      canWakeUp: false,
      canSign: false,
    })
    workbenchApiMocks.listApprovalSheets.mockResolvedValue(
      createApprovalSheetPage({
        instanceStatus: 'COMPLETED',
        automationStatus: 'SUCCESS',
      })
    )

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_auto_001' />)
    renderWithQuery(<WorkbenchDoneListPage />)

    await screen.findByText('业务正文')
    const automationTab = await screen.findByRole('tab', { name: '自动化' })
    fireEvent.mouseDown(automationTab)
    fireEvent.click(automationTab)

    expect(await screen.findAllByText('自动动作轨迹')).not.toHaveLength(0)
    expect(screen.getAllByText('通知发送记录').length).toBeGreaterThan(0)
    expect(screen.getAllByText('自动化状态').length).toBeGreaterThan(0)
    expect(screen.getAllByText('执行成功').length).toBeGreaterThan(0)
  })

  it('shows delegate action and proxy metadata in the task detail page', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_delegate_001',
        status: 'PENDING',
        assignmentMode: 'USER',
        actingMode: 'PROXY',
        actingForUserId: 'usr_001',
        delegatedByUserId: 'usr_009',
        handoverFromUserId: 'usr_010',
        taskTrace: [
          {
            taskId: 'task_delegate_001',
            nodeId: 'approve_manager',
            nodeName: '部门负责人审批',
            status: 'DELEGATED',
            assigneeUserId: 'usr_002',
            candidateUserIds: ['usr_002'],
            action: 'DELEGATE',
            operatorUserId: 'usr_002',
            comment: '委派给王五',
            receiveTime: '2026-03-22T09:10:00+08:00',
            readTime: '2026-03-22T09:11:00+08:00',
            handleStartTime: '2026-03-22T09:12:00+08:00',
            handleEndTime: '2026-03-22T09:13:00+08:00',
            handleDurationSeconds: 60,
            targetUserId: 'usr_003',
            actingMode: 'DELEGATE',
            actingForUserId: 'usr_002',
            delegatedByUserId: 'usr_001',
            handoverFromUserId: null,
          },
          {
            taskId: 'task_proxy_001',
            nodeId: 'approve_proxy',
            nodeName: '代理代办',
            status: 'PENDING',
            assigneeUserId: 'usr_003',
            candidateUserIds: ['usr_003'],
            action: 'PROXY',
            operatorUserId: 'usr_003',
            comment: '代理代办中',
            receiveTime: '2026-03-22T09:20:00+08:00',
            readTime: '2026-03-22T09:21:00+08:00',
            handleStartTime: '2026-03-22T09:22:00+08:00',
            handleEndTime: null,
            handleDurationSeconds: null,
            actingMode: 'PROXY',
            actingForUserId: 'usr_002',
            delegatedByUserId: 'usr_001',
            handoverFromUserId: null,
          },
          {
            taskId: 'task_handover_001',
            nodeId: 'approve_handover',
            nodeName: '离职转办',
            status: 'HANDOVERED',
            assigneeUserId: 'usr_004',
            candidateUserIds: ['usr_004'],
            action: 'HANDOVER',
            operatorUserId: 'usr_999',
            comment: '离职转办给新同事',
            receiveTime: '2026-03-22T09:30:00+08:00',
            readTime: '2026-03-22T09:31:00+08:00',
            handleStartTime: '2026-03-22T09:32:00+08:00',
            handleEndTime: '2026-03-22T09:33:00+08:00',
            handleDurationSeconds: 60,
            actingMode: 'HANDOVER',
            actingForUserId: 'usr_004',
            delegatedByUserId: null,
            handoverFromUserId: 'usr_999',
          },
        ],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: false,
      canRejectRoute: false,
      canTransfer: false,
      canReturn: false,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
      canJump: false,
      canTakeBack: false,
      canWakeUp: false,
      canDelegate: true,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_delegate_001' />)

    expect(await screen.findByRole('button', { name: '委派' })).toBeInTheDocument()
    expect(screen.getAllByText('代理代办').length).toBeGreaterThan(0)
    expect(screen.getAllByText('代谁办理').length).toBeGreaterThan(0)
    expect(screen.getAllByText('委派来源').length).toBeGreaterThan(0)
    expect(screen.getAllByText('离职转办来源').length).toBeGreaterThan(0)
  })

  it('renders advanced runtime labels in the action timeline', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_history_001',
        activeTaskIds: ['task_history_001'],
        taskTrace: [
          {
            taskId: 'task_history_001',
            nodeId: 'approve_manager',
            nodeName: '部门负责人审批',
            status: 'REJECTED',
            assigneeUserId: 'usr_002',
            candidateUserIds: ['usr_002'],
            action: 'REJECT_ROUTE',
            operatorUserId: 'usr_002',
            comment: '请补充资料',
            receiveTime: '2026-03-22T09:10:00+08:00',
            readTime: '2026-03-22T09:12:00+08:00',
            handleStartTime: '2026-03-22T09:13:00+08:00',
            handleEndTime: '2026-03-22T09:18:00+08:00',
            handleDurationSeconds: 300,
            sourceTaskId: 'task_source_001',
            targetTaskId: 'task_target_001',
            targetUserId: 'usr_003',
            isCcTask: false,
            isAddSignTask: false,
            isRevoked: false,
          },
          {
            taskId: 'task_history_002',
            nodeId: 'jump_node_001',
            nodeName: '财务复核',
            status: 'JUMPED',
            assigneeUserId: 'usr_004',
            candidateUserIds: ['usr_004'],
            action: 'JUMP',
            operatorUserId: 'usr_002',
            comment: '跳转到财务复核',
            receiveTime: '2026-03-22T09:20:00+08:00',
            readTime: '2026-03-22T09:21:00+08:00',
            handleStartTime: '2026-03-22T09:22:00+08:00',
            handleEndTime: '2026-03-22T09:23:00+08:00',
            handleDurationSeconds: 60,
            sourceTaskId: 'task_source_002',
            targetTaskId: 'task_target_002',
            targetUserId: 'usr_004',
            isCcTask: false,
            isAddSignTask: false,
            isRevoked: false,
          },
          {
            taskId: 'task_history_003',
            nodeId: 'take_back_node_001',
            nodeName: '流程发起',
            status: 'TAKEN_BACK',
            assigneeUserId: 'usr_001',
            candidateUserIds: ['usr_001'],
            action: 'TAKE_BACK',
            operatorUserId: 'usr_001',
            comment: '拿回后重提',
            receiveTime: '2026-03-22T09:30:00+08:00',
            readTime: '2026-03-22T09:31:00+08:00',
            handleStartTime: '2026-03-22T09:32:00+08:00',
            handleEndTime: '2026-03-22T09:33:00+08:00',
            handleDurationSeconds: 60,
            sourceTaskId: 'task_source_003',
            targetTaskId: 'task_target_003',
            targetUserId: 'usr_001',
            isCcTask: false,
            isAddSignTask: false,
            isRevoked: false,
          },
          {
            taskId: 'task_history_004',
            nodeId: 'wake_node_001',
            nodeName: '历史审批',
            status: 'PENDING',
            assigneeUserId: 'usr_005',
            candidateUserIds: ['usr_005'],
            action: 'WAKE_UP',
            operatorUserId: 'usr_001',
            comment: '唤醒后重新处理',
            receiveTime: '2026-03-22T09:40:00+08:00',
            readTime: '2026-03-22T09:41:00+08:00',
            handleStartTime: '2026-03-22T09:42:00+08:00',
            handleEndTime: null,
            handleDurationSeconds: null,
            sourceTaskId: 'task_source_004',
            targetTaskId: 'task_target_004',
            targetUserId: 'usr_005',
            isCcTask: false,
            isAddSignTask: false,
            isRevoked: false,
          },
          {
            taskId: 'task_history_005',
            nodeId: 'delegate_node_001',
            nodeName: '委派复核',
            status: 'DELEGATED',
            assigneeUserId: 'usr_006',
            candidateUserIds: ['usr_006'],
            action: 'DELEGATE',
            operatorUserId: 'usr_002',
            comment: '委派给同事代办',
            receiveTime: '2026-03-22T09:50:00+08:00',
            readTime: '2026-03-22T09:51:00+08:00',
            handleStartTime: '2026-03-22T09:52:00+08:00',
            handleEndTime: '2026-03-22T09:53:00+08:00',
            handleDurationSeconds: 60,
            sourceTaskId: 'task_source_005',
            targetTaskId: 'task_target_005',
            targetUserId: 'usr_006',
            actingMode: 'DELEGATE',
            actingForUserId: 'usr_002',
            delegatedByUserId: 'usr_001',
            handoverFromUserId: null,
            isCcTask: false,
            isAddSignTask: false,
            isRevoked: false,
          },
          {
            taskId: 'task_history_006',
            nodeId: 'proxy_node_001',
            nodeName: '代理代办',
            status: 'PENDING',
            assigneeUserId: 'usr_007',
            candidateUserIds: ['usr_007'],
            action: 'PROXY',
            operatorUserId: 'usr_007',
            comment: '代理人处理',
            receiveTime: '2026-03-22T09:55:00+08:00',
            readTime: '2026-03-22T09:56:00+08:00',
            handleStartTime: '2026-03-22T09:57:00+08:00',
            handleEndTime: '2026-03-22T09:58:00+08:00',
            handleDurationSeconds: 60,
            sourceTaskId: 'task_source_006',
            targetTaskId: 'task_target_006',
            targetUserId: 'usr_007',
            actingMode: 'PROXY',
            actingForUserId: 'usr_002',
            delegatedByUserId: 'usr_001',
            handoverFromUserId: null,
            isCcTask: false,
            isAddSignTask: false,
            isRevoked: false,
          },
          {
            taskId: 'task_history_007',
            nodeId: 'handover_node_001',
            nodeName: '离职转办',
            status: 'HANDOVERED',
            assigneeUserId: 'usr_008',
            candidateUserIds: ['usr_008'],
            action: 'HANDOVER',
            operatorUserId: 'usr_998',
            comment: '离职后批量转办',
            receiveTime: '2026-03-22T10:00:00+08:00',
            readTime: '2026-03-22T10:01:00+08:00',
            handleStartTime: '2026-03-22T10:02:00+08:00',
            handleEndTime: '2026-03-22T10:03:00+08:00',
            handleDurationSeconds: 60,
            sourceTaskId: 'task_source_007',
            targetTaskId: 'task_target_007',
            targetUserId: 'usr_008',
            actingMode: 'HANDOVER',
            actingForUserId: 'usr_008',
            delegatedByUserId: null,
            handoverFromUserId: 'usr_998',
            isCcTask: false,
            isAddSignTask: false,
            isRevoked: false,
          },
          {
            taskId: 'task_history_008',
            nodeId: 'or_sign_node_001',
            nodeName: '负责人或签',
            status: 'AUTO_FINISHED',
            assigneeUserId: 'usr_009',
            candidateUserIds: ['usr_009'],
            action: null,
            operatorUserId: null,
            comment: '或签命中后自动结束',
            receiveTime: '2026-03-22T10:10:00+08:00',
            readTime: null,
            handleStartTime: null,
            handleEndTime: '2026-03-22T10:11:00+08:00',
            handleDurationSeconds: 0,
            isCcTask: false,
            isAddSignTask: false,
            isRevoked: false,
          },
        ],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: false,
      canReturn: false,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
      canJump: false,
      canTakeBack: false,
      canWakeUp: false,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_history_001' />)

    await screen.findByText('业务正文')
    const traceTab = await screen.findByRole('tab', { name: '轨迹' })
    fireEvent.mouseDown(traceTab)
    fireEvent.click(traceTab)

    expect(await screen.findByText('审批过程')).toBeInTheDocument()
    expect(screen.getAllByText('驳回到上一步人工节点').length).toBeGreaterThan(0)
    expect(screen.getAllByText('跳转').length).toBeGreaterThan(0)
    expect(screen.getAllByText('拿回').length).toBeGreaterThan(0)
    expect(screen.getAllByText('唤醒').length).toBeGreaterThan(0)
    expect(screen.getAllByText('委派').length).toBeGreaterThan(0)
    expect(screen.getAllByText('代理代办').length).toBeGreaterThan(0)
    expect(screen.getAllByText('离职转办').length).toBeGreaterThan(0)
    expect(screen.getAllByText('自动结束').length).toBeGreaterThan(0)
  })

  it('shows countersign progress and vote summary in approval detail', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_vote_001',
        activeTaskIds: ['task_vote_001'],
        countersignGroups: [
          {
            groupId: 'tg_vote_001',
            instanceId: 'pi_001',
            nodeId: 'approve_manager',
            nodeName: '负责人票签',
            approvalMode: 'VOTE',
            groupStatus: 'RUNNING',
            totalCount: 3,
            completedCount: 1,
            activeCount: 2,
            waitingCount: 0,
            voteThresholdPercent: 60,
            approvedWeight: 40,
            rejectedWeight: 0,
            decisionStatus: null,
            members: [
              {
                memberId: 'member_001',
                taskId: 'task_vote_001',
                assigneeUserId: 'usr_002',
                sequenceNo: 1,
                voteWeight: 40,
                memberStatus: 'COMPLETED',
              },
              {
                memberId: 'member_002',
                taskId: 'task_vote_002',
                assigneeUserId: 'usr_003',
                sequenceNo: 2,
                voteWeight: 35,
                memberStatus: 'ACTIVE',
              },
              {
                memberId: 'member_003',
                taskId: 'task_vote_003',
                assigneeUserId: 'usr_004',
                sequenceNo: 3,
                voteWeight: 25,
                memberStatus: 'AUTO_FINISHED',
              },
            ],
          },
        ],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: false,
      canReturn: false,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
      canJump: false,
      canTakeBack: false,
      canWakeUp: false,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_vote_001' />)

    expect(await screen.findByText('会签进度')).toBeInTheDocument()
    expect(screen.getByText('票签')).toBeInTheDocument()
    expect(screen.getByText('通过阈值：60%')).toBeInTheDocument()
    expect(screen.getByText('通过票权：40')).toBeInTheDocument()
    expect(screen.getByText('拒绝票权：0')).toBeInTheDocument()
    expect(screen.getByText('负责人票签')).toBeInTheDocument()
    expect(screen.getByText('票权 40')).toBeInTheDocument()
    expect(screen.getByText(/状态：自动结束/)).toBeInTheDocument()
  })

  it('shows subprocess links in approval detail', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_subprocess_001',
        activeTaskIds: ['task_subprocess_001'],
        processLinks: [
          {
            linkId: 'link_001',
            rootInstanceId: 'pi_001',
            parentInstanceId: 'pi_001',
            childInstanceId: 'pi_child_001',
            parentNodeId: 'subprocess_review',
            calledProcessKey: 'oa_sub_review',
            calledDefinitionId: 'oa_sub_review:1:1004',
            linkType: 'CALL_ACTIVITY',
            status: 'RUNNING',
            terminatePolicy: 'TERMINATE_PARENT_AND_SUBPROCESS',
            childFinishPolicy: 'RETURN_TO_PARENT',
            createdAt: '2026-03-23T10:10:00+08:00',
            finishedAt: null,
          },
        ],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: false,
      canReturn: false,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
      canJump: false,
      canTakeBack: false,
      canWakeUp: false,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_subprocess_001' />)

    await screen.findByText('业务正文')
    const runtimeTab = await screen.findByRole('tab', { name: '运行态' })
    fireEvent.mouseDown(runtimeTab)
    fireEvent.click(runtimeTab)

    expect(await screen.findByText('主子流程')).toBeInTheDocument()
    expect(screen.getByText('主流程实例：pi_001')).toBeInTheDocument()
    expect(screen.getByText('子流程实例：pi_child_001')).toBeInTheDocument()
    expect(screen.getByText('调用节点：subprocess_review')).toBeInTheDocument()
    expect(screen.getByText('子流程编码：oa_sub_review')).toBeInTheDocument()
  })

  it('shows inclusive gateway hits in approval detail', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_inclusive_001',
        flowNodes: [
          {
            id: 'start_1',
            type: 'start',
            name: '开始',
            position: { x: 100, y: 100 },
          },
          {
            id: 'inclusive_split_1',
            type: 'inclusive_split',
            name: '包容分支',
            position: { x: 320, y: 100 },
          },
          {
            id: 'approve_finance',
            type: 'approver',
            name: '财务审批',
            position: { x: 540, y: 40 },
          },
          {
            id: 'approve_hr',
            type: 'approver',
            name: '人事审批',
            position: { x: 540, y: 180 },
          },
          {
            id: 'inclusive_join_1',
            type: 'inclusive_join',
            name: '包容汇聚',
            position: { x: 760, y: 100 },
          },
        ],
        inclusiveGatewayHits: [
          {
            splitNodeId: 'inclusive_split_1',
            splitNodeName: '包容分支',
            joinNodeId: 'inclusive_join_1',
            joinNodeName: '包容汇聚',
            defaultBranchId: 'edge_finance',
            requiredBranchCount: 1,
            branchMergePolicy: 'DEFAULT_BRANCH',
            gatewayStatus: 'COMPLETED',
            totalTargetCount: 2,
            activatedTargetCount: 1,
            activatedTargetNodeIds: ['approve_finance'],
            activatedTargetNodeNames: ['财务审批'],
            skippedTargetNodeIds: ['approve_hr'],
            skippedTargetNodeNames: ['人事审批'],
            branchPriorities: [10, 20],
            branchLabels: ['财务审批', '人事审批'],
            branchExpressions: ['amount > 1000', 'days > 3'],
            decisionSummary: '命中财务审批，默认分支未触发。',
            firstActivatedAt: '2026-03-23T10:05:00+08:00',
            finishedAt: '2026-03-23T10:20:00+08:00',
          },
        ],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: false,
      canReturn: false,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
      canJump: false,
      canTakeBack: false,
      canWakeUp: false,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_inclusive_001' />)

    expect(await screen.findByText('包容分支命中')).toBeInTheDocument()
    expect(screen.getAllByText('包容分支').length).toBeGreaterThan(0)
    expect(screen.getByText('命中路径')).toBeInTheDocument()
    expect(screen.getByText('跳过路径')).toBeInTheDocument()
    expect(screen.getAllByText('财务审批').length).toBeGreaterThan(0)
    expect(screen.getAllByText('人事审批').length).toBeGreaterThan(0)
  })

  it('shows append and dynamic build runtime structure links in approval detail', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_runtime_001',
        activeTaskIds: ['task_runtime_001'],
        processLinks: [
          {
            linkId: 'subprocess_001',
            rootInstanceId: 'pi_001',
            parentInstanceId: 'pi_001',
            childInstanceId: 'pi_sub_001',
            parentNodeId: 'subprocess_review',
            calledProcessKey: 'oa_sub_review',
            calledDefinitionId: 'oa_sub_review:1:1004',
            calledVersionPolicy: 'FIXED_VERSION',
            calledVersion: 1,
            linkType: 'CALL_ACTIVITY',
            status: 'RUNNING',
            terminatePolicy: 'TERMINATE_PARENT_AND_SUBPROCESS',
            childFinishPolicy: 'RETURN_TO_PARENT',
            callScope: 'CHILD_AND_DESCENDANTS',
            joinMode: 'WAIT_PARENT_CONFIRM',
            childStartStrategy: 'FIXED_VERSION',
            childStartDecisionReason: 'FIXED_VERSION',
            parentResumeStrategy: 'WAIT_PARENT_CONFIRM',
            resumeDecisionReason: 'WAIT_PARENT_CONFIRM',
            createdAt: '2026-03-23T10:10:00+08:00',
            finishedAt: null,
          },
        ],
        runtimeStructureLinks: [
          {
            linkId: 'append_001',
            rootInstanceId: 'pi_001',
            parentInstanceId: 'pi_sub_001',
            childInstanceId: 'pi_append_001',
            parentNodeId: 'dynamic_builder_001',
            calledProcessKey: 'oa_leave_append_review',
            calledDefinitionId: 'oa_leave_append_review:1:1008',
            calledVersionPolicy: 'LATEST_PUBLISHED',
            calledVersion: null,
            linkType: 'ADHOC',
            runtimeLinkType: 'ADHOC_TASK',
            triggerMode: 'APPEND',
            appendType: 'TASK',
            status: 'RUNNING',
            terminatePolicy: 'TERMINATE_GENERATED_ONLY',
            childFinishPolicy: 'RETURN_TO_PARENT',
            callScope: null,
            joinMode: null,
            childStartStrategy: null,
            childStartDecisionReason: null,
            parentResumeStrategy: null,
            resumeDecisionReason: null,
            sourceTaskId: 'task_runtime_001',
            sourceNodeId: 'dynamic_builder_001',
            targetTaskId: 'task_append_001',
            targetInstanceId: 'pi_append_001',
            targetUserId: 'usr_003',
            operatorUserId: 'usr_002',
            commentText: '追加一位串行复核人',
            createdAt: '2026-03-23T10:20:00+08:00',
            finishedAt: null,
          },
          {
            linkId: 'dynamic_001',
            rootInstanceId: 'pi_001',
            parentInstanceId: 'pi_sub_001',
            childInstanceId: 'pi_dynamic_001',
            parentNodeId: 'dynamic_builder_002',
            calledProcessKey: 'oa_leave_dynamic_subflow',
            calledDefinitionId: 'oa_leave_dynamic_subflow:2:1009',
            calledVersionPolicy: 'LATEST_PUBLISHED',
            calledVersion: 2,
            linkType: 'ADHOC',
            runtimeLinkType: 'ADHOC_SUBPROCESS',
            triggerMode: 'DYNAMIC_BUILD',
            appendType: 'SUBPROCESS',
            status: 'COMPLETED',
            terminatePolicy: 'TERMINATE_PARENT_AND_GENERATED',
            childFinishPolicy: 'TERMINATE_PARENT',
            callScope: 'CHILD_ONLY',
            joinMode: 'AUTO_RETURN',
            childStartStrategy: 'LATEST_PUBLISHED',
            childStartDecisionReason: 'LATEST_PUBLISHED',
            parentResumeStrategy: 'AUTO_RETURN',
            resumeDecisionReason: 'AUTO_RETURN',
            buildMode: 'SUBPROCESS_CALLS',
            sourceMode: 'RULE',
            executionStrategy: 'RULE_ONLY',
            fallbackStrategy: 'USE_TEMPLATE',
            resolvedTargetMode: 'SUBPROCESS',
            resolvedSourceMode: 'RULE',
            resolutionPath: 'RULE -> TEMPLATE',
            templateSource: 'system',
            sourceTaskId: 'task_runtime_001',
            sourceNodeId: 'dynamic_builder_002',
            targetTaskId: null,
            targetInstanceId: 'pi_dynamic_001',
            targetUserId: null,
            operatorUserId: 'system',
            commentText: '规则命中后自动构建子流程',
            createdAt: '2026-03-23T10:30:00+08:00',
            finishedAt: '2026-03-23T10:45:00+08:00',
          },
        ],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: false,
      canReturn: false,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
      canJump: false,
      canTakeBack: false,
      canWakeUp: false,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_runtime_001' />)

    await screen.findByText('业务正文')
    const runtimeTab = await screen.findByRole('tab', { name: '运行态' })
    fireEvent.mouseDown(runtimeTab)
    fireEvent.click(runtimeTab)

    expect(await screen.findByText('运行态结构')).toBeInTheDocument()
    expect(screen.getAllByText('主子流程').length).toBeGreaterThan(0)
    expect(screen.getAllByText('追加任务').length).toBeGreaterThan(0)
    expect(screen.getAllByText('动态构建').length).toBeGreaterThan(0)
    expect(screen.getByText(/子流程实例：pi_sub_001/)).toBeInTheDocument()
    expect(screen.getByText(/附属任务/)).toBeInTheDocument()
    expect(
      screen
        .getAllByText((_, element) =>
          element?.textContent?.includes('pi_append_001') ?? false
        )
        .some((element) =>
          element.textContent?.replace(/\s+/g, '') === '附属任务：pi_append_001'
        )
    ).toBe(true)
    expect(screen.getByText(/动态构建实例/)).toBeInTheDocument()
    expect(
      screen
        .getAllByText((_, element) =>
          element?.textContent?.includes('pi_dynamic_001') ?? false
        )
        .some((element) =>
          element.textContent?.replace(/\s+/g, '') === '动态构建实例：pi_dynamic_001'
        )
    ).toBe(true)
    expect(screen.getByText('结构来源：ADHOC_TASK')).toBeInTheDocument()
    expect(screen.getByText('结构来源：ADHOC_SUBPROCESS')).toBeInTheDocument()
    expect(screen.getByText('调用版本策略：FIXED_VERSION')).toBeInTheDocument()
    expect(screen.getByText('调用版本：1')).toBeInTheDocument()
    expect(screen.getByText('调用范围：CHILD_AND_DESCENDANTS')).toBeInTheDocument()
    expect(screen.getByText('汇合模式：WAIT_PARENT_CONFIRM')).toBeInTheDocument()
    expect(screen.getByText('子流程启动策略：FIXED_VERSION')).toBeInTheDocument()
    expect(screen.getByText('启动决策：FIXED_VERSION')).toBeInTheDocument()
    expect(screen.getByText('父流程恢复策略：WAIT_PARENT_CONFIRM')).toBeInTheDocument()
    expect(screen.getByText('恢复决策：WAIT_PARENT_CONFIRM')).toBeInTheDocument()
    expect(screen.getByText('执行策略：RULE_ONLY')).toBeInTheDocument()
    expect(screen.getByText('回退策略：USE_TEMPLATE')).toBeInTheDocument()
    expect(screen.getAllByText('APPEND').length).toBeGreaterThan(0)
    expect(screen.getAllByText('DYNAMIC_BUILD').length).toBeGreaterThan(0)
    expect(screen.getByText('附言：追加一位串行复核人')).toBeInTheDocument()
  })

  it('shows termination strategy, collaboration and time-travel sections in approval detail', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        flowNodes: [
          {
            id: 'start_1',
            type: 'start',
            name: '流程发起',
            position: { x: 100, y: 100 },
          },
          {
            id: 'supervise_1',
            type: 'supervise',
            name: '督办节点',
            position: { x: 340, y: 100 },
          },
          {
            id: 'end_1',
            type: 'end',
            name: '流程结束',
            position: { x: 580, y: 100 },
          },
        ],
        flowEdges: [
          {
            id: 'edge_1',
            source: 'start_1',
            target: 'supervise_1',
            label: '提交',
          },
          {
            id: 'edge_2',
            source: 'supervise_1',
            target: 'end_1',
            label: '通过',
          },
        ],
        instanceEvents: [
          {
            eventId: 'evt_001',
            instanceId: 'pi_001',
            nodeId: 'start_1',
            taskId: null,
            eventType: 'INSTANCE_STARTED',
            eventName: '流程实例已发起',
            operatorUserId: 'usr_001',
            occurredAt: '2026-03-23T10:00:00+08:00',
            details: {},
          },
          {
            eventId: 'evt_002',
            instanceId: 'pi_001',
            nodeId: 'supervise_1',
            taskId: 'task_001',
            eventType: 'TASK_CREATED',
            eventName: '任务已创建',
            operatorUserId: 'usr_001',
            occurredAt: '2026-03-23T10:05:00+08:00',
            details: {},
          },
        ],
        taskTrace: [
          {
            taskId: 'task_001',
            nodeId: 'supervise_1',
            nodeName: '督办节点',
            taskKind: 'supervise',
            status: 'PENDING',
            assigneeUserId: 'usr_002',
            candidateUserIds: ['usr_002'],
            candidateGroupIds: [],
            action: null,
            operatorUserId: null,
            comment: '待处理',
            receiveTime: '2026-03-23T10:05:00+08:00',
            readTime: '2026-03-23T10:06:00+08:00',
            handleStartTime: '2026-03-23T10:07:00+08:00',
            handleEndTime: null,
            handleDurationSeconds: null,
          },
        ],
        processLinks: [
          {
            linkId: 'subprocess_001',
            rootInstanceId: 'pi_root_001',
            parentInstanceId: 'pi_root_001',
            childInstanceId: 'pi_001',
            parentNodeId: 'subprocess_review',
            calledProcessKey: 'oa_sub_review',
            calledDefinitionId: 'oa_sub_review:1:1004',
            linkType: 'CALL_ACTIVITY',
            status: 'RUNNING',
            terminatePolicy: 'TERMINATE_PARENT_AND_SUBPROCESS',
            childFinishPolicy: 'RETURN_TO_PARENT',
            createdAt: '2026-03-23T10:10:00+08:00',
            finishedAt: null,
          },
        ],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: false,
      canReturn: false,
      canAddSign: false,
      canRemoveSign: false,
      canRevoke: false,
      canUrge: false,
      canRead: false,
      canJump: false,
      canTakeBack: false,
      canWakeUp: false,
    })
    advancedRuntimeApiMocks.getProcessTerminationSnapshot.mockResolvedValue({
      rootInstanceId: 'pi_root_001',
      scope: 'CHILD',
      propagationPolicy: 'CASCADE_ALL',
      reason: '审批详情终止策略预览',
      operatorUserId: 'usr_002',
      summary: 'scope=CHILD, propagation=CASCADE_ALL, targets=2',
      targetCount: 2,
      generatedAt: '2026-03-23T10:20:00+08:00',
      nodes: [],
    })
    advancedRuntimeApiMocks.listProcessTerminationAuditTrail.mockResolvedValue([
      {
        auditId: 'audit_001',
        rootInstanceId: 'pi_root_001',
        targetInstanceId: 'pi_001',
        parentInstanceId: 'pi_root_001',
        targetKind: 'SUBPROCESS',
        terminateScope: 'CHILD',
        propagationPolicy: 'CASCADE_ALL',
        eventType: 'PLANNED',
        resultStatus: 'PLANNED',
        reason: '终止原因',
        operatorUserId: 'usr_admin',
        detailJson: '{}',
        createdAt: '2026-03-23T10:21:00+08:00',
        finishedAt: null,
      },
    ])
    advancedRuntimeApiMocks.listProcessCollaborationTrace.mockResolvedValue([
      {
        eventId: 'col_001',
        instanceId: 'pi_001',
        taskId: 'task_001',
        eventType: 'SUPERVISE',
        subject: '请补充材料',
        content: '请补充请假附件。',
        mentionedUserIds: ['usr_002'],
        permissionCode: 'workflow:collaboration:view',
        actionType: 'COLLABORATION_EVENT_CREATED',
        actionCategory: 'COLLABORATION',
        operatorUserId: 'usr_003',
        occurredAt: '2026-03-23T10:22:00+08:00',
        details: {},
      },
    ])
    advancedRuntimeApiMocks.listProcessTimeTravelTrace.mockResolvedValue([
      {
        executionId: 'tt_001',
        instanceId: 'pi_001',
        strategy: 'BACK_TO_NODE',
        taskId: 'task_001',
        targetNodeId: 'approve_manager',
        targetTaskId: 'task_001',
        newInstanceId: null,
        permissionCode: 'workflow:time-travel:view',
        actionType: 'TIME_TRAVEL_BACK_TO_NODE',
        actionCategory: 'TIME_TRAVEL',
        operatorUserId: 'usr_admin',
        occurredAt: '2026-03-23T10:23:00+08:00',
        details: {},
      },
    ])

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_001' />)

    await screen.findByText('业务正文')
    const runtimeTab = await screen.findByRole('tab', { name: '运行态' })
    fireEvent.mouseDown(runtimeTab)
    fireEvent.click(runtimeTab)

    expect(await screen.findByText('终止高级策略')).toBeInTheDocument()
    expect(screen.getByText('协同轨迹')).toBeInTheDocument()
    expect(screen.getByText('穿越时空轨迹')).toBeInTheDocument()
    expect(await screen.findByText('督办')).toBeInTheDocument()
    expect(await screen.findByText('请补充材料')).toBeInTheDocument()
    expect(await screen.findByText('回退到历史节点')).toBeInTheDocument()
  })

  it('loads approval-sheet detail through the business locator path', async () => {
    workbenchApiMocks.getApprovalSheetDetailByBusiness.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_business_001',
        activeTaskIds: ['task_business_001'],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: true,
      canReturn: false,
    })

    renderWithQuery(
      <WorkbenchTodoDetailPage
        businessType='OA_LEAVE'
        businessId='leave_001'
        backHref='/oa/query'
        backLabel='返回 OA 流程查询'
      />
    )

    await waitFor(() => {
      expect(
        workbenchApiMocks.getApprovalSheetDetailByBusiness
      ).toHaveBeenCalledWith({
        businessType: 'OA_LEAVE',
        businessId: 'leave_001',
      })
    })

    expect(await screen.findByText('业务正文')).toBeInTheDocument()
    expect(screen.getAllByText('请假申请').length).toBeGreaterThan(0)
    expect(
      screen.getByRole('link', { name: '返回 OA 流程查询' })
    ).toHaveAttribute('href', '/oa/query')
  })

  it('shows a business bill link for PLM approval sheets', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_plm_001',
        processKey: 'plm_ecr',
        processName: 'PLM ECR 变更申请',
        businessKey: 'ecr_001',
        businessType: 'PLM_ECR',
        formData: {
          changeTitle: '结构件变更',
          changeReason: '供应替代',
        },
        businessData: {
          billId: 'ecr_001',
          billNo: 'ECR-20260323-000001',
          changeTitle: '结构件变更',
          changeReason: '供应替代',
          affectedProductCode: 'PRD-001',
          priorityLevel: 'HIGH',
        },
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: true,
      canReturn: false,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_plm_001' />)

    expect(await screen.findByText('业务正文')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '查看业务单' })).toHaveAttribute(
      'href',
      '/plm/ecr/$billId'
    )
  })

  it('shows quick task actions and an action timeline in the detail page', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_pending_005',
        taskSemanticMode: 'supervise',
        activeTaskIds: ['task_pending_005'],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: true,
      canReturn: true,
      canAddSign: true,
      canRemoveSign: true,
      canRevoke: true,
      canUrge: true,
      canRead: true,
      canJump: true,
      canTakeBack: true,
      canWakeUp: true,
    })
    workbenchApiMocks.readWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_pending_005',
      status: 'RUNNING',
      nextTasks: [],
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_pending_005' />)

    await screen.findByText('业务正文')
    const traceTab = await screen.findByRole('tab', { name: '轨迹' })
    fireEvent.mouseDown(traceTab)
    fireEvent.click(traceTab)

    expect(await screen.findByText('审批过程')).toBeInTheDocument()
    expect(screen.getAllByText('部门负责人审批').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '加签' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '减签' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '撤销' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '催办' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '督办已阅' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '督办已阅' }))

    await waitFor(() => {
      expect(workbenchApiMocks.readWorkbenchTask).toHaveBeenCalledWith(
        'task_pending_005'
      )
    })
  })

  it('submits the transfer dialog with target user id', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(createWorkbenchTaskDetail({
      taskId: 'task_pending_001',
      activeTaskIds: ['task_pending_001'],
    }))
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
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

    fireEvent.click(await screen.findByRole('button', { name: '转办' }))
    fireEvent.change(screen.getByLabelText('目标用户'), {
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

  it('submits the return dialog with an initiator target strategy', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_return_001',
        activeTaskIds: ['task_return_001'],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: true,
      canReturn: true,
    })
    workbenchApiMocks.returnWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_return_001',
      status: 'RETURNED',
      nextTasks: [],
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_return_001' />)

    fireEvent.click(await screen.findByRole('button', { name: '退回' }))
    const returnDialog = await screen.findByRole('dialog', { name: '退回' })
    fireEvent.change(within(returnDialog).getByRole('combobox'), {
      target: { value: 'INITIATOR' },
    })
    fireEvent.change(within(returnDialog).getByLabelText('退回说明'), {
      target: { value: '退回发起人补充材料' },
    })
    fireEvent.click(within(returnDialog).getByRole('button', { name: '确认退回' }))

    await waitFor(() => {
      expect(workbenchApiMocks.returnWorkbenchTask).toHaveBeenCalledWith(
        'task_return_001',
        expect.objectContaining({
          targetStrategy: 'INITIATOR',
          comment: '退回发起人补充材料',
        })
      )
    })
  })

  it('submits the collaboration dialog with an explicit event type', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(
      createWorkbenchTaskDetail({
        taskId: 'task_collab_001',
        activeTaskIds: ['task_collab_001'],
      })
    )
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: true,
      canReturn: true,
    })
    advancedRuntimeApiMocks.listProcessCollaborationTrace.mockResolvedValue([])
    advancedRuntimeApiMocks.listProcessTerminationAuditTrail.mockResolvedValue([])
    advancedRuntimeApiMocks.getProcessTerminationSnapshot.mockResolvedValue(null)
    advancedRuntimeApiMocks.listProcessTimeTravelTrace.mockResolvedValue([])

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_collab_001' />)

    await screen.findByText('业务正文')
    const runtimeTab = screen.getByRole('tab', { name: '运行态' })
    fireEvent.mouseDown(runtimeTab)
    fireEvent.click(runtimeTab)
    fireEvent.click(await screen.findByRole('button', { name: '发起协同' }))
    const collaborationDialog = await screen.findByRole('dialog', { name: '发起协同' })
    fireEvent.change(within(collaborationDialog).getByRole('combobox'), {
      target: { value: 'SUPERVISE' },
    })
    fireEvent.change(within(collaborationDialog).getByLabelText('协同标题'), {
      target: { value: '请尽快处理' },
    })
    fireEvent.change(within(collaborationDialog).getByLabelText('协同内容'), {
      target: { value: '这是一条督办提醒。' },
    })
    fireEvent.click(within(collaborationDialog).getByRole('button', { name: '发送协同' }))

    await waitFor(() => {
      expect(advancedRuntimeApiMocks.createProcessCollaborationEvent).toHaveBeenCalledWith({
        instanceId: 'pi_001',
        taskId: 'task_collab_001',
        eventType: 'SUPERVISE',
        subject: '请尽快处理',
        content: '这是一条督办提醒。',
        mentionedUserIds: [],
      })
    })
  })

  it('submits node form data when completing a task', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(createWorkbenchTaskDetail({
      taskId: 'task_pending_003',
      taskFormData: {
        approved: true,
        comment: '同意执行',
      },
      formData: { days: 2, reason: '请假' },
      activeTaskIds: ['task_pending_003'],
    }))
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canRejectRoute: true,
      canTransfer: false,
      canReturn: false,
    })
    workbenchApiMocks.completeWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_pending_003',
      status: 'COMPLETED',
      nextTasks: [],
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_pending_003' />)

    fireEvent.click(await screen.findByLabelText('同意通过'))
    fireEvent.change(screen.getByLabelText('审批意见'), {
      target: { value: '同意，继续流转' },
    })
    fireEvent.click(screen.getByRole('button', { name: '完成任务' }))

    await waitFor(() => {
      expect(workbenchApiMocks.completeWorkbenchTask).toHaveBeenCalledWith(
        'task_pending_003',
        {
          action: 'APPROVE',
          comment: '同意，继续流转',
          taskFormData: {
            approved: true,
            comment: '同意，继续流转',
          },
        }
      )
    })
  })

  it('falls back to the process form when no node override is registered', async () => {
    workbenchApiMocks.getWorkbenchTaskDetail.mockResolvedValue(createWorkbenchTaskDetail({
      taskId: 'task_pending_004',
      nodeId: 'start_1',
      nodeName: '流程发起',
      status: 'PENDING',
      effectiveFormKey: 'oa-leave-start-form',
      effectiveFormVersion: '1.0.0',
      nodeFormKey: null,
      nodeFormVersion: null,
      fieldBindings: [],
      taskFormData: {
        days: 2,
        reason: '请假',
      },
      formData: { days: 2, reason: '请假' },
      flowNodes: [
        {
          id: 'start_1',
          type: 'start',
          name: '流程发起',
          position: { x: 100, y: 100 },
        },
      ],
      taskTrace: [
        {
          taskId: 'task_pending_004',
          nodeId: 'start_1',
          nodeName: '流程发起',
          status: 'PENDING',
          assigneeUserId: 'usr_002',
          candidateUserIds: ['usr_002'],
          action: null,
          operatorUserId: null,
          comment: '待处理',
          receiveTime: '2026-03-22T09:00:00+08:00',
          readTime: '2026-03-22T09:02:00+08:00',
          handleStartTime: '2026-03-22T09:03:00+08:00',
          handleEndTime: null,
          handleDurationSeconds: null,
        },
      ],
      activeTaskIds: ['task_pending_004'],
    }))
    workbenchApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: false,
      canRejectRoute: false,
      canTransfer: false,
      canReturn: false,
    })
    workbenchApiMocks.completeWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_pending_004',
      status: 'COMPLETED',
      nextTasks: [],
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_pending_004' />)

    await screen.findByText('当前节点没有独立办理表单，请直接填写审批动作和审批意见。')
    fireEvent.change(await screen.findByLabelText('审批意见'), {
      target: { value: '同意，请按流程执行' },
    })
    fireEvent.click(screen.getByRole('button', { name: '完成任务' }))

    await waitFor(() => {
      expect(workbenchApiMocks.completeWorkbenchTask).toHaveBeenCalledWith(
        'task_pending_004',
        {
          action: 'APPROVE',
          comment: '同意，请按流程执行',
          taskFormData: {
            days: 2,
            reason: '请假',
          },
        }
      )
    })
  })
})
