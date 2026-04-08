import { Children } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  PLMECOCreatePage,
  PLMECOExecutionBillDetailPage,
  PLMECOListPage,
  PLMECRCreatePage,
  PLMECRListPage,
  PLMECRRequestBillDetailPage,
  PLMHomePage,
  PLMMaterialChangeBillDetailPage,
  PLMMaterialChangeCreatePage,
  PLMMaterialChangeListPage,
  PLMQueryPage,
} from './pages'

const {
  navigateMock,
  plmApiMocks,
  routeSearchMock,
  toastSuccessMock,
  workbenchApiMocks,
} = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  routeSearchMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  plmApiMocks: {
    createPLMECRRequest: vi.fn(),
    createPLMECOExecution: vi.fn(),
    createPLMMaterialChangeRequest: vi.fn(),
    addPLMImplementationEvidence: vi.fn(),
    dispatchPLMConnectorTask: vi.fn(),
    releasePLMConfigurationBaseline: vi.fn(),
    releasePLMDocumentAsset: vi.fn(),
    getPLMDashboardSummary: vi.fn(),
    getPLMDashboardCockpit: vi.fn(),
    listPLMECRRequests: vi.fn(),
    listPLMECOExecutions: vi.fn(),
    listPLMMaterialChangeRequests: vi.fn(),
    getPLMECRRequestDetail: vi.fn(),
    getPLMECOExecutionDetail: vi.fn(),
    getPLMMaterialChangeDetail: vi.fn(),
    listPLMBomNodes: vi.fn(),
    listPLMConfigurationBaselines: vi.fn(),
    listPLMDomainAcl: vi.fn(),
    listPLMDocumentAssets: vi.fn(),
    listPLMExternalIntegrations: vi.fn(),
    listPLMExternalSyncEvents: vi.fn(),
    listPLMObjectAcl: vi.fn(),
    listPLMRoleAssignments: vi.fn(),
    listPLMConnectorTasks: vi.fn(),
    getPLMImplementationWorkspace: vi.fn(),
    performPLMImplementationTaskAction: vi.fn(),
    submitPLMECRDraft: vi.fn(),
    cancelPLMECRRequest: vi.fn(),
    submitPLMECODraft: vi.fn(),
    cancelPLMECOExecution: vi.fn(),
    submitPLMMaterialDraft: vi.fn(),
    cancelPLMMaterialChange: vi.fn(),
    startPLMBusinessImplementation: vi.fn(),
    markPLMBusinessValidating: vi.fn(),
    retryPLMConnectorTask: vi.fn(),
    updatePLMAcceptanceChecklist: vi.fn(),
    closePLMBusinessBill: vi.fn(),
  },
  workbenchApiMocks: {
    getApprovalSheetDetailByBusiness: vi.fn(),
  },
}))

