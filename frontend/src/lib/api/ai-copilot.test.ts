import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { getMock, postMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
}))

vi.mock('@/lib/api/client', () => ({
  apiClient: {
    get: getMock,
    post: postMock,
    postForm: postMock,
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
        audit: [],
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
      toolCalls: [],
      audit: [],
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
          audit: [],
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
          audit: [],
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
          audit: [],
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
          audit: [],
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
        argumentsOverride: {
          processKey: 'oa_leave',
          businessType: 'OA_LEAVE',
          sceneCode: 'default',
          formData: {
            days: '1',
            reason: '参加客户评审',
          },
        },
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
        attachments: [],
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
        argumentsOverride: {
          processKey: 'oa_leave',
          businessType: 'OA_LEAVE',
          sceneCode: 'default',
          formData: {
            days: '1',
            reason: '参加客户评审',
          },
        },
      }
    )
    expect(getMock).toHaveBeenNthCalledWith(
      2,
      '/ai/copilot/conversations/session_002'
    )
  })

  it('downloads attachment previews as blobs through the authenticated client', async () => {
    const previewBlob = new Blob(['preview'], { type: 'image/png' })
    getMock.mockResolvedValueOnce({ data: previewBlob })

    const { downloadAICopilotAssetPreview } = await import('./ai-copilot')

    await expect(downloadAICopilotAssetPreview('aif_001')).resolves.toBe(
      previewBlob
    )
    expect(getMock).toHaveBeenCalledWith('/ai/copilot/assets/aif_001/preview', {
      responseType: 'blob',
    })
  })

  it('maps result, failure, and trace blocks from the backend contract', async () => {
    getMock.mockResolvedValueOnce(
      okResponse({
        conversationId: 'session_rich_001',
        title: 'AI 执行轨迹演示',
        preview: '我已经整理出本次执行结果与失败信息。',
        status: 'active',
        updatedAt: '2026-03-23T09:10:00.000Z',
        messageCount: 1,
        contextTags: ['PLM', 'AI Copilot'],
        history: [
          {
            messageId: 'msg_001',
            role: 'assistant',
            authorName: 'AI Copilot',
            createdAt: '2026-03-23T09:10:00.000Z',
            content: '这里是结构化输出。',
            blocks: [
              {
                type: 'trace',
                title: '命中轨迹',
                summary: 'Supervisor -> Skill',
                sourceType: 'SKILL',
                sourceKey: 'plm-assistant',
                sourceName: 'PLM 助手',
                trace: [
                  {
                    stage: 'SUPERVISOR',
                    label: 'Supervisor',
                    detail: '命中 PLM 分析意图',
                    status: 'SUCCEEDED',
                  },
                ],
              },
              {
                type: 'result',
                title: '执行结果',
                summary: '已完成 PLM 摘要汇总。',
                sourceType: 'SKILL',
                sourceKey: 'plm.change.summary',
                sourceName: 'PLM 变更摘要',
                toolType: 'READ',
                result: {
                  impactedCount: 4,
                },
                fields: [
                  {
                    label: '变更单号',
                    value: 'ECR-001',
                  },
                ],
                metrics: [
                  {
                    label: '影响对象数',
                    value: '4',
                    tone: 'warning',
                  },
                ],
              },
              {
                type: 'failure',
                title: '执行失败',
                summary: '外部 MCP 检索失败。',
                sourceType: 'MCP',
                sourceKey: 'external.mcp.search',
                sourceName: '外部知识库检索',
                toolType: 'READ',
                failure: {
                  code: 'MCP_TIMEOUT',
                  message: 'MCP 请求超时',
                  detail: '远端服务未响应。',
                },
              },
            ],
          },
        ],
        toolCalls: [],
        audit: [],
      })
    )

    const { getAICopilotSession } = await import('./ai-copilot')

    await expect(getAICopilotSession('session_rich_001')).resolves.toMatchObject({
      sessionId: 'session_rich_001',
      history: [
        expect.objectContaining({
          blocks: expect.arrayContaining([
            expect.objectContaining({
              type: 'trace',
              sourceType: 'SKILL',
              sourceName: 'PLM 助手',
              trace: expect.arrayContaining([
                expect.objectContaining({
                  stage: 'SUPERVISOR',
                }),
              ]),
            }),
            expect.objectContaining({
              type: 'result',
              toolType: 'READ',
              result: expect.objectContaining({
                impactedCount: 4,
              }),
            }),
            expect.objectContaining({
              type: 'failure',
              failure: expect.objectContaining({
                code: 'MCP_TIMEOUT',
              }),
            }),
          ]),
        }),
      ],
    })
  })

  it('maps persisted attachment items from result payloads', async () => {
    getMock.mockResolvedValueOnce(
      okResponse({
        conversationId: 'session_attachment_001',
        title: '附件回放',
        preview: '查看历史附件',
        status: 'active',
        updatedAt: '2026-03-31T22:42:07.000Z',
        messageCount: 1,
        contextTags: ['AI Copilot'],
        history: [
          {
            messageId: 'msg_attachment_001',
            role: 'user',
            authorName: '你',
            createdAt: '2026-03-31T22:42:06.000Z',
            content: '已上传 1 张图片',
            blocks: [
              {
                type: 'attachments',
                result: {
                  items: [
                    {
                      fileId: 'aif_001',
                      displayName: 'leave-request-test-zh-clean.png',
                      contentType: 'image/png',
                      previewUrl: '/api/v1/ai/copilot/assets/aif_001/preview',
                    },
                  ],
                },
              },
            ],
          },
        ],
        toolCalls: [],
        audit: [],
      })
    )

    const { getAICopilotSession } = await import('./ai-copilot')

    await expect(getAICopilotSession('session_attachment_001')).resolves.toMatchObject({
      history: [
        expect.objectContaining({
          blocks: [
            expect.objectContaining({
              type: 'attachments',
              items: [
                expect.objectContaining({
                  fileId: 'aif_001',
                  displayName: 'leave-request-test-zh-clean.png',
                }),
              ],
            }),
          ],
        }),
      ],
    })
  })

  it('enriches task.handle blocks with business action, task id, and next step hints', async () => {
    getMock.mockResolvedValueOnce(
      okResponse({
        conversationId: 'session_task_handle_001',
        title: '待办处理闭环',
        preview: '这里是待办处理结果。',
        status: 'active',
        updatedAt: '2026-03-23T09:30:00.000Z',
        messageCount: 1,
        contextTags: ['工作台', '待办'],
        history: [
          {
            messageId: 'msg_001',
            role: 'assistant',
            authorName: 'AI Copilot',
            createdAt: '2026-03-23T09:30:00.000Z',
            content: '待办处理已经返回结构化结果。',
            blocks: [
              {
                type: 'result',
                title: '执行结果',
                summary: '待办处理成功。',
                sourceType: 'PLATFORM',
                sourceKey: 'task.handle',
                sourceName: 'task.handle',
                toolType: 'WRITE',
                result: {
                  confirmationId: 'confirm_001',
                  toolCallId: 'tool_001',
                  arguments: {
                    taskId: 'task_001',
                    action: 'COMPLETE',
                    comment: '审批通过',
                  },
                },
              },
              {
                type: 'failure',
                title: '执行失败',
                summary: '待办处理失败。',
                sourceType: 'PLATFORM',
                sourceKey: 'task.handle',
                sourceName: 'task.handle',
                toolType: 'WRITE',
                result: {
                  confirmationId: 'confirm_002',
                  retryable: true,
                  arguments: {
                    taskId: 'task_002',
                    action: 'REJECT',
                  },
                },
                failure: {
                  code: 'AI.TOOL_EXECUTE_FAILED',
                  message: '执行失败',
                  detail: '当前任务状态已变化。',
                },
              },
            ],
          },
        ],
        toolCalls: [],
        audit: [],
      })
    )

    const { getAICopilotSession } = await import('./ai-copilot')

    await expect(getAICopilotSession('session_task_handle_001')).resolves.toMatchObject({
      history: [
        expect.objectContaining({
          blocks: expect.arrayContaining([
            expect.objectContaining({
              type: 'result',
              fields: expect.arrayContaining([
                expect.objectContaining({
                  label: '待办动作',
                  value: '完成待办',
                }),
                expect.objectContaining({
                  label: '待办编号',
                  value: 'task_001',
                }),
                expect.objectContaining({
                  label: '下一步建议',
                }),
              ]),
            }),
            expect.objectContaining({
              type: 'failure',
              fields: expect.arrayContaining([
                expect.objectContaining({
                  label: '待办动作',
                  value: '驳回待办',
                }),
                expect.objectContaining({
                  label: '失败原因',
                  value: '执行失败',
                }),
              ]),
            }),
          ]),
        }),
      ],
    })
  })
})
