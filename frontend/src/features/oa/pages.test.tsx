import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  OAApprovalSheetDetailPage,
  OACommonCreatePage,
  OACommonListPage,
  OAExpenseCreatePage,
  OAExpenseListPage,
  OAQueryPage,
  OALeaveCreatePage,
  OALeaveListPage,
} from './pages'

const { navigateMock, routeSearchState, oaApiMocks } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  routeSearchState: {
    current: {
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
      draftId: undefined as string | undefined,
    },
  },
  oaApiMocks: {
    createOALeaveBill: vi.fn(),
    createOAExpenseBill: vi.fn(),
    createOACommonRequestBill: vi.fn(),
    saveOALeaveDraft: vi.fn(),
    updateOALeaveDraft: vi.fn(),
    submitOALeaveDraft: vi.fn(),
    saveOAExpenseDraft: vi.fn(),
    updateOAExpenseDraft: vi.fn(),
    submitOAExpenseDraft: vi.fn(),
    saveOACommonRequestDraft: vi.fn(),
    updateOACommonRequestDraft: vi.fn(),
    submitOACommonRequestDraft: vi.fn(),
    getOALeaveBillDetail: vi.fn(),
    getOAExpenseBillDetail: vi.fn(),
    getOACommonRequestBillDetail: vi.fn(),
    listOALeaveDrafts: vi.fn(),
    listOAExpenseDrafts: vi.fn(),
    listOACommonDrafts: vi.fn(),
    listApprovalSheets: vi.fn(),
    getApprovalSheetDetailByBusiness: vi.fn(),
    getWorkbenchTaskDetail: vi.fn(),
    getWorkbenchTaskActions: vi.fn(),
    claimWorkbenchTask: vi.fn(),
    completeWorkbenchTask: vi.fn(),
    transferWorkbenchTask: vi.fn(),
    returnWorkbenchTask: vi.fn(),
  },
}))

const advancedRuntimeApiMocks = vi.hoisted(() => ({
  getProcessTerminationSnapshot: vi.fn(),
  listProcessCollaborationTrace: vi.fn(),
  listProcessTerminationAuditTrail: vi.fn(),
  listProcessTimeTravelTrace: vi.fn(),
}))

const systemUserApiMocks = vi.hoisted(() => ({
  listSystemUsers: vi.fn(),
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    to,
    search,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to?: string; search?: Record<string, unknown> }) => {
    const query = search && Object.keys(search).length > 0
      ? `?${new URLSearchParams(
          Object.entries(search).reduce<Record<string, string>>((acc, [key, value]) => {
            if (value != null) {
              acc[key] = String(value)
            }
            return acc
          }, {})
        ).toString()}`
      : ''

    return (
      <a href={`${to ?? ''}${query}`} {...props}>
        {children}
      </a>
    )
  },
  useNavigate: () => navigateMock,
  getRouteApi: () => ({
    useSearch: () => routeSearchState.current,
    useNavigate: () => navigateMock,
  }),
}))

