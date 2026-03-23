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
      requestId: 'req_ai_admin',
    },
  }
}

describe('ai admin api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('loads registry pages and detail data', async () => {
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
              agentId: 'agent_001',
              agentCode: 'workflow-router',
              agentName: '流程路由智能体',
              capabilityCode: 'ai:workflow:route',
              routeMode: 'SUPERVISOR',
              supervisor: true,
              priority: 100,
              status: 'ENABLED',
              contextTags: ['流程', '审批'],
              systemPrompt: '你好',
              metadataJson: '{"type":"agent"}',
              createdAt: '2026-03-23T09:00:00+08:00',
              updatedAt: '2026-03-23T09:10:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              toolId: 'tool_001',
              toolCode: 'task-summary',
              toolName: '待办总结',
              toolCategory: 'workflow',
              actionMode: 'READ',
              requiredCapabilityCode: 'ai:task:read',
              status: 'ENABLED',
              metadataJson: '{"domain":"workflow"}',
              createdAt: '2026-03-23T09:00:00+08:00',
              updatedAt: '2026-03-23T09:10:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              mcpId: 'mcp_001',
              mcpCode: 'internal-mcp',
              mcpName: '平台内置 MCP',
              endpointUrl: null,
              transportType: 'INTERNAL',
              requiredCapabilityCode: 'ai:copilot:internal',
              status: 'ENABLED',
              metadataJson: '{}',
              createdAt: '2026-03-23T09:00:00+08:00',
              updatedAt: '2026-03-23T09:10:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              skillId: 'skill_001',
              skillCode: 'workflow-design-skill',
              skillName: '流程设计技能',
              skillPath: 'classpath:ai/skills/workflow-design-skill.md',
              requiredCapabilityCode: 'ai:workflow:design',
              status: 'ENABLED',
              metadataJson: '{"type":"local-skill"}',
              createdAt: '2026-03-23T09:00:00+08:00',
              updatedAt: '2026-03-23T09:10:00+08:00',
            },
          ],
        })
      )

    getMock
      .mockResolvedValueOnce(
        okResponse({
          agentId: 'agent_001',
          agentCode: 'workflow-router',
          agentName: '流程路由智能体',
          capabilityCode: 'ai:workflow:route',
          routeMode: 'SUPERVISOR',
          supervisor: true,
          priority: 100,
          status: 'ENABLED',
          contextTags: ['流程', '审批'],
          systemPrompt: '你好',
          metadataJson: '{"type":"agent"}',
          createdAt: '2026-03-23T09:00:00+08:00',
          updatedAt: '2026-03-23T09:10:00+08:00',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          toolId: 'tool_001',
          toolCode: 'task-summary',
          toolName: '待办总结',
          toolCategory: 'workflow',
          actionMode: 'READ',
          requiredCapabilityCode: 'ai:task:read',
          status: 'ENABLED',
          metadataJson: '{"domain":"workflow"}',
          description: '总结待办',
          createdAt: '2026-03-23T09:00:00+08:00',
          updatedAt: '2026-03-23T09:10:00+08:00',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          mcpId: 'mcp_001',
          mcpCode: 'internal-mcp',
          mcpName: '平台内置 MCP',
          endpointUrl: null,
          transportType: 'INTERNAL',
          requiredCapabilityCode: 'ai:copilot:internal',
          status: 'ENABLED',
          metadataJson: '{}',
          description: '平台桥',
          createdAt: '2026-03-23T09:00:00+08:00',
          updatedAt: '2026-03-23T09:10:00+08:00',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          skillId: 'skill_001',
          skillCode: 'workflow-design-skill',
          skillName: '流程设计技能',
          skillPath: 'classpath:ai/skills/workflow-design-skill.md',
          requiredCapabilityCode: 'ai:workflow:design',
          status: 'ENABLED',
          metadataJson: '{"type":"local-skill"}',
          description: '设计辅助',
          createdAt: '2026-03-23T09:00:00+08:00',
          updatedAt: '2026-03-23T09:10:00+08:00',
        })
      )

    const aiAdminApi = await import('./ai-admin')

    await expect(
      aiAdminApi.listAiAgents({
        page: 1,
        pageSize: 20,
        keyword: '流程',
        filters: [{ field: 'status', operator: 'eq', value: 'ENABLED' }],
        sorts: [{ field: 'updatedAt', direction: 'desc' }],
        groups: [{ field: 'capabilityCode' }],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ agentCode: 'workflow-router', supervisor: true }],
    })

    await expect(aiAdminApi.getAiAgentDetail('agent_001')).resolves.toMatchObject({
      agentId: 'agent_001',
      routeMode: 'SUPERVISOR',
    })

    await expect(aiAdminApi.listAiTools({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })).resolves.toMatchObject({
      records: [{ toolCode: 'task-summary', actionMode: 'READ' }],
    })

    await expect(aiAdminApi.getAiToolDetail('tool_001')).resolves.toMatchObject({
      toolId: 'tool_001',
      description: '总结待办',
    })

    await expect(aiAdminApi.listAiMcps({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })).resolves.toMatchObject({
      records: [{ mcpCode: 'internal-mcp', transportType: 'INTERNAL' }],
    })

    await expect(aiAdminApi.getAiMcpDetail('mcp_001')).resolves.toMatchObject({
      mcpId: 'mcp_001',
      description: '平台桥',
    })

    await expect(aiAdminApi.listAiSkills({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })).resolves.toMatchObject({
      records: [{ skillCode: 'workflow-design-skill', skillPath: 'classpath:ai/skills/workflow-design-skill.md' }],
    })

    await expect(aiAdminApi.getAiSkillDetail('skill_001')).resolves.toMatchObject({
      skillId: 'skill_001',
      description: '设计辅助',
    })
  })

  it('creates updates and loads records', async () => {
    postMock
      .mockResolvedValueOnce(okResponse({ agentId: 'agent_new' }))
      .mockResolvedValueOnce(okResponse({ mcpId: 'mcp_new' }))
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              conversationId: 'conv_001',
              title: '流程设计咨询',
              preview: '请帮我梳理',
              status: 'ACTIVE',
              contextTags: ['workflow'],
              messageCount: 3,
              operatorUserId: 'admin',
              createdAt: '2026-03-23T09:00:00+08:00',
              updatedAt: '2026-03-23T09:10:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              toolCallId: 'call_001',
              conversationId: 'conv_001',
              toolKey: 'task-summary',
              toolType: 'READ',
              toolSource: 'PLATFORM',
              status: 'EXECUTED',
              requiresConfirmation: false,
              summary: '已总结 3 条待办',
              confirmationId: null,
              operatorUserId: 'admin',
              createdAt: '2026-03-23T09:00:00+08:00',
              completedAt: '2026-03-23T09:01:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              confirmationId: 'confirm_001',
              toolCallId: 'call_002',
              status: 'APPROVED',
              approved: true,
              comment: '确认执行',
              resolvedBy: 'admin',
              createdAt: '2026-03-23T09:00:00+08:00',
              resolvedAt: '2026-03-23T09:01:00+08:00',
              updatedAt: '2026-03-23T09:01:00+08:00',
            },
          ],
        })
      )

    putMock
      .mockResolvedValueOnce(okResponse({ toolId: 'tool_new' }))
      .mockResolvedValueOnce(okResponse({ skillId: 'skill_new' }))

    getMock
      .mockResolvedValueOnce(
        okResponse({
          conversationId: 'conv_001',
          title: '流程设计咨询',
          preview: '请帮我梳理',
          status: 'ACTIVE',
          contextTags: ['workflow'],
          messageCount: 3,
          operatorUserId: 'admin',
          createdAt: '2026-03-23T09:00:00+08:00',
          updatedAt: '2026-03-23T09:10:00+08:00',
          messages: [],
          toolCalls: [],
          confirmations: [],
          audits: [],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          toolCallId: 'call_001',
          conversationId: 'conv_001',
          toolKey: 'task-summary',
          toolType: 'READ',
          toolSource: 'PLATFORM',
          status: 'EXECUTED',
          requiresConfirmation: false,
          summary: '已总结 3 条待办',
          confirmationId: null,
          operatorUserId: 'admin',
          createdAt: '2026-03-23T09:00:00+08:00',
          completedAt: '2026-03-23T09:01:00+08:00',
          argumentsJson: '{}',
          resultJson: '{}',
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          confirmationId: 'confirm_001',
          toolCallId: 'call_002',
          status: 'APPROVED',
          approved: true,
          comment: '确认执行',
          resolvedBy: 'admin',
          createdAt: '2026-03-23T09:00:00+08:00',
          resolvedAt: '2026-03-23T09:01:00+08:00',
          updatedAt: '2026-03-23T09:01:00+08:00',
        })
      )

    const aiAdminApi = await import('./ai-admin')

    await expect(
      aiAdminApi.createAiAgent({
        agentCode: 'workflow-router',
        agentName: '流程路由智能体',
        capabilityCode: 'ai:workflow:route',
        routeMode: 'SUPERVISOR',
        supervisor: true,
        priority: 100,
        contextTags: ['流程'],
        systemPrompt: '你是流程智能体',
        metadataJson: '{}',
        enabled: true,
      })
    ).resolves.toEqual({ agentId: 'agent_new' })

    await expect(
      aiAdminApi.updateAiTool('tool_new', {
        toolCode: 'task-summary',
        toolName: '待办总结',
        toolCategory: 'workflow',
        actionMode: 'READ',
        requiredCapabilityCode: 'ai:task:read',
        metadataJson: '{}',
        enabled: true,
      })
    ).resolves.toEqual({ toolId: 'tool_new' })

    await expect(
      aiAdminApi.createAiMcp({
        mcpCode: 'internal-mcp',
        mcpName: '平台内置 MCP',
        endpointUrl: '',
        transportType: 'INTERNAL',
        requiredCapabilityCode: 'ai:copilot:internal',
        metadataJson: '{}',
        enabled: true,
      })
    ).resolves.toEqual({ mcpId: 'mcp_new' })

    await expect(
      aiAdminApi.updateAiSkill('skill_new', {
        skillCode: 'workflow-design-skill',
        skillName: '流程设计技能',
        skillPath: 'classpath:ai/skills/workflow-design-skill.md',
        requiredCapabilityCode: 'ai:workflow:design',
        metadataJson: '{}',
        enabled: true,
      })
    ).resolves.toEqual({ skillId: 'skill_new' })

    await expect(aiAdminApi.listAiConversations({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })).resolves.toMatchObject({
      records: [{ conversationId: 'conv_001', title: '流程设计咨询' }],
    })

    await expect(aiAdminApi.getAiConversationDetail('conv_001')).resolves.toMatchObject({
      conversationId: 'conv_001',
      messages: [],
      toolCalls: [],
    })

    await expect(aiAdminApi.listAiToolCalls({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })).resolves.toMatchObject({
      records: [{ toolCallId: 'call_001', status: 'EXECUTED' }],
    })

    await expect(aiAdminApi.getAiToolCallDetail('call_001')).resolves.toMatchObject({
      toolCallId: 'call_001',
      argumentsJson: '{}',
    })

    await expect(aiAdminApi.listAiConfirmations({
      page: 1,
      pageSize: 20,
      keyword: '',
      filters: [],
      sorts: [],
      groups: [],
    })).resolves.toMatchObject({
      records: [{ confirmationId: 'confirm_001', approved: true }],
    })

    await expect(aiAdminApi.getAiConfirmationDetail('confirm_001')).resolves.toMatchObject({
      confirmationId: 'confirm_001',
      status: 'APPROVED',
    })
  })
})
