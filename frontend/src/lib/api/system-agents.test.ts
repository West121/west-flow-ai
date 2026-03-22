import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { getMock, postMock, putMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  putMock: vi.fn(),
}))

vi.mock('@/lib/api/client', () => ({
  apiClient: {
    get: getMock,
    post: postMock,
    put: putMock,
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

describe('system agents api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('loads agent list, detail and form options', async () => {
    postMock.mockResolvedValueOnce(
      okResponse({
        page: 1,
        pageSize: 20,
        total: 1,
        pages: 1,
        groups: [],
        records: [
          {
            agentId: 'agt_001',
            principalUserId: 'usr_001',
            principalDisplayName: '张三',
            principalUsername: 'zhangsan',
            principalDepartmentName: '财务部',
            principalPostName: '报销审核岗',
            delegateUserId: 'usr_002',
            delegateDisplayName: '李四',
            delegateUsername: 'lisi',
            delegateDepartmentName: '人力资源部',
            delegatePostName: '请假复核岗',
            remark: '代理请假审批',
            status: 'ACTIVE',
            createdAt: '2026-03-22T09:00:00+08:00',
            updatedAt: '2026-03-22T09:10:00+08:00',
          },
        ],
      })
    )
    getMock
      .mockResolvedValueOnce(
        okResponse({
          agentId: 'agt_001',
          principalUserId: 'usr_001',
          principalDisplayName: '张三',
          principalUsername: 'zhangsan',
          principalDepartmentName: '财务部',
          principalPostName: '报销审核岗',
          delegateUserId: 'usr_002',
          delegateDisplayName: '李四',
          delegateUsername: 'lisi',
          delegateDepartmentName: '人力资源部',
          delegatePostName: '请假复核岗',
          remark: '代理请假审批',
          status: 'ACTIVE',
          createdAt: '2026-03-22T09:00:00+08:00',
          updatedAt: '2026-03-22T09:10:00+08:00',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          principalUsers: [
            {
              userId: 'usr_001',
              displayName: '张三',
              username: 'zhangsan',
              departmentName: '财务部',
              postName: '报销审核岗',
              enabled: true,
            },
          ],
          delegateUsers: [
            {
              userId: 'usr_002',
              displayName: '李四',
              username: 'lisi',
              departmentName: '人力资源部',
              postName: '请假复核岗',
              enabled: true,
            },
          ],
          statusOptions: [
            { value: 'ACTIVE', label: '启用' },
            { value: 'DISABLED', label: '停用' },
          ],
        })
      )

    const {
      listSystemAgents,
      getSystemAgentDetail,
      getSystemAgentFormOptions,
    } = await import('./system-agents')

    await expect(
      listSystemAgents({
        page: 1,
        pageSize: 20,
        keyword: '代理',
        filters: [],
        sorts: [{ field: 'updatedAt', direction: 'desc' }],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [
        {
          agentId: 'agt_001',
          sourceUserId: 'usr_001',
          sourceUserName: '张三',
          targetUserId: 'usr_002',
          targetUserName: '李四',
        },
      ],
    })

    await expect(getSystemAgentDetail('agt_001')).resolves.toMatchObject({
      agentId: 'agt_001',
      sourceUserName: '张三',
      targetUserName: '李四',
    })

    await expect(getSystemAgentFormOptions()).resolves.toMatchObject({
      users: [
        { id: 'usr_001', displayName: '张三' },
        { id: 'usr_002', displayName: '李四' },
      ],
    })
  })

  it('creates updates and executes handover flows', async () => {
    postMock
      .mockResolvedValueOnce(okResponse({ agentId: 'agt_new' }))
      .mockResolvedValueOnce(
        okResponse({
          sourceUserId: 'usr_001',
          sourceDisplayName: '张三',
          targetUserId: 'usr_002',
          targetDisplayName: '李四',
          previewTaskCount: 2,
          previewTasks: [
            {
              taskId: 'task_001',
              instanceId: 'pi_001',
              processName: '请假审批',
              businessTitle: '请假申请 · 事假',
              billNo: 'LEAVE-001',
              currentNodeName: '部门负责人审批',
              assigneeUserId: 'usr_001',
              createdAt: '2026-03-22T09:00:00+08:00',
              canTransfer: true,
              reason: null,
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          sourceUserId: 'usr_001',
          sourceDisplayName: '张三',
          targetUserId: 'usr_002',
          targetDisplayName: '李四',
          executedTaskCount: 2,
          executionTasks: [
            {
              sourceTaskId: 'task_001',
              targetTaskId: 'task_002',
              instanceId: 'pi_001',
              processName: '请假审批',
              businessTitle: '请假申请 · 事假',
              billNo: 'LEAVE-001',
              currentNodeName: '部门负责人审批',
              assigneeUserId: 'usr_002',
              executedAt: '2026-03-22T09:10:00+08:00',
              status: 'HANDOVERED',
              canTransfer: true,
              reason: null,
            },
          ],
        })
      )

    putMock.mockResolvedValueOnce(okResponse({ agentId: 'agt_new' }))

    const {
      createSystemAgent,
      updateSystemAgent,
      previewSystemHandover,
      executeSystemHandover,
    } = await import('./system-agents')

    await expect(
      createSystemAgent({
        sourceUserId: 'usr_001',
        targetUserId: 'usr_002',
        description: '代理请假审批',
        enabled: true,
      })
    ).resolves.toEqual({ agentId: 'agt_new' })

    await expect(
      updateSystemAgent('agt_new', {
        sourceUserId: 'usr_001',
        targetUserId: 'usr_003',
        description: '更新代理关系',
        enabled: false,
      })
    ).resolves.toEqual({ agentId: 'agt_new' })

    await expect(
      previewSystemHandover({
        sourceUserId: 'usr_001',
        targetUserId: 'usr_002',
        comment: '离职转办预览',
      })
    ).resolves.toMatchObject({
      transferableCount: 2,
      tasks: [{ taskId: 'task_001', billNo: 'LEAVE-001' }],
    })

    await expect(
      executeSystemHandover({
        sourceUserId: 'usr_001',
        targetUserId: 'usr_002',
        comment: '离职转办执行',
      })
    ).resolves.toMatchObject({
      transferredCount: 2,
      transferredTaskIds: ['task_002'],
      tasks: [{ taskId: 'task_002', currentTaskStatus: 'HANDOVERED' }],
    })

    expect(postMock).toHaveBeenNthCalledWith(
      1,
      '/system/agents',
      expect.objectContaining({
        principalUserId: 'usr_001',
        delegateUserId: 'usr_002',
        status: 'ACTIVE',
      })
    )
    expect(putMock).toHaveBeenCalledWith(
      '/system/agents/agt_new',
      expect.objectContaining({
        principalUserId: 'usr_001',
        delegateUserId: 'usr_003',
        status: 'DISABLED',
      })
    )
  })
})
