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

describe('triggers api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('lists, loads and mutates automation triggers with system endpoints', async () => {
    postMock
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              triggerId: 'trg_001',
              triggerName: '请假审批完成通知',
              triggerKey: 'LEAVE_DONE_NOTIFY',
              triggerEvent: 'TASK_COMPLETED',
              automationStatus: 'ACTIVE',
              createdAt: '2026-03-22T09:00:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(okResponse({ triggerId: 'trg_new' }))

    getMock
      .mockResolvedValueOnce(
        okResponse({
          triggerId: 'trg_001',
          triggerName: '请假审批完成通知',
          triggerKey: 'LEAVE_DONE_NOTIFY',
          triggerEvent: 'TASK_COMPLETED',
          businessType: 'OA_LEAVE',
          channelIds: ['chn_001'],
          conditionExpression: 'status == "COMPLETED"',
          description: '审批完成后通知发起人',
          enabled: true,
          createdAt: '2026-03-22T09:00:00+08:00',
          updatedAt: '2026-03-22T09:10:00+08:00',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          triggerEvents: [
            { value: 'TASK_CREATED', label: '任务创建' },
            { value: 'TASK_COMPLETED', label: '任务完成' },
          ],
        })
      )

    putMock.mockResolvedValueOnce(okResponse({ triggerId: 'trg_new' }))

    const {
      createTrigger,
      getTriggerDetail,
      getTriggerFormOptions,
      listTriggers,
      updateTrigger,
    } = await import('./triggers')

    await expect(
      listTriggers({
        page: 1,
        pageSize: 20,
        keyword: '通知',
        filters: [],
        sorts: [{ field: 'createdAt', direction: 'desc' }],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ triggerKey: 'LEAVE_DONE_NOTIFY' }],
    })

    await expect(getTriggerDetail('trg_001')).resolves.toMatchObject({
      triggerName: '请假审批完成通知',
      businessType: 'OA_LEAVE',
    })

    await expect(getTriggerFormOptions()).resolves.toMatchObject({
      triggerEvents: expect.arrayContaining([
        { value: 'TASK_CREATED', label: '任务创建' },
      ]),
    })

    await expect(
      createTrigger({
        triggerName: '报销通过通知',
        triggerKey: 'EXPENSE_DONE_NOTIFY',
        triggerEvent: 'TASK_COMPLETED',
        businessType: 'OA_EXPENSE',
        channelIds: ['chn_001'],
        conditionExpression: 'status == "COMPLETED"',
        description: '审批完成后发送通知',
        enabled: true,
      })
    ).resolves.toEqual({ triggerId: 'trg_new' })

    await expect(
      updateTrigger('trg_new', {
        triggerName: '报销通过通知',
        triggerKey: 'EXPENSE_DONE_NOTIFY',
        triggerEvent: 'TASK_COMPLETED',
        businessType: 'OA_EXPENSE',
        channelIds: ['chn_001', 'chn_002'],
        conditionExpression: 'status == "COMPLETED"',
        description: '审批完成后发送通知',
        enabled: false,
      })
    ).resolves.toEqual({ triggerId: 'trg_new' })
  })
})
