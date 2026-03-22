import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { sidebarData } from '@/components/layout/data/sidebar-data'
import {
  OAApprovalSheetDetailPage,
  OACommonCreatePage,
  OAExpenseCreatePage,
  OAQueryPage,
  OALeaveCreatePage,
} from './pages'

const { navigateMock, oaApiMocks } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  oaApiMocks: {
    createOALeaveBill: vi.fn(),
    createOAExpenseBill: vi.fn(),
    createOACommonRequestBill: vi.fn(),
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
    useSearch: () => ({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    }),
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
      {actions}
      {children}
    </div>
  ),
}))

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: ({
    title,
    createAction,
    data,
  }: {
    title: string
    createAction?: { label: string; href: string }
    data: Array<{ instanceId: string; businessTitle?: string; billNo?: string }>
  }) => (
    <div>
      <h2>{title}</h2>
      {createAction ? <a href={createAction.href}>{createAction.label}</a> : null}
      {data.map((item) => (
        <div key={item.instanceId}>{item.businessTitle ?? item.billNo}</div>
      ))}
    </div>
  ),
}))

vi.mock('@/lib/api/oa', () => oaApiMocks)
vi.mock('@/lib/api/workbench', () => oaApiMocks)

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
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('submits 请假申请 and jumps to the first task', async () => {
    oaApiMocks.createOALeaveBill.mockResolvedValue(mockLaunchResponse())

    renderWithQuery(<OALeaveCreatePage />)

    fireEvent.change(screen.getByLabelText('请假天数'), {
      target: { value: '3' },
    })
    fireEvent.change(screen.getByLabelText('请假原因'), {
      target: { value: '外出处理事务' },
    })
    fireEvent.click(screen.getByRole('button', { name: '发起请假申请' }))

    await waitFor(() => {
      expect(oaApiMocks.createOALeaveBill).toHaveBeenCalledWith(
        {
          days: 3,
          reason: '外出处理事务',
        },
        expect.objectContaining({
          client: expect.any(Object),
        })
      )
    })

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith({
        to: '/oa/leave/$billId',
        params: { billId: 'bill_001' },
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
    fireEvent.click(screen.getByRole('button', { name: '发起报销申请' }))

    await waitFor(() => {
      expect(oaApiMocks.createOAExpenseBill).toHaveBeenCalledWith(
        {
          amount: 128.5,
          reason: '客户接待',
        },
        expect.objectContaining({
          client: expect.any(Object),
        })
      )
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
    fireEvent.click(screen.getByRole('button', { name: '发起通用申请' }))

    await waitFor(() => {
      expect(oaApiMocks.createOACommonRequestBill).toHaveBeenCalledWith(
        {
          title: '资产借用',
          content: '申请借用一台演示电脑',
        },
        expect.objectContaining({
          client: expect.any(Object),
        })
      )
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
        pageSize: 20,
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
      screen.getByRole('link', { name: '发起 OA 申请' })
    ).toHaveAttribute('href', '/workbench/start')
  })

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
    expect(screen.getByText('LEAVE-001')).toBeInTheDocument()
  })

  it('exposes OA and process-center entries in the sidebar data', () => {
    const oaGroup = sidebarData.navGroups.find(({ title }) => title === 'OA')
    const processGroup = sidebarData.navGroups.find(
      ({ title }) => title === '流程管理'
    )
    const processCenter = processGroup?.items.find(
      (item) => item.title === '流程中心'
    )

    expect(oaGroup?.items.map((item) => item.title)).toEqual([
      '请假申请',
      '报销申请',
      '通用申请',
      'OA 流程查询',
    ])
    expect(processCenter?.items?.map((item) => item.title)).toEqual([
      '待办列表',
      '已办列表',
      '我发起',
      '抄送我',
      '发起流程',
    ])
  })
})