vi.mock('sonner', () => ({
  toast: {
    success: toastSuccessMock,
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

vi.mock('@/features/ai/context-entry', () => ({
  ContextualCopilotEntry: ({
    label,
    sourceRoute,
  }: {
    label: string
    sourceRoute: string
  }) => <button data-source-route={sourceRoute}>{label}</button>,
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
    <section>
      <h1>{title}</h1>
      <p>{description}</p>
      {actions ? <div>{Children.toArray(actions)}</div> : null}
      {children}
    </section>
  ),
}))

vi.mock('@/features/shared/crud/resource-list-page', () => ({
  ResourceListPage: ({
    title,
    createAction,
    extraActions,
    topContent,
    total,
    data,
  }: {
    title: string
    createAction?: { label: string; href: string }
    extraActions?: React.ReactNode
    topContent?: React.ReactNode
    total?: number
    data: Array<Record<string, unknown>>
  }) => (
    <section>
      <h2>{title}</h2>
      {topContent ? <div>{topContent}</div> : null}
      {extraActions ? <div>{Children.toArray(extraActions)}</div> : null}
      {createAction ? (
        <a href={createAction.href}>{createAction.label}</a>
      ) : null}
      <span>total:{total ?? data.length}</span>
      {data.map((item, index) => (
        <div key={String(item.billId ?? item.billNo ?? index)}>
          {String(
            item.businessTitle ??
              item.changeTitle ??
              item.executionTitle ??
              item.materialName ??
              item.billNo ??
              ''
          )}
        </div>
      ))}
    </section>
  ),
}))

vi.mock('@/lib/api/plm', () => plmApiMocks)
vi.mock('@/lib/api/workbench', () => workbenchApiMocks)
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
    billNo: 'PLM-20260407-001',
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
    createdAt: '2026-04-07T09:00:00+08:00',
    updatedAt: '2026-04-07T09:10:00+08:00',
    completedAt: null,
    receiveTime: '2026-04-07T09:01:00+08:00',
    readTime: '2026-04-07T09:02:00+08:00',
    handleStartTime: '2026-04-07T09:03:00+08:00',
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

function fillAffectedItemRow(
  group: HTMLElement,
  values: {
    itemType: string
    itemCode: string
    itemName: string
    beforeVersion?: string
    afterVersion?: string
    changeAction: string
    ownerUserId?: string
    remark?: string
  }
) {
  fireEvent.change(within(group).getByLabelText('对象类型'), {
    target: { value: values.itemType },
  })
  fireEvent.change(within(group).getByLabelText('对象编码'), {
    target: { value: values.itemCode },
  })
  fireEvent.change(within(group).getByLabelText('对象名称'), {
    target: { value: values.itemName },
  })
  if (values.beforeVersion !== undefined) {
    fireEvent.change(within(group).getByLabelText('前版本'), {
      target: { value: values.beforeVersion },
    })
  }
  if (values.afterVersion !== undefined) {
    fireEvent.change(within(group).getByLabelText('后版本'), {
      target: { value: values.afterVersion },
    })
  }
  fireEvent.change(within(group).getByLabelText('变更动作'), {
    target: { value: values.changeAction },
  })
  if (values.ownerUserId !== undefined) {
    fireEvent.change(within(group).getByLabelText('责任人'), {
      target: { value: values.ownerUserId },
    })
  }
  if (values.remark !== undefined) {
    fireEvent.change(within(group).getByLabelText('备注'), {
      target: { value: values.remark },
    })
  }
}

describe('plm pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeSearchMock.mockReturnValue({
      page: 1,
      pageSize: 10,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })
    plmApiMocks.listPLMBomNodes.mockResolvedValue([])
    plmApiMocks.listPLMConfigurationBaselines.mockResolvedValue([])
    plmApiMocks.listPLMDomainAcl.mockResolvedValue([])
    plmApiMocks.listPLMDocumentAssets.mockResolvedValue([])
    plmApiMocks.listPLMExternalIntegrations.mockResolvedValue([])
    plmApiMocks.listPLMExternalSyncEvents.mockResolvedValue([])
    plmApiMocks.listPLMObjectAcl.mockResolvedValue([])
    plmApiMocks.listPLMRoleAssignments.mockResolvedValue([])
    plmApiMocks.listPLMConnectorTasks.mockResolvedValue([])
    plmApiMocks.dispatchPLMConnectorTask.mockResolvedValue({
      id: 'job_default',
      businessType: 'PLM_ECR',
      billId: 'plm_001',
      connectorCode: 'ERP',
      connectorName: 'ERP Connector',
      targetSystem: 'ERP',
      taskType: 'SYNC',
      status: 'DISPATCHED',
      dispatchLogs: [],
      receipts: [],
    })
    plmApiMocks.retryPLMConnectorTask.mockResolvedValue({
      id: 'job_default',
      businessType: 'PLM_ECR',
      billId: 'plm_001',
      connectorCode: 'ERP',
      connectorName: 'ERP Connector',
      targetSystem: 'ERP',
      taskType: 'SYNC',
      status: 'PENDING',
      dispatchLogs: [],
      receipts: [],
    })
    plmApiMocks.addPLMImplementationEvidence.mockResolvedValue({
      id: 'evidence_default',
      businessType: 'PLM_ECR',
      billId: 'plm_001',
      evidenceType: 'VALIDATION',
      title: '默认验证记录',
      status: 'COLLECTED',
      summary: '默认验证摘要',
    })
    plmApiMocks.updatePLMAcceptanceChecklist.mockResolvedValue({
      id: 'checkpoint_default',
      businessType: 'PLM_ECR',
      billId: 'plm_001',
      checkpointCode: 'AC-DEFAULT',
      checkpointName: '默认检查点',
      status: 'COMPLETED',
      required: true,
      summary: '默认检查结果',
    })
    plmApiMocks.getPLMImplementationWorkspace.mockResolvedValue({
      dependencies: [],
      evidences: [],
      acceptanceCheckpoints: [],
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
    expect(screen.getByRole('link', { name: '发起物料变更' })).toHaveAttribute(
      'href',
      '/plm/material-master/create'
    )
  })

  it('renders the PLM workspace summary and recent bills', async () => {
    plmApiMocks.getPLMDashboardSummary.mockResolvedValue({
      totalCount: 12,
      draftCount: 2,
      runningCount: 5,
      completedCount: 4,
      rejectedCount: 1,
      cancelledCount: 0,
      implementingCount: 3,
      validatingCount: 1,
      closedCount: 4,
      summary: {
        totalCount: 12,
        draftCount: 2,
        runningCount: 5,
        completedCount: 4,
        rejectedCount: 1,
        cancelledCount: 0,
        implementingCount: 3,
        validatingCount: 1,
        closedCount: 4,
      },
      typeDistribution: [
        {
          businessType: 'PLM_ECR',
          totalCount: 4,
          draftCount: 1,
          runningCount: 2,
          completedCount: 1,
        },
        {
          businessType: 'PLM_ECO',
          totalCount: 5,
          draftCount: 0,
          runningCount: 2,
          completedCount: 3,
        },
      ],
      stageDistribution: [
        { stage: 'DRAFT', stageLabel: '草稿', totalCount: 2, percent: 16.7 },
        {
          stage: 'RUNNING',
          stageLabel: '审批中',
          totalCount: 5,
          percent: 41.7,
        },
        {
          stage: 'IMPLEMENTING',
          stageLabel: '实施中',
          totalCount: 3,
          percent: 25,
        },
      ],
      trendSeries: [
        {
          day: '2026-04-05',
          totalCount: 3,
          draftCount: 1,
          runningCount: 1,
          completedCount: 1,
        },
        {
          day: '2026-04-06',
          totalCount: 4,
          draftCount: 1,
          runningCount: 2,
          completedCount: 1,
        },
      ],
      taskAlerts: [
        {
          id: 'alert_001',
          alertType: 'OVERDUE',
          severity: 'HIGH',
          billId: 'eco_001',
          billNo: 'PLM-ECO-001',
          businessType: 'PLM_ECO',
          businessTitle: 'ECO 执行',
          ownerUserId: 'usr_002',
          ownerDisplayName: '李四',
          dueAt: '2026-04-07T09:00:00+08:00',
          message: '实施任务已超期 2 天。',
        },
      ],
      ownerRanking: [
        {
          ownerUserId: 'usr_002',
          ownerDisplayName: '李四',
          totalCount: 6,
          pendingCount: 2,
          blockedCount: 1,
          overdueTaskCount: 1,
          completedCount: 2,
        },
      ],
      byBusinessType: [
        {
          businessType: 'PLM_ECR',
          totalCount: 4,
          draftCount: 1,
          runningCount: 2,
          completedCount: 1,
        },
        {
          businessType: 'PLM_ECO',
          totalCount: 5,
          draftCount: 0,
          runningCount: 2,
          completedCount: 3,
        },
        {
          businessType: 'PLM_MATERIAL',
          totalCount: 3,
          draftCount: 1,
          runningCount: 1,
          completedCount: 1,
        },
      ],
      recentBills: [
        {
          billId: 'ecr_001',
          billNo: 'PLM-ECR-001',
          businessType: 'PLM_ECR',
          businessTitle: '结构件替换',
          sceneCode: 'SCENE_A',
          status: 'RUNNING',
          detailSummary: '影响产品 PRD-001 · 高优先级',
          updatedAt: '2026-04-07T09:10:00+08:00',
        },
      ],
    })
    plmApiMocks.getPLMDashboardCockpit.mockResolvedValue({
      stuckSyncItems: [
        {
          id: 'stuck_sync_001',
          billId: 'ecr_001',
          billNo: 'PLM-ECR-001',
          businessType: 'PLM_ECR',
          businessTitle: '结构件替换',
          systemCode: 'ERP',
          systemName: 'ERP',
          connectorName: 'ERP 变更下发',
          status: 'FAILED',
          pendingCount: 1,
          failedCount: 2,
          ownerDisplayName: '李四',
          summary: 'ERP 回执失败，仍有 1 条待推进记录。',
          updatedAt: '2026-04-07T10:08:00+08:00',
        },
      ],
      closeBlockerItems: [
        {
          id: 'close_blocker_001',
          billId: 'ecr_001',
          billNo: 'PLM-ECR-001',
          businessType: 'PLM_ECR',
          businessTitle: '结构件替换',
          blockerType: 'ACCEPTANCE',
          blockerTitle: '工艺验证签收',
          blockerCount: 1,
          ownerDisplayName: '赵六',
          summary: '工艺验证签收未完成，不能关闭。',
          dueAt: '2026-04-08T18:00:00+08:00',
        },
      ],
      failedSystemHotspots: [
        {
          systemCode: 'ERP',
          systemName: 'ERP',
          failedCount: 2,
          pendingCount: 1,
          blockedBillCount: 1,
          summary: 'ERP 是当前最需要处理的失败系统。',
        },
      ],
      objectTypeDistribution: [
        { code: 'PART', label: '零部件', totalCount: 5 },
        { code: 'DOCUMENT', label: '文档', totalCount: 3 },
      ],
      domainDistribution: [
        { code: 'SCENE_A', label: '试制变更', totalCount: 6 },
        { code: 'SCENE_B', label: '量产变更', totalCount: 6 },
      ],
      baselineStatusDistribution: [
        { code: 'RELEASED', label: '已发布', totalCount: 4 },
        { code: 'DRAFT', label: '草稿', totalCount: 2 },
      ],
      blockedTaskCount: 1,
      overdueTaskCount: 2,
      readyToCloseCount: 3,
      pendingIntegrationCount: 2,
      failedSyncEventCount: 1,
      roleCoverageRate: 83,
      averageClosureHours: 48,
      connectorTaskBacklogCount: 3,
      pendingReceiptCount: 2,
      implementationHealthyRate: 75,
      acceptanceDueCount: 1,
      integrationSystemDistribution: [
        { code: 'ERP', label: 'ERP', totalCount: 3 },
        { code: 'MES', label: 'MES', totalCount: 2 },
      ],
      integrationStatusDistribution: [
        { code: 'SYNCED', label: '已同步', totalCount: 2 },
        { code: 'FAILED', label: '失败', totalCount: 1 },
      ],
      connectorStatusDistribution: [
        { code: 'RUNNING', label: '处理中', totalCount: 2 },
      ],
      implementationHealthDistribution: [
        { code: 'HEALTHY', label: '健康', totalCount: 4 },
        { code: 'RISK', label: '风险', totalCount: 1 },
      ],
    })

    renderWithQuery(<PLMQueryPage />)

    expect(await screen.findByText('PLM 工作台')).toBeInTheDocument()
    expect(await screen.findByText('业务类型分布')).toBeInTheDocument()
    expect(await screen.findByText('生命周期分布')).toBeInTheDocument()
    expect(await screen.findByText('任务预警')).toBeInTheDocument()
    expect(await screen.findByText('负责人排行')).toBeInTheDocument()
    expect(await screen.findByText('管理驾驶舱 v2')).toBeInTheDocument()
    expect(await screen.findByText('连接器健康')).toBeInTheDocument()
    expect(await screen.findByText('卡住同步')).toBeInTheDocument()
    expect(await screen.findByText('失败系统热点')).toBeInTheDocument()
    expect(await screen.findByText('阻塞关闭清单')).toBeInTheDocument()
    expect(await screen.findByText('ERP 回执失败，仍有 1 条待推进记录。')).toBeInTheDocument()
    expect(await screen.findByText('工艺验证签收未完成，不能关闭。')).toBeInTheDocument()
    expect((await screen.findAllByText('ERP')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('超期任务')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('实施闭环')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('结构件替换')).length).toBeGreaterThan(0)
    expect(screen.getByText('总单据')).toBeInTheDocument()
    expect(
      screen.getByText('进入实施阶段、等待任务完成的单据数量。')
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '打开单据' })).toHaveAttribute(
      'href',
      '/plm/ecr/$billId'
    )
  })

  it('renders PLM business list pages and applies advanced filters', async () => {
    plmApiMocks.listPLMECRRequests.mockResolvedValue({
      page: 1,
      pageSize: 10,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          billId: 'ecr_001',
          billNo: 'PLM-ECR-001',
          sceneCode: 'SCENE_A',
          changeTitle: '结构件替换',
          affectedProductCode: 'PRD-001',
          priorityLevel: 'HIGH',
          processInstanceId: 'pi_ecr_001',
          status: 'RUNNING',
          creatorUserId: 'usr_001',
          creatorDisplayName: '张三',
          createdAt: '2026-04-07T09:00:00+08:00',
          updatedAt: '2026-04-07T09:10:00+08:00',
          changeCategory: '结构',
          targetVersion: 'A.02',
          impactScope: '总装与测试',
          riskLevel: 'MEDIUM',
        },
      ],
    })
    plmApiMocks.listPLMECOExecutions.mockResolvedValue({
      page: 1,
      pageSize: 10,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          billId: 'eco_001',
          billNo: 'PLM-ECO-001',
          sceneCode: 'SCENE_B',
          executionTitle: 'ECO 执行',
          effectiveDate: '2026-04-10',
          changeReason: '量产切换',
          processInstanceId: 'pi_eco_001',
          status: 'RUNNING',
          creatorUserId: 'usr_001',
          creatorDisplayName: '张三',
          createdAt: '2026-04-07T09:00:00+08:00',
          updatedAt: '2026-04-07T09:10:00+08:00',
          implementationOwner: '李四',
          targetVersion: 'B.03',
          rolloutScope: '华东工厂',
        },
      ],
    })
    plmApiMocks.listPLMMaterialChangeRequests.mockResolvedValue({
      page: 1,
      pageSize: 10,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          billId: 'material_001',
          billNo: 'PLM-MATERIAL-001',
          sceneCode: 'SCENE_C',
          materialCode: 'MAT-001',
          materialName: '主板总成',
          changeType: 'ATTRIBUTE_UPDATE',
          changeReason: '替换物料编码',
          processInstanceId: 'pi_material_001',
          status: 'RUNNING',
          creatorUserId: 'usr_001',
          creatorDisplayName: '张三',
          createdAt: '2026-04-07T09:00:00+08:00',
          updatedAt: '2026-04-07T09:10:00+08:00',
          specificationChange: '阻焊颜色调整',
          oldValue: '绿色',
          newValue: '黑色',
          uom: 'EA',
        },
      ],
    })

    renderWithQuery(
      <>
        <PLMECRListPage
          search={{
            page: 1,
            pageSize: 10,
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
            pageSize: 10,
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
            pageSize: 10,
            keyword: '',
            filters: [],
            sorts: [],
            groups: [],
          }}
          navigate={navigateMock}
        />
      </>
    )

    expect(await screen.findByText('ECR 变更申请台账')).toBeInTheDocument()
    expect(await screen.findByText('ECO 变更执行台账')).toBeInTheDocument()
    expect(await screen.findByText('物料主数据变更台账')).toBeInTheDocument()
    expect(await screen.findByText('结构件替换')).toBeInTheDocument()
    expect(await screen.findByText('ECO 执行')).toBeInTheDocument()
    expect(await screen.findByText('主板总成')).toBeInTheDocument()

    fireEvent.change(screen.getAllByPlaceholderText('例如：DEFAULT')[0], {
      target: { value: 'SCENE_A' },
    })
    fireEvent.change(screen.getAllByPlaceholderText('例如：usr_001')[0], {
      target: { value: 'usr_001' },
    })
    fireEvent.click(screen.getAllByRole('button', { name: '应用筛选' })[0])

    const navigateCall =
      navigateMock.mock.calls[navigateMock.mock.calls.length - 1]?.[0]
    const nextSearch = navigateCall.search({
      page: 1,
      pageSize: 10,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })

    expect(nextSearch.filters).toEqual([
      { field: 'sceneCode', operator: 'eq', value: 'SCENE_A' },
      { field: 'creatorUserId', operator: 'eq', value: 'usr_001' },
    ])
  })

  it('allows adding and removing structured affected items in the ECR create form', async () => {
    plmApiMocks.createPLMECRRequest.mockResolvedValue(createLaunchResponse())

    renderWithQuery(<PLMECRCreatePage />)

    fireEvent.change(screen.getByLabelText('变更标题'), {
      target: { value: '结构件变更' },
    })
    fireEvent.change(screen.getByLabelText('变更原因'), {
      target: { value: '供应替代' },
    })

    fireEvent.click(screen.getByRole('button', { name: '添加受影响对象' }))

    const groupsBeforeRemoval = screen.getAllByRole('group', {
      name: /受影响对象 \d+/,
    })
    expect(groupsBeforeRemoval).toHaveLength(2)

    fillAffectedItemRow(groupsBeforeRemoval[0], {
      itemType: 'PART',
      itemCode: 'PART-001',
      itemName: '机壳',
      beforeVersion: 'A.01',
      afterVersion: 'A.02',
      changeAction: 'REPLACE',
      ownerUserId: 'usr_001',
      remark: '首版替换',
    })
    fillAffectedItemRow(groupsBeforeRemoval[1], {
      itemType: 'DOCUMENT',
      itemCode: 'DOC-010',
      itemName: '总装图纸',
      beforeVersion: 'V1',
      afterVersion: 'V2',
      changeAction: 'UPDATE',
      ownerUserId: 'usr_002',
      remark: '同步最新图纸',
    })

    fireEvent.click(
      within(groupsBeforeRemoval[0]).getByRole('button', {
        name: '删除受影响对象',
      })
    )

    expect(
      screen.getAllByRole('group', { name: /受影响对象 \d+/ })
    ).toHaveLength(1)

    fireEvent.click(screen.getByRole('button', { name: '发起 ECR 变更申请' }))

    await waitFor(() => {
      expect(plmApiMocks.createPLMECRRequest).toHaveBeenCalledWith(
        expect.objectContaining({
          changeTitle: '结构件变更',
          changeReason: '供应替代',
          affectedItems: [
            {
              itemType: 'DOCUMENT',
              itemCode: 'DOC-010',
              itemName: '总装图纸',
              beforeVersion: 'V1',
              afterVersion: 'V2',
              changeAction: 'UPDATE',
              ownerUserId: 'usr_002',
              remark: '同步最新图纸',
            },
          ],
        }),
        expect.anything()
      )
    })
    expect(navigateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        to: '/plm/ecr/$billId',
        params: { billId: 'plm_001' },
      })
    )
  })

  it('submits the ECO create form with structured affected items', async () => {
    plmApiMocks.createPLMECOExecution.mockResolvedValue(createLaunchResponse())

    renderWithQuery(<PLMECOCreatePage />)

    fireEvent.change(screen.getByLabelText('执行标题'), {
      target: { value: 'ECO 下发' },
    })
    fireEvent.change(screen.getByLabelText('执行说明'), {
      target: { value: '通知工厂执行' },
    })
    fireEvent.change(screen.getByLabelText('变更原因'), {
      target: { value: '量产切换' },
    })

    const group = screen.getByRole('group', { name: '受影响对象 1' })
    fillAffectedItemRow(group, {
      itemType: 'BOM',
      itemCode: 'BOM-001',
      itemName: '主装配BOM',
      beforeVersion: 'B.01',
      afterVersion: 'B.02',
      changeAction: 'UPDATE',
      ownerUserId: 'usr_003',
      remark: '执行前确认',
    })

    fireEvent.click(screen.getByRole('button', { name: '发起 ECO 变更执行' }))

    await waitFor(() => {
      expect(plmApiMocks.createPLMECOExecution).toHaveBeenCalledWith(
        expect.objectContaining({
          executionTitle: 'ECO 下发',
          executionPlan: '通知工厂执行',
          changeReason: '量产切换',
          affectedItems: [
            {
              itemType: 'BOM',
              itemCode: 'BOM-001',
              itemName: '主装配BOM',
              beforeVersion: 'B.01',
              afterVersion: 'B.02',
              changeAction: 'UPDATE',
              ownerUserId: 'usr_003',
              remark: '执行前确认',
            },
          ],
        }),
        expect.anything()
      )
    })
  })

  it('submits the material change form with structured affected items', async () => {
    plmApiMocks.createPLMMaterialChangeRequest.mockResolvedValue(
      createLaunchResponse()
    )

    renderWithQuery(<PLMMaterialChangeCreatePage />)

    fireEvent.change(screen.getByLabelText('物料编码'), {
      target: { value: 'MAT-001' },
    })
    fireEvent.change(screen.getByLabelText('物料名称'), {
      target: { value: '主板总成' },
    })
    fireEvent.change(screen.getByLabelText('变更原因'), {
      target: { value: '替换物料编码' },
    })

    fillAffectedItemRow(screen.getByRole('group', { name: '受影响对象 1' }), {
      itemType: 'MATERIAL',
      itemCode: 'MAT-001',
      itemName: '主板总成',
      beforeVersion: '1.0',
      afterVersion: '2.0',
      changeAction: 'REPLACE',
      ownerUserId: 'usr_004',
      remark: '主数据切换',
    })

    fireEvent.click(
      screen.getByRole('button', { name: '发起物料主数据变更申请' })
    )

    await waitFor(() => {
      expect(plmApiMocks.createPLMMaterialChangeRequest).toHaveBeenCalledWith(
        expect.objectContaining({
          materialCode: 'MAT-001',
          materialName: '主板总成',
          changeReason: '替换物料编码',
          affectedItems: [
            {
              itemType: 'MATERIAL',
              itemCode: 'MAT-001',
              itemName: '主板总成',
              beforeVersion: '1.0',
              afterVersion: '2.0',
              changeAction: 'REPLACE',
              ownerUserId: 'usr_004',
              remark: '主数据切换',
            },
          ],
        }),
        expect.anything()
      )
    })
  })

  it('renders detail workspace and triggers lifecycle submit action for draft ECR', async () => {
    plmApiMocks.getPLMECRRequestDetail.mockResolvedValue({
      billId: 'ecr_001',
      billNo: 'ECR-20260407-0001',
      sceneCode: 'SCENE_A',
      changeTitle: '结构件变更',
      changeReason: '供应替代',
      affectedProductCode: 'PRD-001',
      priorityLevel: 'HIGH',
      changeCategory: '结构',
      targetVersion: 'A.02',
      affectedObjectsText: '机壳、总装文件',
      impactScope: '总装与测试',
      riskLevel: 'MEDIUM',
      affectedItems: [
        {
          id: 'ai_001',
          businessType: 'PLM_ECR',
          billId: 'ecr_001',
          itemType: 'PART',
          itemCode: 'PART-001',
          itemName: '机壳',
          beforeVersion: 'A.01',
          afterVersion: 'A.02',
          changeAction: 'REPLACE',
          ownerUserId: 'usr_001',
          remark: '首版替换',
        },
      ],
      objectLinks: [
        {
          id: 'ol_001',
          businessType: 'PLM_ECR',
          billId: 'ecr_001',
          objectId: 'obj_001',
          objectCode: 'PART-001',
          objectName: '机壳',
          objectType: 'PART',
          objectRevisionCode: 'A.02',
          versionLabel: 'A.02',
          roleCode: 'AFFECTED_OBJECT',
          roleLabel: '受影响对象',
          changeAction: 'REPLACE',
          beforeRevisionCode: 'A.01',
          afterRevisionCode: 'A.02',
          sourceSystem: 'PDM',
          externalRef: 'PDM-001',
          remark: '首版替换',
          sortOrder: 1,
        },
      ],
      revisionDiffs: [
        {
          id: 'diff_001',
          businessType: 'PLM_ECR',
          billId: 'ecr_001',
          objectId: 'obj_001',
          objectCode: 'PART-001',
          objectName: '机壳',
          beforeRevisionCode: 'A.01',
          afterRevisionCode: 'A.02',
          diffKind: 'ATTRIBUTE',
          diffSummary: '材质和尺寸字段更新',
          diffPayloadJson: { material: 'AL6061', size: '120x80' },
          createdAt: '2026-04-07T09:05:00+08:00',
        },
      ],
      status: 'DRAFT',
      detailSummary: '影响产品 PRD-001 · 高优先级',
      approvalSummary: '草稿阶段尚未发起审批',
      creatorUserId: 'usr_001',
      creatorDisplayName: '张三',
      createdAt: '2026-04-07T09:00:00+08:00',
      updatedAt: '2026-04-07T09:10:00+08:00',
      availableActions: ['SUBMIT', 'CANCEL'],
    })
    workbenchApiMocks.getApprovalSheetDetailByBusiness.mockResolvedValue(
      createApprovalDetail({
        businessKey: 'ecr_001',
        activeTaskIds: [],
        instanceStatus: 'RUNNING',
      })
    )
    plmApiMocks.submitPLMECRDraft.mockResolvedValue({
      billId: 'ecr_001',
      billNo: 'ECR-20260407-0001',
      status: 'RUNNING',
      processInstanceId: 'pi_ecr_001',
    })
    plmApiMocks.listPLMBomNodes.mockResolvedValue([
      {
        id: 'bom_001',
        businessType: 'PLM_ECR',
        billId: 'ecr_001',
        nodeCode: 'PART-001',
        nodeName: '机壳',
        nodeType: 'PART',
        quantity: 1,
        unit: 'EA',
        hierarchyLevel: 0,
        changeAction: 'REPLACE',
      },
    ])
    plmApiMocks.listPLMDocumentAssets.mockResolvedValue([
      {
        id: 'doc_001',
        businessType: 'PLM_ECR',
        billId: 'ecr_001',
        documentCode: 'DOC-010',
        documentName: '总装图纸',
        documentType: 'DRAWING',
        versionLabel: 'V2',
        vaultState: 'WORKING',
        fileName: 'assy.pdf',
        sourceSystem: 'PDM',
        externalRef: 'PDM-DOC-010',
      },
    ])
    plmApiMocks.listPLMConfigurationBaselines.mockResolvedValue([
      {
        id: 'baseline_001',
        businessType: 'PLM_ECR',
        billId: 'ecr_001',
        baselineCode: 'BL-001',
        baselineName: '样机基线',
        baselineType: 'PRODUCT',
        status: 'DRAFT',
        releasedAt: null,
        items: [
          {
            id: 'baseline_item_001',
            objectCode: 'PART-001',
            objectName: '机壳',
            objectType: 'PART',
            beforeRevisionCode: 'A.01',
            afterRevisionCode: 'A.02',
          },
        ],
      },
    ])
    plmApiMocks.listPLMObjectAcl.mockResolvedValue([
      {
        id: 'acl_001',
        businessType: 'PLM_ECR',
        billId: 'ecr_001',
        objectId: 'obj_001',
        objectCode: 'PART-001',
        objectName: '机壳',
        subjectType: 'ROLE',
        subjectCode: 'PLM_MANAGER',
        permissionCode: 'EDIT',
        accessScope: 'SCENE_A',
        inherited: false,
      },
    ])
    plmApiMocks.listPLMDomainAcl.mockResolvedValue([
      {
        id: 'domain_acl_001',
        businessType: 'PLM_ECR',
        billId: 'ecr_001',
        domainCode: 'SCENE_A',
        roleCode: 'PLM_MANAGER',
        permissionCode: 'EDIT',
        accessScope: 'DOMAIN',
        policySource: 'POLICY',
      },
    ])
    plmApiMocks.listPLMRoleAssignments.mockResolvedValue([
      {
        id: 'role_001',
        businessType: 'PLM_ECR',
        billId: 'ecr_001',
        roleCode: 'PLM_MANAGER',
        roleLabel: '变更经理',
        assigneeUserId: 'usr_001',
        assigneeDisplayName: '张三',
        assignmentScope: 'SCENE_A',
        required: true,
        status: 'ASSIGNED',
      },
    ])
    plmApiMocks.listPLMExternalIntegrations.mockResolvedValue([
      {
        id: 'integration_001',
        businessType: 'PLM_ECR',
        billId: 'ecr_001',
        systemCode: 'ERP',
        systemName: 'ERP',
        directionCode: 'OUTBOUND',
        integrationType: 'CHANGE_NOTICE',
        status: 'RUNNING',
        endpointKey: 'erp.change.notice',
        externalRef: 'ERP-CHANGE-001',
        lastSyncAt: '2026-04-07T10:10:00+08:00',
        message: '等待 ERP 确认',
        events: [],
      },
    ])
    plmApiMocks.listPLMExternalSyncEvents.mockResolvedValue([
      {
        id: 'sync_event_001',
        integrationId: 'integration_001',
        businessType: 'PLM_ECR',
        billId: 'ecr_001',
        systemCode: 'ERP',
        systemName: 'ERP',
        directionCode: 'OUTBOUND',
        eventType: 'PUSH_CHANGE',
        status: 'FAILED',
        errorMessage: 'ERP 接口超时',
        happenedAt: '2026-04-07T10:12:00+08:00',
      },
    ])
    plmApiMocks.listPLMConnectorTasks.mockResolvedValue([
      {
        id: 'connector_task_001',
        businessType: 'PLM_ECR',
        billId: 'ecr_001',
        connectorCode: 'ERP_CHANGE',
        connectorName: 'ERP 变更下发',
        targetSystem: 'ERP',
        directionCode: 'OUTBOUND',
        taskType: 'CHANGE_NOTICE',
        status: 'RUNNING',
        ownerDisplayName: '李四',
        requestedAt: '2026-04-07T10:00:00+08:00',
        completedAt: null,
        externalRef: 'ERP-CHANGE-001',
        payloadSummary: '向 ERP 推送结构件替换指令',
        receipts: [
          {
            id: 'receipt_001',
            connectorTaskId: 'connector_task_001',
            receiptType: 'ACK',
            receiptStatus: 'PENDING',
            receiptNo: 'ACK-001',
          },
        ],
      },
    ])
    plmApiMocks.getPLMImplementationWorkspace.mockResolvedValue({
      dependencies: [
        {
          id: 'dependency_001',
          businessType: 'PLM_ECR',
          billId: 'ecr_001',
          dependencyType: 'UPSTREAM_APPROVAL',
          upstreamTaskNo: 'TASK-UP-001',
          upstreamTitle: 'ERP 接口窗口确认',
          status: 'BLOCKED',
          blocking: true,
          dueAt: '2026-04-08T10:00:00+08:00',
          note: '需等待 ERP 可用窗口',
        },
      ],
      evidences: [
        {
          id: 'evidence_001',
          businessType: 'PLM_ECR',
          billId: 'ecr_001',
          evidenceType: 'TEST_REPORT',
          title: '样机验证报告',
          status: 'COLLECTED',
          ownerDisplayName: '王五',
          externalRef: 'DOC-REPORT-001',
          summary: '已上传样机验证结果',
        },
      ],
      acceptanceCheckpoints: [
        {
          id: 'checkpoint_001',
          businessType: 'PLM_ECR',
          billId: 'ecr_001',
          checkpointCode: 'AC-001',
          checkpointName: '工艺验证签收',
          status: 'READY',
          required: true,
          ownerDisplayName: '赵六',
          summary: '等待最终确认',
        },
      ],
    })

    renderWithQuery(<PLMECRRequestBillDetailPage billId='ecr_001' />)

    expect(await screen.findByText('ECR 变更申请详情')).toBeInTheDocument()
    expect(await screen.findByText('业务概览')).toBeInTheDocument()
    expect(await screen.findAllByText('结构件变更')).toHaveLength(2)
    expect(await screen.findByText('机壳、总装文件')).toBeInTheDocument()
    expect(await screen.findByText('对象链接')).toBeInTheDocument()
    expect(await screen.findByText('版本对比')).toBeInTheDocument()
    expect(await screen.findByText('执行总览')).toBeInTheDocument()
    expect(await screen.findByText('发布 / 外部推进总览')).toBeInTheDocument()
    expect(await screen.findByText('连接器任务 / 回执')).toBeInTheDocument()
    expect(await screen.findByText('下一步动作')).toBeInTheDocument()
    expect(await screen.findByText('优先处理失败回执与连接器任务')).toBeInTheDocument()
    expect(await screen.findByText('实施依赖 / 证据 / 验收')).toBeInTheDocument()
    expect((await screen.findAllByText('连接器健康')).length).toBeGreaterThan(0)
    expect(await screen.findByText('回执闭环')).toBeInTheDocument()
    expect(
      await screen.findByText('向 ERP 推送结构件替换指令')
    ).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: '发布基线' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: '受控发布' })).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: '实施依赖' })).toBeInTheDocument()
    expect(await screen.findByText('阻塞关闭清单')).toBeInTheDocument()
    expect(await screen.findByText('卡住同步')).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: '验证证据' })).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: '验收检查' })).toBeInTheDocument()
    expect(await screen.findByText('已收集 1')).toBeInTheDocument()
    expect(await screen.findByText('已就绪 1')).toBeInTheDocument()
    expect(await screen.findByText('依赖疏通')).toBeInTheDocument()
    expect((await screen.findAllByText('PART-001')).length).toBeGreaterThanOrEqual(2)
    expect(await screen.findByText('材质和尺寸字段更新')).toBeInTheDocument()

    plmApiMocks.dispatchPLMConnectorTask.mockResolvedValue({
      id: 'connector_task_001',
      businessType: 'PLM_ECR',
      billId: 'ecr_001',
      connectorCode: 'ERP_CHANGE',
      connectorName: 'ERP 变更下发',
      targetSystem: 'ERP',
      taskType: 'CHANGE_NOTICE',
      status: 'DISPATCHED',
      dispatchLogs: [],
      receipts: [],
    })
    fireEvent.click(screen.getByRole('button', { name: '重新派发' }))

    await waitFor(() => {
      expect(plmApiMocks.dispatchPLMConnectorTask).toHaveBeenCalledWith(
        'connector_task_001'
      )
    })

    plmApiMocks.releasePLMConfigurationBaseline.mockResolvedValue({
      businessType: 'PLM_ECR',
      billId: 'ecr_001',
      targetType: 'BASELINE',
      targetId: 'baseline_001',
      targetName: '样机基线',
      status: 'RELEASED',
      message: '样机基线 已完成发布，等待外部系统消费基线变更。',
      actedAt: '2026-04-07T10:40:00+08:00',
    })
    fireEvent.click(screen.getByRole('button', { name: '发布基线' }))
    await waitFor(() => {
      expect(plmApiMocks.releasePLMConfigurationBaseline).toHaveBeenCalledWith(
        'PLM_ECR',
        'ecr_001',
        'baseline_001'
      )
    })

    plmApiMocks.releasePLMDocumentAsset.mockResolvedValue({
      businessType: 'PLM_ECR',
      billId: 'ecr_001',
      targetType: 'DOCUMENT_ASSET',
      targetId: 'doc_001',
      targetName: '总装图纸',
      status: 'RELEASED',
      message: '总装图纸 已受控发布，等待外部系统同步文档资产。',
      actedAt: '2026-04-07T10:41:00+08:00',
    })
    fireEvent.click(screen.getByRole('button', { name: '受控发布' }))
    await waitFor(() => {
      expect(plmApiMocks.releasePLMDocumentAsset).toHaveBeenCalledWith(
        'PLM_ECR',
        'ecr_001',
        'doc_001'
      )
    })

    fireEvent.click(screen.getByRole('button', { name: '提交草稿' }))

    await waitFor(() => {
      expect(plmApiMocks.submitPLMECRDraft).toHaveBeenCalledWith('ecr_001')
    })
    expect(toastSuccessMock).toHaveBeenCalled()
  })

  it('renders the enterprise lifecycle panel and action buttons for implementing details', async () => {
    plmApiMocks.getPLMECOExecutionDetail.mockResolvedValue({
      billId: 'eco_001',
      billNo: 'ECO-20260407-0001',
      executionTitle: 'ECO 下发',
      executionPlan: '通知工厂执行',
      effectiveDate: '2026-04-10',
      changeReason: '量产切换',
      implementationOwner: '李四',
      implementationSummary: '已下发到华东工厂',
      implementationStartedAt: '2026-04-07T10:00:00+08:00',
      validationOwner: '王五',
      validationSummary: '等待首件验证',
      validatedAt: null,
      closedBy: null,
      closedAt: null,
      closeComment: null,
      targetVersion: 'B.03',
      rolloutScope: '华东工厂',
      validationPlan: '首件验证',
      rollbackPlan: '回退到旧版本',
      affectedItems: [
        {
          id: 'ai_002',
          businessType: 'PLM_ECO',
          billId: 'eco_001',
          itemType: 'BOM',
          itemCode: 'BOM-001',
          itemName: '主装配BOM',
          beforeVersion: 'B.01',
          afterVersion: 'B.02',
          changeAction: 'UPDATE',
          ownerUserId: 'usr_003',
          remark: '实施前确认',
        },
      ],
      objectLinks: [
        {
          id: 'ol_002',
          businessType: 'PLM_ECO',
          billId: 'eco_001',
          objectId: 'obj_002',
          objectCode: 'BOM-001',
          objectName: '主装配BOM',
          objectType: 'BOM',
          objectRevisionCode: 'B.02',
          versionLabel: 'B.02',
          roleCode: 'IMPLEMENTATION_TARGET',
          roleLabel: '实施对象',
          changeAction: 'UPDATE',
          beforeRevisionCode: 'B.01',
          afterRevisionCode: 'B.02',
          sourceSystem: 'PDM',
          externalRef: 'PDM-002',
          remark: '实施前确认',
          sortOrder: 1,
        },
      ],
      revisionDiffs: [
        {
          id: 'diff_002',
          businessType: 'PLM_ECO',
          billId: 'eco_001',
          objectId: 'obj_002',
          objectCode: 'BOM-001',
          objectName: '主装配BOM',
          beforeRevisionCode: 'B.01',
          afterRevisionCode: 'B.02',
          diffKind: 'BOM_STRUCTURE',
          diffSummary: '增加 2 个子件并调整顺序',
          diffPayloadJson: { added: ['PART-007', 'PART-008'], removed: [] },
          createdAt: '2026-04-07T10:05:00+08:00',
        },
      ],
      implementationTasks: [
        {
          id: 'task_eco_001',
          businessType: 'PLM_ECO',
          billId: 'eco_001',
          taskNo: 'TASK-001',
          taskTitle: '实施工厂确认',
          taskType: 'IMPLEMENTATION',
          ownerUserId: 'usr_003',
          ownerDisplayName: '王五',
          status: 'RUNNING',
          plannedStartAt: '2026-04-07T10:00:00+08:00',
          plannedEndAt: '2026-04-08T10:00:00+08:00',
          startedAt: '2026-04-07T10:10:00+08:00',
          completedAt: null,
          resultSummary: '等待首件完成后关闭',
          verificationRequired: true,
          sortOrder: 1,
        },
      ],
      status: 'IMPLEMENTING',
      detailSummary: '生效日期 2026-04-10 · 华东工厂',
      approvalSummary: 'IMPLEMENTING · 当前执行中',
      creatorUserId: 'usr_001',
      createdAt: '2026-04-07T09:00:00+08:00',
      updatedAt: '2026-04-07T09:10:00+08:00',
    })
    workbenchApiMocks.getApprovalSheetDetailByBusiness.mockResolvedValueOnce(
      createApprovalDetail({
        businessKey: 'eco_001',
        businessType: 'PLM_ECO',
        processName: 'ECO 变更执行',
        activeTaskIds: [],
        nodeName: '实施确认',
        instanceStatus: 'RUNNING',
      })
    )
    plmApiMocks.startPLMBusinessImplementation.mockResolvedValue({
      billId: 'eco_001',
      billNo: 'ECO-20260407-0001',
      status: 'IMPLEMENTING',
      processInstanceId: 'pi_eco_001',
    })
    plmApiMocks.markPLMBusinessValidating.mockResolvedValue({
      billId: 'eco_001',
      billNo: 'ECO-20260407-0001',
      status: 'VALIDATING',
      processInstanceId: 'pi_eco_001',
    })

    renderWithQuery(<PLMECOExecutionBillDetailPage billId='eco_001' />)

    expect(await screen.findByText('ECO 变更执行详情')).toBeInTheDocument()
    expect(await screen.findAllByText('李四')).toHaveLength(2)
    expect(await screen.findByText('已下发到华东工厂')).toBeInTheDocument()
    expect(await screen.findByText('对象链接')).toBeInTheDocument()
    expect(await screen.findByText('版本对比')).toBeInTheDocument()
    expect(await screen.findByText('实施任务')).toBeInTheDocument()
    expect(await screen.findByText('实施工厂确认')).toBeInTheDocument()
    expect(await screen.findByText('实施 / 验证 / 关闭')).toBeInTheDocument()
    expect(await screen.findByText('标记验证中')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '开始实施' })
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '完成' })).toBeInTheDocument()

    plmApiMocks.performPLMImplementationTaskAction.mockResolvedValue({
      billId: 'eco_001',
      billNo: 'ECO-20260407-0001',
      status: 'IMPLEMENTING',
      processInstanceId: 'pi_eco_001',
    })
    fireEvent.click(screen.getByRole('button', { name: '标记验证中' }))

    await waitFor(() => {
      expect(plmApiMocks.markPLMBusinessValidating).toHaveBeenCalledWith(
        'PLM_ECO',
        'eco_001'
      )
    })

    fireEvent.click(screen.getByRole('button', { name: '完成' }))

    await waitFor(() => {
      expect(
        plmApiMocks.performPLMImplementationTaskAction
      ).toHaveBeenCalledWith('PLM_ECO', 'eco_001', 'task_eco_001', 'COMPLETE')
    })

    fireEvent.click(screen.getByRole('button', { name: '补证据' }))
    fireEvent.click(screen.getByRole('button', { name: '保存证据' }))

    await waitFor(() => {
      expect(plmApiMocks.addPLMImplementationEvidence).toHaveBeenCalledWith(
        'PLM_ECO',
        'eco_001',
        'task_eco_001',
        expect.objectContaining({
          evidenceType: 'VALIDATION',
          evidenceName: '实施工厂确认 验证记录',
        })
      )
    })
  })
})
