import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  WorkbenchStartPage,
  WorkbenchTodoDetailPage,
  WorkbenchTodoListPage,
} from './pages'

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
    getApprovalSheetDetailByBusiness: vi.fn(),
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
    activeTaskIds: [taskId],
    ...overrides,
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
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders public tasks as 待认领 in the todo list', async () => {
    renderWithQuery(<WorkbenchTodoListPage />)

    expect(await screen.findByText('公共认领请假审批')).toBeInTheDocument()
    expect(screen.getByText('待认领')).toBeInTheDocument()
  })

  it('shows business entry cards instead of the legacy processKey form', async () => {
    renderWithQuery(<WorkbenchStartPage />)

    expect(screen.queryByLabelText('流程标识')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('业务单号')).not.toBeInTheDocument()
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
      assignmentMode: 'USER',
      candidateUserIds: ['usr_001', 'usr_002'],
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
          candidateUserIds: ['usr_001', 'usr_002'],
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
      canTransfer: false,
      canReturn: true,
    })

    renderWithQuery(<WorkbenchTodoDetailPage taskId='task_claim_001' />)

    expect(await screen.findByText('业务正文')).toBeInTheDocument()
    expect(screen.getByText('业务表单正文')).toBeInTheDocument()
    expect(screen.getByText('LEAVE-001')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '播放回顾' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '暂停回顾' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '继续回顾' })).toBeInTheDocument()
    expect(screen.getAllByText('办理人').length).toBeGreaterThan(0)
    expect(screen.getAllByText('读取时间').length).toBeGreaterThan(0)
    expect(screen.getAllByText('办理开始时间').length).toBeGreaterThan(0)
    expect(screen.getAllByText('办理完成时间').length).toBeGreaterThan(0)
    expect(screen.getAllByText('办理时长').length).toBeGreaterThan(0)
    expect(screen.getAllByText('是否超时').length).toBeGreaterThan(0)
    expect(screen.getAllByText('审批意见摘要').length).toBeGreaterThan(0)

    expect(await screen.findByRole('button', { name: '认领任务' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '退回上一步' })).toBeInTheDocument()
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
    expect(screen.getByText('LEAVE-001')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '返回 OA 流程查询' })
    ).toHaveAttribute('href', '/oa/query')
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
      canTransfer: false,
      canReturn: false,
    })
    workbenchApiMocks.completeWorkbenchTask.mockResolvedValue({
      instanceId: 'pi_001',
      completedTaskId: 'task_pending_004',
      status: 'COMPLETED',
      nextTasks: [],
    })

    const view = renderWithQuery(<WorkbenchTodoDetailPage taskId='task_pending_004' />)

    await waitFor(() => {
      expect(view.container.querySelectorAll('input').length).toBeGreaterThan(0)
    })

    const dayInput = Array.from(view.container.querySelectorAll('input')).find(
      (element) =>
        element.getAttribute('type') === 'number' && !element.hasAttribute('disabled')
    )
    fireEvent.change(dayInput as HTMLElement, {
      target: { value: '3' },
    })
    const reasonInput = Array.from(view.container.querySelectorAll('textarea')).find(
      (element) => !element.hasAttribute('disabled')
    )
    fireEvent.change(reasonInput as HTMLElement, {
      target: { value: '外出处理事务' },
    })
    fireEvent.change(screen.getByLabelText('审批意见'), {
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
            days: 3,
            reason: '外出处理事务',
          },
        }
      )
    })
  })
})
