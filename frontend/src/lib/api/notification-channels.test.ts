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

describe('notification channels api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('lists, loads and mutates notification channels with system endpoints', async () => {
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
              channelId: 'chn_001',
              channelName: '企业微信通知',
              channelType: 'WECHAT_WORK',
              endpoint: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send',
              status: 'ENABLED',
              createdAt: '2026-03-22T09:00:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(okResponse({ channelId: 'chn_new' }))

    getMock
      .mockResolvedValueOnce(
        okResponse({
          channelId: 'chn_001',
          channelName: '企业微信通知',
          channelType: 'WECHAT_WORK',
          endpoint: 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send',
          secret: 'sec_001',
          remark: '流程消息通知',
          status: 'ENABLED',
          createdAt: '2026-03-22T09:00:00+08:00',
          updatedAt: '2026-03-22T09:10:00+08:00',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          channelTypes: [
            { value: 'EMAIL', label: '邮件' },
            { value: 'WEBHOOK', label: 'Webhook' },
          ],
        })
      )

    putMock.mockResolvedValueOnce(okResponse({ channelId: 'chn_new' }))

    const {
      createNotificationChannel,
      getNotificationChannelDetail,
      getNotificationChannelFormOptions,
      listNotificationChannels,
      updateNotificationChannel,
    } = await import('./notification-channels')

    await expect(
      listNotificationChannels({
        page: 1,
        pageSize: 20,
        keyword: '通知',
        filters: [],
        sorts: [{ field: 'createdAt', direction: 'desc' }],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ channelName: '企业微信通知' }],
    })

    await expect(getNotificationChannelDetail('chn_001')).resolves.toMatchObject({
      channelName: '企业微信通知',
      channelType: 'WECHAT_WORK',
    })

    await expect(getNotificationChannelFormOptions()).resolves.toMatchObject({
      channelTypes: expect.arrayContaining([{ value: 'EMAIL', label: '邮件' }]),
    })

    await expect(
      createNotificationChannel({
        channelName: '邮件通知',
        channelType: 'EMAIL',
        endpoint: 'ops@westflow.cn',
        secret: '',
        remark: '用于审批结果通知',
        enabled: true,
      })
    ).resolves.toEqual({ channelId: 'chn_new' })

    await expect(
      updateNotificationChannel('chn_new', {
        channelName: '邮件通知',
        channelType: 'EMAIL',
        endpoint: 'ops@westflow.cn',
        secret: 'sec_new',
        remark: '用于审批结果通知',
        enabled: false,
      })
    ).resolves.toEqual({ channelId: 'chn_new' })
  })
})
