import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { WorkflowInstanceDetailPage } from './management-pages'

const { getWorkflowInstanceDetailMock } = vi.hoisted(() => ({
  getWorkflowInstanceDetailMock: vi.fn(),
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

    expect(await screen.findByText('运行态结构')).toBeInTheDocument()
    expect(screen.getByText('主子流程')).toBeInTheDocument()
    expect(screen.getByText('动态构建')).toBeInTheDocument()
    expect(screen.getByText(/子流程实例：pi_sub_001/)).toBeInTheDocument()
    expect(screen.getByText(/动态构建实例：pi_dynamic_001/)).toBeInTheDocument()
    expect(screen.getByText('结构来源：ADHOC_SUBPROCESS')).toBeInTheDocument()
    expect(screen.getByText('附言：规则命中后自动构建子流程')).toBeInTheDocument()
  })
})