vi.mock('@/stores/auth-store', () => ({
  useAuthStore: (
    selector: (state: { currentUser: { aiCapabilities: string[] } }) => unknown
  ) =>
    selector({
      currentUser: {
        aiCapabilities: ['ai:copilot:open'],
      },
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
      {actions}
      {children}
    </div>
  ),
}))

vi.mock('@/features/shared/pro-table', () => ({
  ProTable: ({
    title,
    extraActions,
    createActionNode,
    data,
    supportsBoard,
  }: {
    title: string
    extraActions?: React.ReactNode
    createActionNode?: React.ReactNode
    data: Array<{ instanceId: string; businessTitle?: string; billNo?: string }>
    supportsBoard?: boolean
  }) => (
    <div>
      <h2>{title}</h2>
      {supportsBoard ? (
        <>
          <button type='button'>表格</button>
          <button type='button'>看板</button>
        </>
      ) : null}
      {extraActions}
      {createActionNode}
      {data.map((item) => (
        <div key={item.instanceId}>{item.businessTitle ?? item.billNo}</div>
      ))}
    </div>
  ),
}))

vi.mock('@/lib/api/oa', () => oaApiMocks)
vi.mock('@/lib/api/system-users', () => systemUserApiMocks)
vi.mock('@/lib/api/workbench', () => ({
  ...oaApiMocks,
  WORKBENCH_RUNTIME_ENDPOINTS: {
    approvalSheetsPage: '/process-runtime/approval-sheets/page',
    tasksPage: '/process-runtime/tasks/page',
  },
}))
vi.mock('@/lib/api/process-runtime-advanced', () => ({
  ...advancedRuntimeApiMocks,
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

function mockLaunchResponse(taskId = 'task_001') {
  return {
    billId: 'bill_001',
    billNo: 'OA-20260322-001',
    processInstanceId: 'pi_001',
    activeTasks: [
      {
        taskId,
        nodeId: 'approve_manager',
        nodeName: '部门负责人审批',
        status: 'PENDING',
        assignmentMode: 'USER',
        candidateUserIds: ['usr_002'],
        assigneeUserId: 'usr_002',
      },
    ],
  }
}

function mockApprovalSheetDetail() {
  return {
    taskId: 'task_001',
    instanceId: 'pi_001',
    processDefinitionId: 'pd_001',
    processKey: 'oa_leave',
    processName: '请假审批',
    businessKey: 'leave_001',
    businessType: 'OA_LEAVE',
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
    processFormKey: 'oa-leave-start-form',
    processFormVersion: '1.0.0',
    effectiveFormKey: 'oa-leave-approve-form',
    effectiveFormVersion: '1.0.0',
    nodeFormKey: 'oa-leave-approve-form',
    nodeFormVersion: '1.0.0',
    fieldBindings: [],
    taskFormData: { approved: true },
    activeTaskIds: ['task_001'],
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
    flowNodes: [],
    flowEdges: [],
    instanceEvents: [],
    taskTrace: [],
  }
}

function mockApprovalSheetPage() {
  return {
    page: 1,
    pageSize: 20,
    total: 1,
    pages: 1,
    groups: [],
    records: [
      {
        instanceId: 'pi_001',
        processDefinitionId: 'pd_001',
        processKey: 'oa_leave',
        processName: '请假审批',
        businessId: 'leave_001',
        businessType: 'OA_LEAVE',
        billNo: 'LEAVE-001',
        businessTitle: '请假申请 · 外出处理事务',
        initiatorUserId: 'usr_001',
        currentNodeName: '部门负责人审批',
        currentTaskId: 'task_001',
        currentTaskStatus: 'RUNNING',
        currentAssigneeUserId: 'usr_002',
        instanceStatus: 'RUNNING',
        latestAction: 'START',
        latestOperatorUserId: 'usr_001',
        createdAt: '2026-03-22T09:00:00+08:00',
        updatedAt: '2026-03-22T09:10:00+08:00',
        completedAt: null,
      },
    ],
  }
}

describe('oa pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeSearchState.current = {
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
      draftId: undefined,
    }
    oaApiMocks.listOALeaveDrafts.mockResolvedValue([])
    oaApiMocks.listOAExpenseDrafts.mockResolvedValue([])
    oaApiMocks.listOACommonDrafts.mockResolvedValue([])
    advancedRuntimeApiMocks.getProcessTerminationSnapshot.mockResolvedValue(null)
    advancedRuntimeApiMocks.listProcessTerminationAuditTrail.mockResolvedValue([])
    advancedRuntimeApiMocks.listProcessCollaborationTrace.mockResolvedValue([])
    advancedRuntimeApiMocks.listProcessTimeTravelTrace.mockResolvedValue([])
    systemUserApiMocks.listSystemUsers.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 2,
      pages: 1,
      groups: [],
      records: [
        {
          userId: 'usr_002',
          displayName: '李四',
          username: 'lisi',
          mobile: '13800000002',
          email: 'lisi@westflow.cn',
          departmentName: '人力资源部',
          postName: '部门负责人',
          status: 'ENABLED',
          createdAt: '2026-03-22T09:00:00+08:00',
        },
        {
          userId: 'usr_005',
          displayName: '王主管',
          username: 'wangzhuguan',
          mobile: '13800000005',
          email: 'wangzhuguan@westflow.cn',
          departmentName: '运营中心',
          postName: '总监',
          status: 'ENABLED',
          createdAt: '2026-03-22T09:00:00+08:00',
        },
      ],
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('submits 请假申请 and jumps to the first task', async () => {
    oaApiMocks.createOALeaveBill.mockResolvedValue(mockLaunchResponse())

    renderWithQuery(<OALeaveCreatePage />)

    fireEvent.click(screen.getByRole('combobox', { name: '请假类型' }))
    fireEvent.click(screen.getByRole('option', { name: '病假' }))
    fireEvent.change(screen.getByLabelText('请假天数'), {
      target: { value: '3' },
    })
    fireEvent.click(screen.getByRole('switch', { name: '是否紧急' }))
    fireEvent.click(screen.getByLabelText('直属负责人'))
    const managerOptions = await screen.findAllByText('王主管')
    fireEvent.click(managerOptions[managerOptions.length - 1]!)
    fireEvent.change(screen.getByLabelText('请假原因'), {
      target: { value: '外出处理事务' },
    })
    fireEvent.click(screen.getByRole('button', { name: '提交请假申请' }))

    await waitFor(() => {
      expect(oaApiMocks.createOALeaveBill).toHaveBeenCalledWith({
        leaveType: 'SICK',
        days: 3,
        reason: '外出处理事务',
        urgent: true,
        managerUserId: 'usr_005',
      })
    })

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith({
        to: '/oa/leave/$billId',
        params: { billId: 'bill_001' },
      })
    })
  })

  it('saves 请假草稿 and keeps editing route with draftId', async () => {
    oaApiMocks.saveOALeaveDraft.mockResolvedValue({
      billId: 'leave_draft_001',
      billNo: 'LEAVE-DRAFT-001',
      processInstanceId: null,
      activeTasks: [],
    })

    renderWithQuery(<OALeaveCreatePage />)

    fireEvent.change(screen.getByLabelText('请假天数'), {
      target: { value: '2' },
    })
    fireEvent.click(screen.getByLabelText('直属负责人'))
    const managerOptions = await screen.findAllByText('李四')
    fireEvent.click(managerOptions[managerOptions.length - 1]!)
    fireEvent.change(screen.getByLabelText('请假原因'), {
      target: { value: '家中有事' },
    })
    fireEvent.click(screen.getByRole('button', { name: '暂存草稿' }))

    await waitFor(() => {
      expect(oaApiMocks.saveOALeaveDraft).toHaveBeenCalledWith({
        leaveType: 'ANNUAL',
        days: 2,
        reason: '家中有事',
        urgent: false,
        managerUserId: 'usr_002',
      })
    })

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith({
        to: '/oa/leave/create',
        search: { draftId: 'leave_draft_001' },
        replace: true,
      })
    })
  })

  it('loads leave draft and submits through draft endpoint', async () => {
    routeSearchState.current = {
      ...routeSearchState.current,
      draftId: 'leave_draft_002',
    }
    oaApiMocks.getOALeaveBillDetail.mockResolvedValue({
      billId: 'leave_draft_002',
      billNo: 'LEAVE-DRAFT-002',
      sceneCode: 'default',
      leaveType: 'SICK',
      days: 5,
      reason: '草稿原因',
      urgent: true,
      managerUserId: 'usr_005',
      processInstanceId: null,
      status: 'DRAFT',
    })
    oaApiMocks.submitOALeaveDraft.mockResolvedValue(mockLaunchResponse())

    renderWithQuery(<OALeaveCreatePage />)

    await waitFor(() => {
      expect(oaApiMocks.getOALeaveBillDetail).toHaveBeenCalledWith('leave_draft_002')
    })

    await waitFor(() => {
      expect(screen.getByLabelText('请假天数')).toHaveValue(5)
    })

    fireEvent.click(screen.getByRole('button', { name: '提交请假申请' }))

    await waitFor(() => {
      expect(oaApiMocks.submitOALeaveDraft).toHaveBeenCalledWith('leave_draft_002', {
        leaveType: 'SICK',
        days: 5,
        reason: '草稿原因',
        urgent: true,
        managerUserId: 'usr_005',
      })
    })
  })

  it('submits 报销申请 to the expense launch endpoint', async () => {
    oaApiMocks.createOAExpenseBill.mockResolvedValue(mockLaunchResponse('task_002'))

    renderWithQuery(<OAExpenseCreatePage />)

    fireEvent.change(screen.getByLabelText('报销金额'), {
      target: { value: '128.5' },
    })
    fireEvent.change(screen.getByLabelText('报销事由'), {
      target: { value: '客户接待' },
    })
    fireEvent.click(screen.getByRole('button', { name: '提交报销申请' }))

    await waitFor(() => {
      expect(oaApiMocks.createOAExpenseBill).toHaveBeenCalledWith({
        amount: 128.5,
        reason: '客户接待',
      })
    })

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith({
        to: '/oa/expense/$billId',
        params: { billId: 'bill_001' },
      })
    })
  })

  it('submits 通用申请 to the common launch endpoint', async () => {
    oaApiMocks.createOACommonRequestBill.mockResolvedValue(
      mockLaunchResponse('task_003')
    )

    renderWithQuery(<OACommonCreatePage />)

    fireEvent.change(screen.getByLabelText('申请标题'), {
      target: { value: '资产借用' },
    })
    fireEvent.change(screen.getByLabelText('申请内容'), {
      target: { value: '申请借用一台演示电脑' },
    })
    fireEvent.click(screen.getByRole('button', { name: '提交通用申请' }))

    await waitFor(() => {
      expect(oaApiMocks.createOACommonRequestBill).toHaveBeenCalledWith({
        title: '资产借用',
        content: '申请借用一台演示电脑',
      })
    })

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith({
        to: '/oa/common/$billId',
        params: { billId: 'bill_001' },
      })
    })
  })

  it('loads OA query through the approval-sheet paging endpoint', async () => {
    oaApiMocks.listApprovalSheets.mockResolvedValue(mockApprovalSheetPage())

    renderWithQuery(<OAQueryPage />)

    await waitFor(() => {
      expect(oaApiMocks.listApprovalSheets).toHaveBeenCalledWith({
        page: 1,
        pageSize: 200,
        keyword: '',
        filters: [],
        sorts: [],
        groups: [],
        view: 'INITIATED',
        businessTypes: ['OA_LEAVE', 'OA_EXPENSE', 'OA_COMMON'],
      })
    })

    expect(await screen.findByText('OA 流程查询')).toBeInTheDocument()
    expect(screen.getByText('请假申请 · 外出处理事务')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '用 AI 解读 OA 查询结果' })
    ).toHaveAttribute('data-source-route', '/oa/query')
    expect(
      screen.getByRole('link', { name: '发起 OA 申请' })
    ).toHaveAttribute('href', '/workbench/start')
  })

  it.each([
    {
      name: 'OALeaveListPage',
      renderPage: OALeaveListPage,
      businessTypes: ['OA_LEAVE'],
      title: '请假申请列表',
      createLabel: '发起请假申请',
      createHref: '/oa/leave/create',
      copilotSourceRoute: '/oa/leave/list',
    },
    {
      name: 'OAExpenseListPage',
      renderPage: OAExpenseListPage,
      businessTypes: ['OA_EXPENSE'],
      title: '报销申请列表',
      createLabel: '发起报销申请',
      createHref: '/oa/expense/create',
      copilotSourceRoute: '/oa/expense/list',
    },
    {
      name: 'OACommonListPage',
      renderPage: OACommonListPage,
      businessTypes: ['OA_COMMON'],
      title: '通用申请列表',
      createLabel: '发起通用申请',
      createHref: '/oa/common/create',
      copilotSourceRoute: '/oa/common/list',
    },
  ] as const)(
    'loads business list on $name with filtered approval sheets',
    async ({ renderPage, businessTypes, title, createLabel, createHref, copilotSourceRoute }) => {
      oaApiMocks.listApprovalSheets.mockResolvedValue(mockApprovalSheetPage())
      oaApiMocks.listOALeaveDrafts.mockResolvedValue(
        businessTypes[0] === 'OA_LEAVE'
          ? [
              {
                billId: 'leave_draft_001',
                billNo: 'LEAVE-DRAFT-001',
                businessType: 'OA_LEAVE',
                businessTitle: '请假草稿',
                sceneCode: 'default',
                processInstanceId: null,
                status: 'DRAFT',
                creatorUserId: 'usr_001',
                createdAt: '2026-03-22T08:00:00+08:00',
                updatedAt: '2026-03-22T08:30:00+08:00',
              },
            ]
          : []
      )

      const RenderPage = renderPage
      renderWithQuery(<RenderPage />)

      await waitFor(() => {
        expect(oaApiMocks.listApprovalSheets).toHaveBeenCalledWith({
          page: 1,
          pageSize: 200,
          keyword: '',
          filters: [],
          sorts: [],
          groups: [],
          view: 'INITIATED',
          businessTypes,
        })
      })

      expect(await screen.findByText(title)).toBeInTheDocument()
      if (businessTypes[0] === 'OA_LEAVE') {
        expect(screen.getByText('请假草稿')).toBeInTheDocument()
      }
      expect(screen.getByRole('link', { name: createLabel })).toHaveAttribute(
        'href',
        createHref
      )
      expect(
        screen.getByRole('button', { name: '用 AI 解读当前业务列表' })
      ).toHaveAttribute('data-source-route', copilotSourceRoute)
    }
  )

  it('loads OA approval sheet detail by business locator', async () => {
    oaApiMocks.getApprovalSheetDetailByBusiness.mockResolvedValue(
      mockApprovalSheetDetail()
    )
    oaApiMocks.getWorkbenchTaskActions.mockResolvedValue({
      canClaim: false,
      canApprove: true,
      canReject: true,
      canTransfer: false,
      canReturn: false,
    })

    renderWithQuery(
      <OAApprovalSheetDetailPage businessType='OA_LEAVE' billId='leave_001' />
    )

    await waitFor(() => {
      expect(oaApiMocks.getApprovalSheetDetailByBusiness).toHaveBeenCalledWith(
        {
          businessType: 'OA_LEAVE',
          businessId: 'leave_001',
        }
      )
    })

    expect(await screen.findByText('审批单详情')).toBeInTheDocument()
    expect(screen.getByText('业务正文')).toBeInTheDocument()
    expect(screen.getByText('请假申请')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '用 AI 解读当前审批单' })
    ).toHaveAttribute('data-source-route', '/oa/leave/leave_001')
  })

  it('exposes OA launch, query, and Copilot context links as a smoke path', async () => {
    oaApiMocks.listApprovalSheets.mockResolvedValue(mockApprovalSheetPage())

    renderWithQuery(
      <>
        <OALeaveCreatePage />
        <OAQueryPage />
      </>
    )

    expect(
      screen.getByRole('button', { name: '提交请假申请' })
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '用 AI 辅助填写请假申请' })
    ).toHaveAttribute('data-source-route', '/oa/leave/create')
    expect(await screen.findByText('OA 流程查询')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '用 AI 解读 OA 查询结果' })
    ).toHaveAttribute('data-source-route', '/oa/query')
    expect(
      screen.getByRole('link', { name: '发起 OA 申请' })
    ).toHaveAttribute('href', '/workbench/start')
  })

  it.each([
    {
      name: 'OALeaveCreatePage',
      buttonLabel: '用 AI 辅助填写请假申请',
      sourceRoute: '/oa/leave/create',
      renderPage: OALeaveCreatePage,
    },
    {
      name: 'OAExpenseCreatePage',
      buttonLabel: '用 AI 辅助填写报销申请',
      sourceRoute: '/oa/expense/create',
      renderPage: OAExpenseCreatePage,
    },
    {
      name: 'OACommonCreatePage',
      buttonLabel: '用 AI 辅助填写通用申请',
      sourceRoute: '/oa/common/create',
      renderPage: OACommonCreatePage,
    },
  ] as const)('renders Copilot entry on $name', async ({ buttonLabel, sourceRoute, renderPage }) => {
    const RenderPage = renderPage

    renderWithQuery(<RenderPage />)

    if (RenderPage === OALeaveCreatePage) {
      await waitFor(() => {
        expect(systemUserApiMocks.listSystemUsers).toHaveBeenCalled()
      })
    }

    expect(screen.getByRole('button', { name: buttonLabel })).toHaveAttribute(
      'data-source-route',
      sourceRoute
    )
  })

})
