import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { WorkflowInstanceDetailPage } from './management-pages'

const { getWorkflowInstanceDetailMock } = vi.hoisted(() => ({
  getWorkflowInstanceDetailMock: vi.fn(),
}))
const { advancedRuntimeApiMocks } = vi.hoisted(() => ({
  advancedRuntimeApiMocks: {
    getProcessTerminationSnapshot: vi.fn(),
    listProcessCollaborationTrace: vi.fn(),
    listProcessTerminationAuditTrail: vi.fn(),
    listProcessTimeTravelTrace: vi.fn(),
  },
}))

vi.mock('@/lib/api/workflow-management', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api/workflow-management')>(
    '@/lib/api/workflow-management'
  )
  return {
    ...actual,
    getWorkflowInstanceDetail: getWorkflowInstanceDetailMock,
  }
})

vi.mock('@/features/shared/page-shell', () => ({
  PageShell: ({ title, children }: { title: string; children: React.ReactNode }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
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
    },
  })

  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>)
}

afterEach(() => {
  vi.clearAllMocks()
})

describe('workflow management pages', () => {
  it('shows dynamic build runtime structure in instance detail', async () => {
    advancedRuntimeApiMocks.getProcessTerminationSnapshot.mockResolvedValue(null)
    advancedRuntimeApiMocks.listProcessTerminationAuditTrail.mockResolvedValue([])
    advancedRuntimeApiMocks.listProcessCollaborationTrace.mockResolvedValue([])
    advancedRuntimeApiMocks.listProcessTimeTravelTrace.mockResolvedValue([])
    getWorkflowInstanceDetailMock.mockResolvedValue({
      processInstanceId: 'pi_001',
      processDefinitionId: 'pd_001',
      flowableDefinitionId: 'flowable_pd_001',
      processKey: 'oa_leave',
      processName: '请假审批',
      businessType: 'OA_LEAVE',
      businessId: 'leave_026',
      startUserId: 'usr_001',
      status: 'RUNNING',
      suspended: false,
      currentTaskNames: ['领导审批'],
      startedAt: '2026-03-23T10:00:00+08:00',
      finishedAt: null,
      variables: {
        westflowBusinessKey: 'leave_026',
      },
      inclusiveGatewayHits: [
        {
          splitNodeId: 'inclusive_split_1',
          splitNodeName: '包容分支',
          joinNodeId: 'inclusive_join_1',
          joinNodeName: '包容汇聚',
          gatewayStatus: 'COMPLETED',
          totalTargetCount: 2,
          activatedTargetCount: 1,
          activatedTargetNodeIds: ['approve_finance'],
          activatedTargetNodeNames: ['财务审批'],
          skippedTargetNodeIds: ['approve_hr'],
          skippedTargetNodeNames: ['人事审批'],
          firstActivatedAt: '2026-03-23T10:05:00+08:00',
          finishedAt: '2026-03-23T10:20:00+08:00',
        },
      ],
      processLinks: [
        {
          linkId: 'subprocess_001',
          rootInstanceId: 'pi_001',
          parentInstanceId: 'pi_001',
          childInstanceId: 'pi_sub_001',
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
      runtimeStructureLinks: [
        {
          linkId: 'dynamic_001',
          rootInstanceId: 'pi_001',
          parentInstanceId: 'pi_sub_001',
          childInstanceId: 'pi_dynamic_001',
          parentNodeId: 'dynamic_builder_002',
          calledProcessKey: 'oa_leave_dynamic_subflow',
          calledDefinitionId: 'oa_leave_dynamic_subflow:2:1009',
          linkType: 'ADHOC',
          runtimeLinkType: 'ADHOC_SUBPROCESS',
          triggerMode: 'DYNAMIC_BUILD',
          appendType: 'SUBPROCESS',
          status: 'COMPLETED',
          terminatePolicy: 'TERMINATE_PARENT_AND_GENERATED',
          childFinishPolicy: 'TERMINATE_PARENT',
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

    renderWithQuery(<WorkflowInstanceDetailPage instanceId='pi_001' />)

    expect(await screen.findByText('包容分支命中')).toBeInTheDocument()
    expect(screen.getByText('财务审批')).toBeInTheDocument()
    expect(screen.getByText('人事审批')).toBeInTheDocument()
    expect(await screen.findByText('运行态结构')).toBeInTheDocument()
    expect(screen.getByText('主子流程')).toBeInTheDocument()
    expect(screen.getByText('动态构建')).toBeInTheDocument()
    expect(screen.getByText(/子流程实例：pi_sub_001/)).toBeInTheDocument()
    expect(screen.getByText(/动态构建实例：pi_dynamic_001/)).toBeInTheDocument()
    expect(screen.getByText('结构来源：ADHOC_SUBPROCESS')).toBeInTheDocument()
    expect(screen.getByText('附言：规则命中后自动构建子流程')).toBeInTheDocument()
  })

  it('shows termination strategy, collaboration and time-travel details in instance monitor', async () => {
    advancedRuntimeApiMocks.getProcessTerminationSnapshot.mockResolvedValue({
      rootInstanceId: 'pi_root_001',
      scope: 'ROOT',
      propagationPolicy: 'CASCADE_ALL',
      reason: '实例监控终止策略预览',
      operatorUserId: 'usr_001',
      summary: 'scope=ROOT, propagation=CASCADE_ALL, targets=3',
      targetCount: 3,
      generatedAt: '2026-03-23T10:21:00+08:00',
      nodes: [],
    })
    advancedRuntimeApiMocks.listProcessTerminationAuditTrail.mockResolvedValue([
      {
        auditId: 'audit_001',
        rootInstanceId: 'pi_root_001',
        targetInstanceId: 'pi_001',
        parentInstanceId: 'pi_root_001',
        targetKind: 'SUBPROCESS',
        terminateScope: 'ROOT',
        propagationPolicy: 'CASCADE_ALL',
        eventType: 'EXECUTED',
        resultStatus: 'TERMINATED',
        reason: '管理员终止',
        operatorUserId: 'usr_admin',
        detailJson: '{}',
        createdAt: '2026-03-23T10:22:00+08:00',
        finishedAt: null,
      },
    ])
    advancedRuntimeApiMocks.listProcessCollaborationTrace.mockResolvedValue([
      {
        eventId: 'col_001',
        instanceId: 'pi_001',
        taskId: 'task_001',
        eventType: 'COMMENT',
        subject: '请同步技术说明',
        content: '请同步最新技术说明附件。',
        mentionedUserIds: ['usr_002'],
        permissionCode: 'workflow:collaboration:view',
        actionType: 'COLLABORATION_EVENT_CREATED',
        actionCategory: 'COLLABORATION',
        operatorUserId: 'usr_003',
        occurredAt: '2026-03-23T10:23:00+08:00',
        details: {},
      },
    ])
    advancedRuntimeApiMocks.listProcessTimeTravelTrace.mockResolvedValue([
      {
        executionId: 'tt_001',
        instanceId: 'pi_001',
        strategy: 'REOPEN_INSTANCE',
        taskId: null,
        targetNodeId: null,
        targetTaskId: null,
        newInstanceId: 'pi_reopen_001',
        permissionCode: 'workflow:time-travel:view',
        actionType: 'TIME_TRAVEL_REOPEN_INSTANCE',
        actionCategory: 'TIME_TRAVEL',
        operatorUserId: 'usr_admin',
        occurredAt: '2026-03-23T10:24:00+08:00',
        details: {},
      },
    ])
    getWorkflowInstanceDetailMock.mockResolvedValue({
      processInstanceId: 'pi_001',
      processDefinitionId: 'pd_001',
      flowableDefinitionId: 'flowable_pd_001',
      processKey: 'oa_leave',
      processName: '请假审批',
      businessType: 'OA_LEAVE',
      businessId: 'leave_026',
      startUserId: 'usr_001',
      status: 'RUNNING',
      suspended: false,
      currentTaskNames: ['领导审批'],
      startedAt: '2026-03-23T10:00:00+08:00',
      finishedAt: null,
      variables: {},
      inclusiveGatewayHits: [],
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
      runtimeStructureLinks: [],
    })

    renderWithQuery(<WorkflowInstanceDetailPage instanceId='pi_001' />)

    expect(await screen.findByText('终止高级策略')).toBeInTheDocument()
    expect(screen.getByText('协同轨迹')).toBeInTheDocument()
    expect(screen.getByText('请同步技术说明')).toBeInTheDocument()
    expect(screen.getByText('穿越时空轨迹')).toBeInTheDocument()
    expect(screen.getByText('重开流程实例')).toBeInTheDocument()
  })
})
