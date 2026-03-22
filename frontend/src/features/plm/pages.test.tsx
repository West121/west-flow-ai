import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  PLMECOExecutionBillDetailPage,
  PLMECRCreatePage,
  PLMECRRequestBillDetailPage,
  PLMHomePage,
  PLMMaterialChangeBillDetailPage,
  PLMQueryPage,
} from './pages'

const { navigateMock, plmApiMocks, routeSearchMock } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  routeSearchMock: vi.fn(),
  plmApiMocks: {
    createPLMECRRequest: vi.fn(),
    createPLMECOExecution: vi.fn(),
    createPLMMaterialChangeRequest: vi.fn(),
    getPLMECRRequestDetail: vi.fn(),
    getPLMECOExecutionDetail: vi.fn(),
    getPLMMaterialChangeDetail: vi.fn(),
    listPLMApprovalSheets: vi.fn(),
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
    useSearch: routeSearchMock,
    useNavigate: () => navigateMock,
    useParams: () => ({ billId: 'plm_001' }),
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
    data: Array<{
      instanceId: string
      businessTitle?: string | null
      billNo?: string | null
    }>
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

vi.mock('@/features/workbench/pages', () => ({
  WorkbenchTodoDetailPage: ({
    businessType,
    businessId,
  }: {
    businessType: string
    businessId: string
  }) => (
    <div>
      <h2>统一审批单详情</h2>
      <p>
        {businessType}:{businessId}
      </p>
    </div>
  ),
}))

vi.mock('@/lib/api/plm', () => plmApiMocks)

function renderWithQuery(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
  )
}

