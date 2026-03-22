import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}))

vi.mock('@/lib/api/client', () => ({
  apiClient: {
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

describe('workflow api', () => {
  beforeEach(() => {
    vi.resetModules()
    postMock.mockReset()
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
})
