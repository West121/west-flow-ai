import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { postMock, getMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
}))

vi.mock('@/lib/api/client', () => ({
  apiClient: {
    post: postMock,
    get: getMock,
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

describe('workflow api', () => {
  beforeEach(() => {
    vi.resetModules()
    postMock.mockReset()
    getMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('posts the shared pagination contract to process-definitions/page and returns the page payload', async () => {
    const pageResponse = {
      page: 2,
      pageSize: 20,
      total: 42,
      pages: 3,
      records: [
        {
          processDefinitionId: 'proc_leave_v3',
          processKey: 'oa_leave',
          processName: '请假流程',
          version: 3,
          status: 'PUBLISHED',
          category: 'OA',
          createdAt: '2026-03-21T10:20:30+08:00',
        },
      ],
      groups: [],
    }

    postMock.mockResolvedValue(okResponse(pageResponse))

    const { listProcessDefinitions } = await import('./workflow')

    await expect(
      listProcessDefinitions({
        page: 2,
        pageSize: 20,
        keyword: '请假',
        filters: [{ field: 'status', operator: 'eq', value: 'PUBLISHED' }],
        sorts: [{ field: 'createdAt', direction: 'desc' }],
        groups: [],
      })
    ).resolves.toEqual(pageResponse)

    expect(postMock).toHaveBeenCalledWith('/process-definitions/page', {
      page: 2,
      pageSize: 20,
      keyword: '请假',
      filters: [{ field: 'status', operator: 'eq', value: 'PUBLISHED' }],
      sorts: [{ field: 'createdAt', direction: 'desc' }],
      groups: [],
    })
  })

  it('loads, saves, and publishes process definitions through the workflow API contract', async () => {
    const detailResponse = {
      processDefinitionId: 'oa_leave:draft',
      processKey: 'oa_leave',
      processName: '请假审批',
      category: 'OA',
      version: 0,
      status: 'DRAFT',
      createdAt: '2026-03-22T10:00:00+08:00',
      updatedAt: '2026-03-22T10:00:00+08:00',
      dsl: {
        dslVersion: '1.0.0',
        processKey: 'oa_leave',
        processName: '请假审批',
        category: 'OA',
        formKey: 'oa-leave-form',
        formVersion: '1.0.0',
        formFields: [],
        settings: {
          allowWithdraw: true,
          allowUrge: true,
          allowTransfer: true,
        },
        nodes: [],
        edges: [],
      },
      bpmnXml: '<process id="oa_leave:draft" />',
    }

    getMock.mockResolvedValueOnce(okResponse(detailResponse))
    postMock.mockResolvedValueOnce(okResponse(detailResponse))
    postMock.mockResolvedValueOnce(okResponse(detailResponse))

    const {
      getProcessDefinitionDetail,
      saveProcessDefinition,
      publishProcessDefinition,
    } = await import('./workflow')

    await expect(getProcessDefinitionDetail('oa_leave:draft')).resolves.toEqual(
      detailResponse
    )
    await expect(saveProcessDefinition(detailResponse.dsl)).resolves.toEqual(
      detailResponse
    )
    await expect(publishProcessDefinition(detailResponse.dsl)).resolves.toEqual(
      detailResponse
    )

    expect(getMock).toHaveBeenCalledWith('/process-definitions/oa_leave:draft')
    expect(postMock).toHaveBeenNthCalledWith(
      1,
      '/process-definitions/draft',
      detailResponse.dsl
    )
    expect(postMock).toHaveBeenNthCalledWith(
      2,
      '/process-definitions/publish',
      detailResponse.dsl
    )
  })
})
