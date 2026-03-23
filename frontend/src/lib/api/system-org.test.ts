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

describe('system organization api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('lists and mutates companies', async () => {
    postMock.mockResolvedValueOnce(
      okResponse({
        page: 1,
        pageSize: 20,
        total: 2,
        pages: 1,
        groups: [],
        records: [
          {
            companyId: 'cmp_001',
            companyName: '西流科技',
            status: 'ENABLED',
            createdAt: '2026-03-22T09:00:00+08:00',
          },
        ],
      })
    )
    getMock
      .mockResolvedValueOnce(
        okResponse({
          companyId: 'cmp_001',
          companyName: '西流科技',
          enabled: true,
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          companies: [{ id: 'cmp_001', name: '西流科技', enabled: true }],
        })
      )
    postMock.mockResolvedValueOnce(okResponse({ companyId: 'cmp_new' }))
    putMock.mockResolvedValueOnce(okResponse({ companyId: 'cmp_new' }))

    const {
      listCompanies,
      getCompanyDetail,
      getCompanyFormOptions,
      createCompany,
      updateCompany,
    } = await import('./system-org')

    await expect(
      listCompanies({
        page: 1,
        pageSize: 20,
        keyword: '西流',
        filters: [],
        sorts: [{ field: 'createdAt', direction: 'desc' }],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 2,
      records: [{ companyId: 'cmp_001' }],
    })
    await expect(getCompanyDetail('cmp_001')).resolves.toMatchObject({
      companyName: '西流科技',
    })
    await expect(getCompanyFormOptions()).resolves.toMatchObject({
      companies: [{ id: 'cmp_001' }],
    })
    await expect(
      createCompany({
        companyName: '新公司',
        enabled: true,
      })
    ).resolves.toEqual({ companyId: 'cmp_new' })
    await expect(
      updateCompany('cmp_new', {
        companyName: '新公司',
        enabled: false,
      })
    ).resolves.toEqual({ companyId: 'cmp_new' })
  })

  it('lists and mutates departments and posts', async () => {
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
              departmentId: 'dept_001',
              companyName: '西流科技',
              parentDepartmentName: null,
              departmentName: '财务部',
              status: 'ENABLED',
              createdAt: '2026-03-22T09:00:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(okResponse({ departmentId: 'dept_new' }))
      .mockResolvedValueOnce(
        okResponse({
          page: 1,
          pageSize: 20,
          total: 1,
          pages: 1,
          groups: [],
          records: [
            {
              postId: 'post_001',
              companyName: '西流科技',
              departmentName: '财务部',
              postName: '报销审核岗',
              status: 'ENABLED',
              createdAt: '2026-03-22T09:00:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(okResponse({ postId: 'post_new' }))

    putMock
      .mockResolvedValueOnce(okResponse({ departmentId: 'dept_new' }))
      .mockResolvedValueOnce(okResponse({ postId: 'post_new' }))

    getMock
      .mockResolvedValueOnce(
        okResponse({
          departmentId: 'dept_001',
          companyId: 'cmp_001',
          companyName: '西流科技',
          parentDepartmentId: null,
          parentDepartmentName: null,
          departmentName: '财务部',
          enabled: true,
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          companies: [{ id: 'cmp_001', name: '西流科技', enabled: true }],
          parentDepartments: [
            {
              id: 'dept_001',
              name: '财务部',
              companyId: 'cmp_001',
              companyName: '西流科技',
              parentDepartmentId: null,
              enabled: true,
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          postId: 'post_001',
          companyId: 'cmp_001',
          companyName: '西流科技',
          departmentId: 'dept_001',
          departmentName: '财务部',
          postName: '报销审核岗',
          enabled: true,
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          departments: [
            {
              id: 'dept_001',
              name: '财务部',
              companyId: 'cmp_001',
              companyName: '西流科技',
              enabled: true,
            },
          ],
        })
      )

    const {
      listDepartments,
      getDepartmentDetail,
      getDepartmentFormOptions,
      createDepartment,
      updateDepartment,
      listPosts,
      getPostDetail,
      getPostFormOptions,
      createPost,
      updatePost,
    } = await import('./system-org')

    await expect(
      listDepartments({
        page: 1,
        pageSize: 20,
        keyword: '财务',
        filters: [],
        sorts: [],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ departmentId: 'dept_001' }],
    })
    await expect(getDepartmentDetail('dept_001')).resolves.toMatchObject({
      companyId: 'cmp_001',
    })
    await expect(getDepartmentFormOptions('cmp_001')).resolves.toMatchObject({
      companies: [{ id: 'cmp_001' }],
      parentDepartments: [{ id: 'dept_001', parentDepartmentId: null }],
    })
    await expect(
      createDepartment({
        companyId: 'cmp_001',
        parentDepartmentId: null,
        departmentName: '新部门',
        enabled: true,
      })
    ).resolves.toEqual({ departmentId: 'dept_new' })
    await expect(
      updateDepartment('dept_new', {
        companyId: 'cmp_001',
        parentDepartmentId: null,
        departmentName: '新部门',
        enabled: false,
      })
    ).resolves.toEqual({ departmentId: 'dept_new' })

    await expect(
      listPosts({
        page: 1,
        pageSize: 20,
        keyword: '报销',
        filters: [],
        sorts: [],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ postId: 'post_001' }],
    })
    await expect(getPostDetail('post_001')).resolves.toMatchObject({
      departmentId: 'dept_001',
    })
    await expect(getPostFormOptions('cmp_001')).resolves.toMatchObject({
      departments: [{ id: 'dept_001' }],
    })
    await expect(
      createPost({
        departmentId: 'dept_001',
        postName: '新岗位',
        enabled: true,
      })
    ).resolves.toEqual({ postId: 'post_new' })
    await expect(
      updatePost('post_new', {
        departmentId: 'dept_001',
        postName: '新岗位',
        enabled: false,
      })
    ).resolves.toEqual({ postId: 'post_new' })
  })
})