function createLaunchResponse(taskId = 'task_plm_001') {
  return {
    billId: 'plm_001',
    billNo: 'PLM-20260323-001',
    processInstanceId: 'pi_plm_001',
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

function createApprovalSheetRecord(overrides: Record<string, unknown> = {}) {
  return {
    instanceId: typeof overrides.instanceId === 'string' ? overrides.instanceId : 'pi_plm_001',
    processDefinitionId: 'pd_plm_001',
    processKey: 'plm_ecr',
    processName: 'ECR 变更申请',
    businessId: 'plm_001',
    businessType: 'PLM_ECR',
    billNo: 'PLM-20260323-001',
    businessTitle: '结构件变更',
    initiatorUserId: 'usr_001',
    currentNodeName: '部门负责人审批',
    currentTaskId: 'task_plm_001',
    currentTaskStatus: 'RUNNING',
    currentAssigneeUserId: 'usr_002',
    instanceStatus: 'RUNNING',
    latestAction: 'START',
    latestOperatorUserId: 'usr_001',
    createdAt: '2026-03-23T09:00:00+08:00',
    updatedAt: '2026-03-23T09:10:00+08:00',
    completedAt: null,
    ...overrides,
  }
}

describe('plm pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeSearchMock.mockReturnValue({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the PLM launch center cards', () => {
    renderWithQuery(<PLMHomePage />)

    expect(screen.getByText('PLM 发起中心')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '发起 ECR' })).toHaveAttribute(
      'href',
      '/plm/ecr/create'
    )
    expect(screen.getByRole('link', { name: '发起 ECO' })).toHaveAttribute(
      'href',
      '/plm/eco/create'
    )
    expect(
      screen.getByRole('link', { name: '发起物料变更' })
    ).toHaveAttribute('href', '/plm/material-master/create')
  })

  it('submits ECR request and jumps to the business detail page', async () => {
    plmApiMocks.createPLMECRRequest.mockResolvedValue(createLaunchResponse())

    renderWithQuery(<PLMECRCreatePage />)

    fireEvent.change(screen.getByLabelText('变更标题'), {
      target: { value: '结构件变更' },
    })
    fireEvent.change(screen.getByLabelText('变更原因'), {
      target: { value: '供应替代' },
    })
    fireEvent.change(screen.getByLabelText('影响等级'), {
      target: { value: 'HIGH' },
    })
    fireEvent.click(screen.getByRole('button', { name: '发起 ECR 变更申请' }))

    await waitFor(() => {
      expect(plmApiMocks.createPLMECRRequest).toHaveBeenCalledWith(
        {
          changeTitle: '结构件变更',
          changeReason: '供应替代',
          impactLevel: 'HIGH',
        },
        expect.objectContaining({
          client: expect.any(Object),
        })
      )
    })
    expect(navigateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        to: '/plm/ecr/$billId',
        params: { billId: 'plm_001' },
      })
    )
  })

  it('renders the PLM approval sheet query page', async () => {
    plmApiMocks.listPLMApprovalSheets.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [createApprovalSheetRecord()],
    })

    renderWithQuery(<PLMQueryPage />)

    expect(await screen.findByText('PLM 流程查询')).toBeInTheDocument()
    expect(await screen.findByText('结构件变更')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '发起 PLM 申请' })
    ).toHaveAttribute('href', '/plm/start')
  })

  it('renders the PLM ECR detail page shell', async () => {
    plmApiMocks.getPLMECRRequestDetail.mockResolvedValue({
      billId: 'plm_001',
      billNo: 'PLM-20260323-001',
      processInstanceId: 'pi_plm_001',
      activeTasks: [{ taskId: 'task_plm_001' }],
      businessData: {
        billId: 'plm_001',
        billNo: 'PLM-20260323-001',
        changeTitle: '结构件变更',
        changeReason: '供应替代',
      },
      formData: {
        changeTitle: '结构件变更',
        changeReason: '供应替代',
      },
      processFormKey: 'plm-ecr-start-form',
      processFormVersion: '1.0.0',
      effectiveFormKey: 'plm-ecr-start-form',
      effectiveFormVersion: '1.0.0',
      nodeFormKey: null,
      nodeFormVersion: null,
      fieldBindings: [],
      taskFormData: null,
      instanceStatus: 'RUNNING',
      status: 'PENDING',
      assignmentMode: 'USER',
      candidateUserIds: ['usr_002'],
      assigneeUserId: 'usr_002',
      nodeId: 'approve_manager',
      nodeName: '部门负责人审批',
      action: null,
      operatorUserId: null,
      comment: null,
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
      completedAt: null,
      receiveTime: '2026-03-23T09:00:00+08:00',
      readTime: '2026-03-23T09:02:00+08:00',
      handleStartTime: '2026-03-23T09:03:00+08:00',
      handleEndTime: null,
      handleDurationSeconds: null,
      taskTrace: [],
      flowNodes: [],
      flowEdges: [],
      instanceEvents: [],
      automationActionTrace: [],
      notificationSendRecords: [],
      activeTaskIds: ['task_plm_001'],
    })

    renderWithQuery(<PLMECRRequestBillDetailPage billId='plm_001' />)

    expect(await screen.findByText('统一审批单详情')).toBeInTheDocument()
    expect(await screen.findByText('PLM_ECR:plm_001')).toBeInTheDocument()
  })

  it('renders the PLM ECO and material detail shells', async () => {
    plmApiMocks.getPLMECOExecutionDetail.mockResolvedValue({
      billId: 'plm_002',
      billNo: 'PLM-ECO-002',
      processInstanceId: 'pi_plm_002',
      activeTasks: [],
      businessData: { billId: 'plm_002', billNo: 'PLM-ECO-002' },
      formData: {},
      processFormKey: 'plm-eco-start-form',
      processFormVersion: '1.0.0',
      effectiveFormKey: 'plm-eco-start-form',
      effectiveFormVersion: '1.0.0',
      nodeFormKey: null,
      nodeFormVersion: null,
      fieldBindings: [],
      taskFormData: null,
      instanceStatus: 'RUNNING',
      status: 'PENDING',
      assignmentMode: 'USER',
      candidateUserIds: [],
      assigneeUserId: null,
      nodeId: 'approve_manager',
      nodeName: '部门负责人审批',
      action: null,
      operatorUserId: null,
      comment: null,
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
      completedAt: null,
      receiveTime: null,
      readTime: null,
      handleStartTime: null,
      handleEndTime: null,
      handleDurationSeconds: null,
      taskTrace: [],
      flowNodes: [],
      flowEdges: [],
      instanceEvents: [],
      automationActionTrace: [],
      notificationSendRecords: [],
      activeTaskIds: [],
    })
    plmApiMocks.getPLMMaterialChangeDetail.mockResolvedValue({
      billId: 'plm_003',
      billNo: 'PLM-MATERIAL-003',
      processInstanceId: 'pi_plm_003',
      activeTasks: [],
      businessData: { billId: 'plm_003', billNo: 'PLM-MATERIAL-003' },
      formData: {},
      processFormKey: 'plm-material-start-form',
      processFormVersion: '1.0.0',
      effectiveFormKey: 'plm-material-start-form',
      effectiveFormVersion: '1.0.0',
      nodeFormKey: null,
      nodeFormVersion: null,
      fieldBindings: [],
      taskFormData: null,
      instanceStatus: 'RUNNING',
      status: 'PENDING',
      assignmentMode: 'USER',
      candidateUserIds: [],
      assigneeUserId: null,
      nodeId: 'approve_manager',
      nodeName: '部门负责人审批',
      action: null,
      operatorUserId: null,
      comment: null,
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
      completedAt: null,
      receiveTime: null,
      readTime: null,
      handleStartTime: null,
      handleEndTime: null,
      handleDurationSeconds: null,
      taskTrace: [],
      flowNodes: [],
      flowEdges: [],
      instanceEvents: [],
      automationActionTrace: [],
      notificationSendRecords: [],
      activeTaskIds: [],
    })

    renderWithQuery(<PLMECOExecutionBillDetailPage billId='plm_002' />)
    renderWithQuery(<PLMMaterialChangeBillDetailPage billId='plm_003' />)

    expect(await screen.findByText('PLM_ECO:plm_002')).toBeInTheDocument()
    expect(await screen.findByText('PLM_MATERIAL:plm_003')).toBeInTheDocument()
  })
})
