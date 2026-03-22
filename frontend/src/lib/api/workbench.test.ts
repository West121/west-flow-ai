import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { getMock, postMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
}))

vi.mock('@/lib/api/client', () => ({
  apiClient: {
    get: getMock,
    post: postMock,
  },
  unwrapResponse: <T>(response: { data: { data: T } }) => response.data.data,
}))

function okResponse<T>(data: T) {
  return {
    data: {
      code: 'OK',
      message: 'success',
      data,
      requestId: 'req_001',
    },
  }
}

describe('workbench api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('builds runtime paths from the new default namespace', async () => {
    const { resolveWorkbenchRuntimePath, WORKBENCH_RUNTIME_ENDPOINTS } =
      await import('./workbench')

    expect(resolveWorkbenchRuntimePath('tasks', 'page')).toBe(
      '/process-runtime/tasks/page'
    )
    expect(resolveWorkbenchRuntimePath('demo', 'tasks', 'page')).toBe(
      '/process-runtime/demo/tasks/page'
    )
    expect(WORKBENCH_RUNTIME_ENDPOINTS.tasksPage).toBe('/process-runtime/tasks/page')
  })

  it('loads workbench tasks with pagination payload', async () => {
    const pageResponse = {
      page: 1,
      pageSize: 20,
      total: 1,
      pages: 1,
      groups: [],
      records: [
        {
          taskId: 'task_001',
          instanceId: 'pi_001',
          processDefinitionId: 'pd_001',
          processKey: 'oa_leave',
          processName: '请假审批',
          businessKey: 'biz_001',
          applicantUserId: 'zhangsan',
          nodeId: 'approve_manager',
          nodeName: '部门负责人审批',
          status: 'PENDING',
          assignmentMode: 'USER',
          candidateUserIds: ['usr_002'],
          createdAt: '2026-03-22T09:00:00+08:00',
          updatedAt: '2026-03-22T09:00:00+08:00',
          completedAt: null,
        },
      ],
    }

    postMock.mockResolvedValue(okResponse(pageResponse))

    const { listWorkbenchTasks } = await import('./workbench')

    await expect(
      listWorkbenchTasks({
        page: 1,
        pageSize: 20,
        keyword: '请假',
        filters: [],
        sorts: [{ field: 'createdAt', direction: 'desc' }],
        groups: [],
      })
    ).resolves.toEqual(pageResponse)

    expect(postMock).toHaveBeenCalledWith('/process-runtime/tasks/page', {
      page: 1,
      pageSize: 20,
      keyword: '请假',
      filters: [],
      sorts: [{ field: 'createdAt', direction: 'desc' }],
      groups: [],
    })
  })

  it('loads task detail and starts/completes runtime tasks', async () => {
    getMock.mockResolvedValueOnce(
      okResponse({
        taskId: 'task_001',
        instanceId: 'pi_001',
        processDefinitionId: 'pd_001',
        processKey: 'oa_leave',
        processName: '请假审批',
        businessKey: 'biz_001',
        applicantUserId: 'zhangsan',
        nodeId: 'approve_manager',
        nodeName: '部门负责人审批',
        status: 'PENDING',
        assignmentMode: 'USER',
        candidateUserIds: ['usr_002'],
        action: null,
        operatorUserId: null,
        comment: null,
        createdAt: '2026-03-22T09:00:00+08:00',
        updatedAt: '2026-03-22T09:00:00+08:00',
        completedAt: null,
        instanceStatus: 'RUNNING',
        formData: { days: 3 },
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
        activeTaskIds: ['task_001'],
      })
    )
    postMock
      .mockResolvedValueOnce(
        okResponse({
          processDefinitionId: 'pd_001',
          instanceId: 'pi_001',
          status: 'RUNNING',
          activeTasks: [
            {
              taskId: 'task_001',
              nodeId: 'approve_manager',
              nodeName: '部门负责人审批',
              status: 'PENDING',
              assignmentMode: 'USER',
              candidateUserIds: ['usr_002'],
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          instanceId: 'pi_001',
          completedTaskId: 'task_001',
          status: 'COMPLETED',
          nextTasks: [],
        })
      )

    const {
      completeWorkbenchTask,
      getWorkbenchTaskDetail,
      startWorkbenchProcess,
    } = await import('./workbench')

    await expect(getWorkbenchTaskDetail('task_001')).resolves.toMatchObject({
      taskId: 'task_001',
      processName: '请假审批',
    })
    await expect(
      startWorkbenchProcess({
        processKey: 'oa_leave',
        businessKey: 'biz_001',
        formData: { days: 3 },
      })
    ).resolves.toMatchObject({
      instanceId: 'pi_001',
    })
    await expect(
      completeWorkbenchTask('task_001', {
        action: 'APPROVE',
        comment: '同意',
        taskFormData: {
          approved: true,
          comment: '同意',
        },
      })
    ).resolves.toMatchObject({
      completedTaskId: 'task_001',
      status: 'COMPLETED',
    })
  })

  it('supports claim transfer return and action capability APIs', async () => {
    getMock.mockResolvedValueOnce(
      okResponse({
        canClaim: true,
        canApprove: false,
        canReject: false,
        canTransfer: false,
        canReturn: false,
      })
    )
    postMock
      .mockResolvedValueOnce(
        okResponse({
          taskId: 'task_001',
          status: 'PENDING',
          assigneeUserId: 'usr_001',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          completedTaskId: 'task_001',
          status: 'TRANSFERRED',
          nextTasks: [
            {
              taskId: 'task_002',
              nodeId: 'approve_manager',
              nodeName: '部门负责人审批',
              status: 'PENDING',
              assignmentMode: 'USER',
              candidateUserIds: ['usr_003'],
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          completedTaskId: 'task_002',
          status: 'RETURNED',
          nextTasks: [
            {
              taskId: 'task_003',
              nodeId: 'approve_previous',
              nodeName: '上一步审批',
              status: 'PENDING',
              assignmentMode: 'USER',
              candidateUserIds: ['usr_001'],
            },
          ],
        })
      )

    const {
      claimWorkbenchTask,
      getWorkbenchTaskActions,
      returnWorkbenchTask,
      transferWorkbenchTask,
    } = await import('./workbench')

    await expect(getWorkbenchTaskActions('task_001')).resolves.toEqual({
      canClaim: true,
      canApprove: false,
      canReject: false,
      canTransfer: false,
      canReturn: false,
    })
    await expect(
      claimWorkbenchTask('task_001', {
        comment: '认领处理',
      })
    ).resolves.toMatchObject({
      taskId: 'task_001',
      status: 'PENDING',
      assigneeUserId: 'usr_001',
    })
    await expect(
      transferWorkbenchTask('task_001', {
        targetUserId: 'usr_003',
        comment: '转给财务',
      })
    ).resolves.toMatchObject({
      completedTaskId: 'task_001',
      status: 'TRANSFERRED',
    })
    await expect(
      returnWorkbenchTask('task_002', {
        targetStrategy: 'PREVIOUS_USER_TASK',
        comment: '退回补充信息',
      })
    ).resolves.toMatchObject({
      completedTaskId: 'task_002',
      status: 'RETURNED',
    })
  })

  it('supports delegate and handover runtime APIs', async () => {
    postMock
      .mockResolvedValueOnce(
        okResponse({
          instanceId: 'pi_001',
          completedTaskId: 'task_001',
          status: 'RUNNING',
          nextTasks: [
            {
              taskId: 'task_delegate_001',
              nodeId: 'approve_manager',
              nodeName: '部门负责人审批',
              status: 'PENDING',
              assignmentMode: 'USER',
              candidateUserIds: ['usr_003'],
              assigneeUserId: 'usr_003',
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          sourceUserId: 'usr_001',
          targetUserId: 'usr_003',
          transferredCount: 2,
          transferredTaskIds: ['task_handover_001', 'task_handover_002'],
          status: 'RUNNING',
        })
      )

    const { delegateWorkbenchTask, handoverWorkbenchTasks } = await import(
      './workbench'
    )

    await expect(
      delegateWorkbenchTask('task_001', {
        targetUserId: 'usr_003',
        comment: '委派给王五代办',
      })
    ).resolves.toMatchObject({
      completedTaskId: 'task_001',
      status: 'RUNNING',
    })

    await expect(
      handoverWorkbenchTasks({
        sourceUserId: 'usr_001',
        targetUserId: 'usr_003',
        comment: '离职转办给王五',
      })
    ).resolves.toMatchObject({
      sourceUserId: 'usr_001',
      targetUserId: 'usr_003',
      transferredCount: 2,
    })
  })
})
