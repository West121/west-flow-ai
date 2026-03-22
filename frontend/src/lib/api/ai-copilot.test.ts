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

describe('ai-copilot api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('lists sessions and loads session detail through conversation resources', async () => {
    postMock.mockResolvedValueOnce(
      okResponse({
        page: 1,
        pageSize: 50,
        total: 1,
        pages: 1,
        records: [
          {
            conversationId: 'session_001',
            title: '待办分流建议',
            preview: '我已经梳理出 3 个可直接执行的确认动作。',
            status: 'active',
            updatedAt: '2026-03-22T18:40:00.000Z',
            messageCount: 4,
            contextTags: ['工作台', '待办', '确认卡'],
          },
        ],
        groups: [],
      })
    )
    getMock.mockResolvedValueOnce(
      okResponse({
        conversationId: 'session_001',
        title: '待办分流建议',
        preview: '我已经梳理出 3 个可直接执行的确认动作。',
        status: 'active',
        updatedAt: '2026-03-22T18:40:00.000Z',
        messageCount: 4,
        contextTags: ['工作台', '待办', '确认卡'],
        history: [],
        toolCalls: [],
      })
    )

    const { getAICopilotSession, listAICopilotSessions } = await import(
      './ai-copilot'
    )

    await expect(listAICopilotSessions()).resolves.toEqual([
      {
        sessionId: 'session_001',
        title: '待办分流建议',
        preview: '我已经梳理出 3 个可直接执行的确认动作。',
        status: 'active',
        updatedAt: '2026-03-22T18:40:00.000Z',
        messageCount: 4,
        contextTags: ['工作台', '待办', '确认卡'],
      },
    ])

    await expect(getAICopilotSession('session_001')).resolves.toEqual({
      sessionId: 'session_001',
      title: '待办分流建议',
      preview: '我已经梳理出 3 个可直接执行的确认动作。',
      status: 'active',
      updatedAt: '2026-03-22T18:40:00.000Z',
      messageCount: 4,
      contextTags: ['工作台', '待办', '确认卡'],
      history: [],
    })

    expect(postMock).toHaveBeenNthCalledWith(1, '/ai/copilot/conversations/page', {
      page: 1,
      pageSize: 50,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })
    expect(getMock).toHaveBeenNthCalledWith(
      1,
      '/ai/copilot/conversations/session_001'
    )
  })

  it('creates sessions, sends messages, and resolves confirmation cards', async () => {
    postMock
      .mockResolvedValueOnce(
        okResponse({
          conversationId: 'session_002',
          title: '测试会话',
          preview: '刚创建的会话可以立刻开始对话。',
          status: 'active',
          updatedAt: '2026-03-23T08:00:00.000Z',
          messageCount: 1,
          contextTags: ['验证'],
          history: [],
          toolCalls: [],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          conversationId: 'session_002',
          title: '测试会话',
          preview: '请生成一个确认卡和统计卡',
          status: 'active',
          updatedAt: '2026-03-23T08:01:00.000Z',
          messageCount: 3,
          contextTags: ['验证'],
          history: [
            {
              messageId: 'msg_user_001',
              role: 'user',
              authorName: '你',
              createdAt: '2026-03-23T08:00:30.000Z',
              content: '请生成一个确认卡和统计卡',
            },
            {
              messageId: 'msg_assistant_001',
              role: 'assistant',
              authorName: 'AI Copilot',
              createdAt: '2026-03-23T08:01:00.000Z',
              content: '请先确认是否继续执行。',
              blocks: [
                {
                  type: 'confirm',
                  confirmationId: 'confirm_001',
                  title: '是否继续执行？',
                  summary: '确认后会记录审计并触发后续动作。',
                  confirmLabel: '确认处理',
                  cancelLabel: '稍后再说',
                  status: 'pending',
                },
              ],
            },
          ],
          toolCalls: [{ toolCallId: 'tool_001', confirmationId: 'confirm_001' }],
        })
      )
      .mockResolvedValueOnce(okResponse({}))

    getMock
      .mockResolvedValueOnce(
        okResponse({
          conversationId: 'session_002',
          title: '测试会话',
          preview: '请生成一个确认卡和统计卡',
          status: 'active',
          updatedAt: '2026-03-23T08:01:00.000Z',
          messageCount: 3,
          contextTags: ['验证'],
          history: [
            {
              messageId: 'msg_user_001',
              role: 'user',
              authorName: '你',
              createdAt: '2026-03-23T08:00:30.000Z',
              content: '请生成一个确认卡和统计卡',
            },
          ],
          toolCalls: [{ toolCallId: 'tool_001', confirmationId: 'confirm_001' }],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          conversationId: 'session_002',
          title: '测试会话',
          preview: '请生成一个确认卡和统计卡',
          status: 'active',
          updatedAt: '2026-03-23T08:02:00.000Z',
          messageCount: 3,
          contextTags: ['验证'],
          history: [
            {
              messageId: 'msg_user_001',
              role: 'user',
              authorName: '你',
              createdAt: '2026-03-23T08:00:30.000Z',
              content: '请生成一个确认卡和统计卡',
            },
            {
              messageId: 'msg_assistant_001',
              role: 'assistant',
              authorName: 'AI Copilot',
              createdAt: '2026-03-23T08:02:00.000Z',
              content: '确认已完成。',
              blocks: [
                {
                  type: 'confirm',
                  confirmationId: 'confirm_001',
                  title: '是否继续执行？',
                  summary: '确认后会记录审计并触发后续动作。',
                  confirmLabel: '确认处理',
                  cancelLabel: '稍后再说',
                  status: 'confirmed',
                  resolvedAt: '2026-03-23T08:02:00.000Z',
                },
              ],
            },
          ],
          toolCalls: [{ toolCallId: 'tool_001', confirmationId: 'confirm_001' }],
        })
      )

    const {
      confirmAICopilotConfirmation,
      createAICopilotSession,
      sendAICopilotMessage,
    } = await import('./ai-copilot')

    await expect(
      createAICopilotSession({
        title: '测试会话',
        contextTags: ['验证'],
      })
    ).resolves.toMatchObject({
      sessionId: 'session_002',
      title: '测试会话',
    })

    await expect(
      sendAICopilotMessage({
        sessionId: 'session_002',
        content: '请生成一个确认卡和统计卡',
      })
    ).resolves.toMatchObject({
      messageCount: 3,
      history: expect.arrayContaining([
        expect.objectContaining({
          blocks: expect.arrayContaining([
            expect.objectContaining({
              type: 'confirm',
              status: 'pending',
            }),
          ]),
        }),
      ]),
    })

    await expect(
      confirmAICopilotConfirmation({
        sessionId: 'session_002',
        confirmationId: 'confirm_001',
        decision: 'confirm',
      })
    ).resolves.toMatchObject({
      messageCount: 3,
      history: expect.arrayContaining([
        expect.objectContaining({
          blocks: expect.arrayContaining([
            expect.objectContaining({
              type: 'confirm',
              status: 'confirmed',
            }),
          ]),
        }),
      ]),
    })

    expect(postMock).toHaveBeenNthCalledWith(1, '/ai/copilot/conversations', {
      title: '测试会话',
      contextTags: ['验证'],
    })
    expect(postMock).toHaveBeenNthCalledWith(
      2,
      '/ai/copilot/conversations/session_002/messages',
      {
        content: '请生成一个确认卡和统计卡',
      }
    )
    expect(getMock).toHaveBeenNthCalledWith(
      1,
      '/ai/copilot/conversations/session_002'
    )
    expect(postMock).toHaveBeenNthCalledWith(
      3,
      '/ai/copilot/tool-calls/tool_001/confirm',
      {
        approved: true,
        comment: undefined,
      }
    )
    expect(getMock).toHaveBeenNthCalledWith(
      2,
      '/ai/copilot/conversations/session_002'
    )
  })
})
