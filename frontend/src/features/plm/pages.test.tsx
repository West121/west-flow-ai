import { Children } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  PLMECOListPage,
  PLMECOCreatePage,
  PLMECOExecutionBillDetailPage,
  PLMECRListPage,
  PLMECRCreatePage,
  PLMECRRequestBillDetailPage,
  PLMHomePage,
  PLMMaterialChangeListPage,
  PLMMaterialChangeBillDetailPage,
  PLMMaterialChangeCreatePage,
  PLMQueryPage,
} from './pages'

const {
  navigateMock,
  plmApiMocks,
  routeSearchMock,
  workbenchApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  routeSearchMock: vi.fn(),
  plmApiMocks: {
    createPLMECRRequest: vi.fn(),
    createPLMECOExecution: vi.fn(),
    createPLMMaterialChangeRequest: vi.fn(),
    listPLMECRRequests: vi.fn(),
    listPLMECOExecutions: vi.fn(),
    listPLMMaterialChangeRequests: vi.fn(),
    listPLMApprovalSheets: vi.fn(),
    getPLMECRRequestDetail: vi.fn(),
    getPLMECOExecutionDetail: vi.fn(),
    getPLMMaterialChangeDetail: vi.fn(),
  },
  workbenchApiMocks: {
    getApprovalSheetDetailByBusiness: vi.fn(),
  },
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    to,
    search,
    children,
    ...props
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & {
    to?: string
    search?: Record<string, unknown>
  }) => {
    const query =
      search && Object.keys(search).length > 0
        ? `?${new URLSearchParams(
            Object.entries(search).reduce<Record<string, string>>(
              (acc, [key, value]) => {
                if (value != null) {
                  acc[key] = String(value)
                }
                return acc
              },
              {}
            )
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
      {actions ? <div>{Children.toArray(actions)}</div> : null}
      {children}
    </div>
  ),
}))

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: ({
    title,
    createAction,
    extraActions,
    data,
    total,
  }: {
    title: string
    createAction?: { label: string; href: string }
    extraActions?: React.ReactNode
    total?: number
    data: Array<{
      instanceId?: string | null
      billId?: string | null
      businessTitle?: string | null
      billNo?: string | null
    }>
  }) => (
    <div>
      <h2>{title}</h2>
      {extraActions ? <>{Children.toArray(extraActions)}</> : null}
      {createAction ? <a href={createAction.href}>{createAction.label}</a> : null}
      <span>total:{total ?? data.length}</span>
      {data.map((item) => (
        <div key={item.instanceId ?? item.billId ?? item.billNo}>
          {item.businessTitle ?? item.billNo}
        </div>
      ))}
    </div>
  ),
}))

vi.mock('@/lib/api/plm', () => plmApiMocks)
vi.mock('@/lib/api/workbench', () => workbenchApiMocks)
vi.mock('@/stores/auth-store', () => ({
  useAuthStore: (selector: (state: { currentUser: { aiCapabilities: string[] } }) => unknown) =>
    selector({
      currentUser: {
        aiCapabilities: ['ai:copilot:open'],
      },
    }),
}))

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
    instanceId:
      typeof overrides.instanceId === 'string' ? overrides.instanceId : 'pi_plm_001',
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

function createApprovalDetail(overrides: Record<string, unknown> = {}) {
  return {
    taskId: 'task_plm_001',
    instanceId: 'pi_plm_001',
    processDefinitionId: 'pd_plm_001',
    processKey: 'plm_ecr',
    processName: 'PLM ECR 变更申请',
    businessKey: 'plm_001',
    businessType: 'PLM_ECR',
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
    processFormKey: 'plm-ecr-start-form',
    processFormVersion: '1.0.0',
    effectiveFormKey: 'plm-ecr-start-form',
    effectiveFormVersion: '1.0.0',
    nodeFormKey: null,
    nodeFormVersion: null,
    fieldBindings: [],
    taskFormData: null,
    createdAt: '2026-03-23T09:00:00+08:00',
    updatedAt: '2026-03-23T09:10:00+08:00',
    completedAt: null,
    receiveTime: '2026-03-23T09:01:00+08:00',
    readTime: '2026-03-23T09:02:00+08:00',
    handleStartTime: '2026-03-23T09:03:00+08:00',
    handleEndTime: null,
    handleDurationSeconds: null,
    instanceStatus: 'RUNNING',
    formData: {},
    businessData: {},
    flowNodes: [],
    flowEdges: [],
    instanceEvents: [],
    taskTrace: [],
    automationActionTrace: [],
    notificationSendRecords: [],
    activeTaskIds: ['task_plm_001'],
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
    expect(
      screen.getByRole('button', { name: '用 AI 推荐 PLM 入口' })
    ).toHaveAttribute('data-source-route', '/plm/start')
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

  it('renders PLM business list pages', async () => {
    plmApiMocks.listPLMECRRequests.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          billId: 'ecr_001',
          billNo: 'PLM-ECR-001',
          sceneCode: 'default',
          changeTitle: '结构件替换',
          affectedProductCode: 'PRD-001',
          priorityLevel: 'HIGH',
          processInstanceId: 'pi_ecr_001',
          status: 'RUNNING',
          creatorUserId: 'usr_001',
          createdAt: '2026-03-23T09:00:00+08:00',
          updatedAt: '2026-03-23T09:10:00+08:00',
        },
      ],
    })
    plmApiMocks.listPLMECOExecutions.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          billId: 'eco_001',
          billNo: 'PLM-ECO-001',
          sceneCode: 'default',
          executionTitle: 'ECO 执行',
          effectiveDate: '2026-04-01',
          changeReason: '量产切换',
          processInstanceId: 'pi_eco_001',
          status: 'RUNNING',
          creatorUserId: 'usr_001',
          createdAt: '2026-03-23T09:00:00+08:00',
          updatedAt: '2026-03-23T09:10:00+08:00',
        },
      ],
    })
    plmApiMocks.listPLMMaterialChangeRequests.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          billId: 'material_001',
          billNo: 'PLM-MATERIAL-001',
          sceneCode: 'default',
          materialCode: 'MAT-001',
          materialName: '主板总成',
          changeType: 'ATTRIBUTE_UPDATE',
          changeReason: '替换物料编码',
          processInstanceId: 'pi_material_001',
          status: 'RUNNING',
          creatorUserId: 'usr_001',
          createdAt: '2026-03-23T09:00:00+08:00',
          updatedAt: '2026-03-23T09:10:00+08:00',
        },
      ],
    })

    renderWithQuery(
      <>
        <PLMECRListPage
          search={{
            page: 1,
            pageSize: 20,
            keyword: '',
            filters: [],
            sorts: [],
            groups: [],
          }}
          navigate={navigateMock}
        />
        <PLMECOListPage
          search={{
            page: 1,
            pageSize: 20,
            keyword: '',
            filters: [],
            sorts: [],
            groups: [],
          }}
          navigate={navigateMock}
        />
        <PLMMaterialChangeListPage
          search={{
            page: 1,
            pageSize: 20,
            keyword: '',
            filters: [],
            sorts: [],
            groups: [],
          }}
          navigate={navigateMock}
        />
      </>
    )

    expect(await screen.findByText('ECR 变更申请列表')).toBeInTheDocument()
    expect(await screen.findByText('ECO 变更执行列表')).toBeInTheDocument()
    expect(await screen.findByText('物料主数据变更列表')).toBeInTheDocument()
    expect(await screen.findByText('PLM-ECR-001')).toBeInTheDocument()
    expect(await screen.findByText('PLM-ECO-001')).toBeInTheDocument()
    expect(await screen.findByText('PLM-MATERIAL-001')).toBeInTheDocument()
  })

  it('submits ECR request and jumps to the business detail page', async () => {
    plmApiMocks.createPLMECRRequest.mockResolvedValue(createLaunchResponse('ecr_001'))

    renderWithQuery(<PLMECRCreatePage />)
    expect(
      screen.getByRole('button', { name: '用 AI 辅助填写 ECR' })
    ).toHaveAttribute('data-source-route', '/plm/ecr/create')
    fireEvent.change(screen.getByLabelText('变更标题'), {
      target: { value: '结构件变更' },
    })
    fireEvent.change(screen.getByLabelText('变更原因'), {
      target: { value: '供应替代' },
    })
    fireEvent.change(screen.getByLabelText('影响产品编码'), {
      target: { value: 'PRD-001' },
    })
    fireEvent.change(screen.getByLabelText('优先级'), {
      target: { value: 'HIGH' },
    })
    fireEvent.click(screen.getByRole('button', { name: '发起 ECR 变更申请' }))

    await waitFor(() => {
      expect(plmApiMocks.createPLMECRRequest).toHaveBeenCalled()
    })

    expect(plmApiMocks.createPLMECRRequest.mock.calls[0][0]).toEqual({
      changeTitle: '结构件变更',
      changeReason: '供应替代',
      affectedProductCode: 'PRD-001',
      priorityLevel: 'HIGH',
    })
    expect(navigateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        to: '/plm/ecr/$billId',
        params: { billId: 'plm_001' },
      })
    )
  })

  it('submits ECO execution and jumps to the business detail page', async () => {
    plmApiMocks.createPLMECOExecution.mockResolvedValue(createLaunchResponse('eco_001'))

    renderWithQuery(<PLMECOCreatePage />)
    expect(
      screen.getByRole('button', { name: '用 AI 辅助填写 ECO' })
    ).toHaveAttribute('data-source-route', '/plm/eco/create')
    fireEvent.change(screen.getByLabelText('执行标题'), {
      target: { value: 'ECO 下发' },
    })
    fireEvent.change(screen.getByLabelText('执行说明'), {
      target: { value: '通知工厂按新版图纸执行' },
    })
    fireEvent.change(screen.getByLabelText('生效日期'), {
      target: { value: '2026-04-01' },
    })
    fireEvent.change(screen.getByLabelText('变更原因'), {
      target: { value: '量产切换' },
    })
    fireEvent.click(screen.getByRole('button', { name: '发起 ECO 变更执行' }))

    await waitFor(() => {
      expect(plmApiMocks.createPLMECOExecution).toHaveBeenCalled()
    })
    expect(plmApiMocks.createPLMECOExecution.mock.calls[0][0]).toEqual({
      executionTitle: 'ECO 下发',
      executionPlan: '通知工厂按新版图纸执行',
      effectiveDate: '2026-04-01',
      changeReason: '量产切换',
    })
    expect(navigateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        to: '/plm/eco/$billId',
        params: { billId: 'plm_001' },
      })
    )
  })

  it('submits material change and jumps to the business detail page', async () => {
    plmApiMocks.createPLMMaterialChangeRequest.mockResolvedValue(
      createLaunchResponse('material_001')
    )

    renderWithQuery(<PLMMaterialChangeCreatePage />)
    expect(
      screen.getByRole('button', { name: '用 AI 辅助填写物料变更' })
    ).toHaveAttribute('data-source-route', '/plm/material-master/create')
    fireEvent.change(screen.getByLabelText('物料编码'), {
      target: { value: 'MAT-001' },
    })
    fireEvent.change(screen.getByLabelText('物料名称'), {
      target: { value: '主板总成' },
    })
    fireEvent.change(screen.getAllByLabelText('变更原因')[0], {
      target: { value: '替换供应商物料编码' },
    })
    fireEvent.change(screen.getByLabelText('变更类型'), {
      target: { value: 'ATTRIBUTE_UPDATE' },
    })
    fireEvent.click(screen.getByRole('button', { name: '发起物料主数据变更申请' }))

    await waitFor(() => {
      expect(plmApiMocks.createPLMMaterialChangeRequest).toHaveBeenCalled()
    })
    expect(plmApiMocks.createPLMMaterialChangeRequest.mock.calls[0][0]).toEqual({
      materialCode: 'MAT-001',
      materialName: '主板总成',
      changeReason: '替换供应商物料编码',
      changeType: 'ATTRIBUTE_UPDATE',
    })
    expect(navigateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        to: '/plm/material-master/$billId',
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
    expect(
      screen.getByRole('button', { name: '用 AI 解读 PLM 查询结果' })
    ).toHaveAttribute('data-source-route', '/plm/query')
    expect(await screen.findByText('结构件变更')).toBeInTheDocument()
    expect(screen.getByText('total:1')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '发起 PLM 申请' })
    ).toHaveAttribute('href', '/plm/start')
  })

  it('exposes PLM launch and Copilot context links as a smoke path', () => {
    renderWithQuery(
      <>
        <PLMHomePage />
        <PLMECRCreatePage />
      </>
    )

    expect(screen.getByText('PLM 发起中心')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '用 AI 推荐 PLM 入口' })
    ).toHaveAttribute('data-source-route', '/plm/start')
    expect(
      screen.getByRole('button', { name: '用 AI 辅助填写 ECR' })
    ).toHaveAttribute('data-source-route', '/plm/ecr/create')
    expect(
      screen.getByRole('button', { name: '发起 ECR 变更申请' })
    ).toBeInTheDocument()
  })

  it('applies status quick filters on PLM list pages', async () => {
    plmApiMocks.listPLMECRRequests.mockResolvedValue({
      page: 1,
      pageSize: 20,
      total: 0,
      pages: 0,
      groups: [],
      records: [],
    })

    renderWithQuery(
      <PLMECRListPage
        search={{
          page: 1,
          pageSize: 20,
          keyword: '',
          filters: [],
          sorts: [],
          groups: [],
        }}
        navigate={navigateMock}
      />
    )

    expect(await screen.findByText('ECR 变更申请列表')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '审批中' }))

    const filterNavigateCall =
      navigateMock.mock.calls[navigateMock.mock.calls.length - 1]?.[0]
    expect(filterNavigateCall).toBeTruthy()
    const nextSearch = filterNavigateCall.search({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })
    expect(nextSearch.filters).toEqual([
      { field: 'status', operator: 'eq', value: 'RUNNING' },
    ])
  })

  it('renders the ECR business detail and approval link', async () => {
    plmApiMocks.getPLMECRRequestDetail.mockResolvedValue({
      billId: 'ecr_001',
      billNo: 'ECR-20260323-000001',
      changeTitle: '结构件变更',
      changeReason: '供应替代',
      affectedProductCode: 'PRD-001',
      priorityLevel: 'HIGH',
      status: 'RUNNING',
      detailSummary: '影响产品 PRD-001 · 高优先级',
      approvalSummary: 'RUNNING · 当前节点 业务负责人审批',
      creatorUserId: 'usr_001',
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
    })
    workbenchApiMocks.getApprovalSheetDetailByBusiness.mockResolvedValue(
      createApprovalDetail({
        businessKey: 'ecr_001',
        businessData: {
          billId: 'ecr_001',
          billNo: 'ECR-20260323-000001',
          changeTitle: '结构件变更',
          changeReason: '供应替代',
          affectedProductCode: 'PRD-001',
          priorityLevel: 'HIGH',
          status: 'RUNNING',
        },
      })
    )

    renderWithQuery(<PLMECRRequestBillDetailPage billId='ecr_001' />)

    expect(await screen.findByText('ECR 变更申请详情')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '用 AI 解读当前 PLM 单据' })
    ).toHaveAttribute('data-source-route', '/plm/ecr/ecr_001')
    expect(await screen.findByText('业务单详情')).toBeInTheDocument()
    expect(await screen.findByText('审批单联查')).toBeInTheDocument()
    expect(await screen.findByText('结构件变更')).toBeInTheDocument()
    expect(await screen.findByText('影响产品 PRD-001 · 高优先级')).toBeInTheDocument()
    expect(await screen.findByText('usr_001')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '查看审批单' })).toHaveAttribute(
      'href',
      '/workbench/todos/$taskId'
    )
  })

  it('renders ECO and material detail pages', async () => {
    plmApiMocks.getPLMECOExecutionDetail.mockResolvedValue({
      billId: 'eco_001',
      billNo: 'ECO-20260323-000001',
      executionTitle: 'ECO 下发',
      executionPlan: '通知工厂执行',
      effectiveDate: '2026-04-01',
      changeReason: '量产切换',
      status: 'RUNNING',
      detailSummary: '生效日期 2026-04-01 · 量产切换',
      approvalSummary: 'RUNNING · 当前节点 业务负责人审批',
      creatorUserId: 'usr_001',
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
    })
    plmApiMocks.getPLMMaterialChangeDetail.mockResolvedValue({
      billId: 'material_001',
      billNo: 'MAT-20260323-000001',
      materialCode: 'MAT-001',
      materialName: '主板总成',
      changeReason: '替换供应商物料编码',
      changeType: 'ATTRIBUTE_UPDATE',
      status: 'RUNNING',
      detailSummary: 'ATTRIBUTE_UPDATE · 替换供应商物料编码',
      approvalSummary: 'RUNNING · 当前节点 业务负责人审批',
      creatorUserId: 'usr_001',
      createdAt: '2026-03-23T09:00:00+08:00',
      updatedAt: '2026-03-23T09:10:00+08:00',
    })
    workbenchApiMocks.getApprovalSheetDetailByBusiness
      .mockResolvedValueOnce(
        createApprovalDetail({
          businessKey: 'eco_001',
          businessType: 'PLM_ECO',
          processName: 'ECO 变更执行',
          activeTaskIds: [],
          businessData: {
            billId: 'eco_001',
            billNo: 'ECO-20260323-000001',
            executionTitle: 'ECO 下发',
            executionPlan: '通知工厂执行',
            effectiveDate: '2026-04-01',
            changeReason: '量产切换',
            status: 'RUNNING',
          },
        })
      )
      .mockResolvedValueOnce(
        createApprovalDetail({
          businessKey: 'material_001',
          businessType: 'PLM_MATERIAL',
          processName: '物料主数据变更申请',
          activeTaskIds: [],
          businessData: {
            billId: 'material_001',
            billNo: 'MAT-20260323-000001',
            materialCode: 'MAT-001',
            materialName: '主板总成',
            changeReason: '替换供应商物料编码',
            changeType: 'ATTRIBUTE_UPDATE',
            status: 'RUNNING',
          },
        })
      )

    renderWithQuery(<PLMECOExecutionBillDetailPage billId='eco_001' />)
    renderWithQuery(<PLMMaterialChangeBillDetailPage billId='material_001' />)

    expect(await screen.findByText('ECO 变更执行详情')).toBeInTheDocument()
    expect(await screen.findByText('物料主数据变更详情')).toBeInTheDocument()
    expect(await screen.findByText('ECO 变更执行')).toBeInTheDocument()
    expect(await screen.findByText('物料主数据变更申请')).toBeInTheDocument()
    expect(await screen.findByText('生效日期 2026-04-01 · 量产切换')).toBeInTheDocument()
    expect(await screen.findByText('ATTRIBUTE_UPDATE · 替换供应商物料编码')).toBeInTheDocument()
    expect(screen.getAllByText('当前没有可打开的待办审批单')).toHaveLength(2)
  })
})
