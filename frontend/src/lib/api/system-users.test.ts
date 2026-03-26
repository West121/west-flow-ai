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

describe('system users api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('posts shared pagination payload to system users page endpoint', async () => {
    const pageResponse = {
      page: 1,
      pageSize: 20,
      total: 3,
      pages: 1,
      groups: [],
      records: [
        {
          userId: 'usr_001',
          displayName: '张三',
          username: 'zhangsan',
          mobile: '13800000001',
          email: 'zhangsan@westflow.cn',
          departmentName: '财务部',
          postName: '报销审核岗',
          status: 'ENABLED',
          createdAt: '2026-03-22T09:00:00+08:00',
        },
      ],
    }

    postMock.mockResolvedValue(okResponse(pageResponse))

    const { listSystemUsers } = await import('./system-users')

    await expect(
      listSystemUsers({
        page: 1,
        pageSize: 20,
        keyword: '张',
        filters: [{ field: 'status', operator: 'eq', value: 'ENABLED' }],
        sorts: [{ field: 'createdAt', direction: 'desc' }],
        groups: [],
      })
    ).resolves.toEqual(pageResponse)

    expect(postMock).toHaveBeenCalledWith('/system/users/page', {
      page: 1,
      pageSize: 20,
      keyword: '张',
      filters: [{ field: 'status', operator: 'eq', value: 'ENABLED' }],
      sorts: [{ field: 'createdAt', direction: 'desc' }],
      groups: [],
    })
  })

  it('loads detail and options with get requests', async () => {
    getMock
      .mockResolvedValueOnce(
        okResponse({
          userId: 'usr_001',
          displayName: '张三',
          username: 'zhangsan',
          mobile: '13800000001',
          email: 'zhangsan@westflow.cn',
          companyId: 'cmp_001',
          companyName: '西流科技',
          departmentId: 'dept_001',
          departmentName: '财务部',
          postId: 'post_001',
          postName: '报销审核岗',
          roleIds: ['role_oa_user', 'role_dept_manager'],
          enabled: true,
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          companies: [{ id: 'cmp_001', name: '西流科技' }],
          posts: [
            {
              id: 'post_001',
              name: '报销审核岗',
              departmentId: 'dept_001',
              departmentName: '财务部',
            },
          ],
          roles: [
            {
              id: 'role_oa_user',
              name: 'OA 普通用户',
              roleCode: 'OA_USER',
              roleCategory: 'SYSTEM',
            },
          ],
        })
      )

    const { getSystemUserDetail, getSystemUserFormOptions } = await import(
      './system-users'
    )

    await expect(getSystemUserDetail('usr_001')).resolves.toMatchObject({
      userId: 'usr_001',
      displayName: '张三',
    })
    await expect(getSystemUserFormOptions()).resolves.toMatchObject({
      companies: [{ id: 'cmp_001', name: '西流科技' }],
    })

    expect(getMock).toHaveBeenNthCalledWith(1, '/system/users/usr_001')
    expect(getMock).toHaveBeenNthCalledWith(2, '/system/users/options')
  })

  it('creates and updates a system user', async () => {
    postMock.mockResolvedValueOnce(okResponse({ userId: 'usr_new' }))
    putMock.mockResolvedValueOnce(okResponse({ userId: 'usr_new' }))

    const { createSystemUser, updateSystemUser } = await import('./system-users')
    const payload = {
      displayName: '赵六',
      username: 'zhaoliu',
      mobile: '13800000009',
      email: 'zhaoliu@westflow.cn',
      companyId: 'cmp_001',
      primaryPostId: 'post_001',
      roleIds: ['role_oa_user'],
      primaryAssignment: {
        companyId: 'cmp_001',
        postId: 'post_001',
        roleIds: ['role_oa_user'],
        enabled: true,
      },
      partTimeAssignments: [],
      enabled: true,
    }

    await expect(createSystemUser(payload)).resolves.toEqual({
      userId: 'usr_new',
    })
    await expect(updateSystemUser('usr_new', payload)).resolves.toEqual({
      userId: 'usr_new',
    })

    expect(postMock).toHaveBeenCalledWith('/system/users', payload)
    expect(putMock).toHaveBeenCalledWith('/system/users/usr_new', payload)
  })
})
