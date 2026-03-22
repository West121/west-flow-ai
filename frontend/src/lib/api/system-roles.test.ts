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

describe('system role api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('lists and mutates roles with data scopes', async () => {
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
              roleId: 'role_dept_manager',
              roleCode: 'DEPT_MANAGER',
              roleName: '部门经理',
              roleCategory: 'SYSTEM',
              dataScopeSummary: '部门及子部门',
              menuCount: 2,
              status: 'ENABLED',
              createdAt: '2026-03-22T09:00:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(okResponse({ roleId: 'role_new' }))

    getMock
      .mockResolvedValueOnce(
        okResponse({
          roleId: 'role_dept_manager',
          roleCode: 'DEPT_MANAGER',
          roleName: '部门经理',
          roleCategory: 'SYSTEM',
          description: '负责部门数据管理',
          menuIds: ['menu_system_user'],
          dataScopes: [
            {
              scopeType: 'DEPARTMENT_AND_CHILDREN',
              scopeValue: 'dept_001',
            },
          ],
          enabled: true,
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          menus: [{ id: 'menu_system_user', name: '用户管理', menuType: 'MENU' }],
          scopeTypes: [
            { code: 'SELF', name: '仅本人' },
            { code: 'DEPARTMENT', name: '指定部门' },
          ],
          companies: [{ id: 'cmp_001', name: '西流科技' }],
          departments: [{ id: 'dept_001', name: '财务部', companyId: 'cmp_001' }],
          users: [{ id: 'usr_001', name: '张三', departmentId: 'dept_001' }],
        })
      )

    putMock.mockResolvedValueOnce(okResponse({ roleId: 'role_new' }))

    const {
      listRoles,
      getRoleDetail,
      getRoleFormOptions,
      createRole,
      updateRole,
    } = await import('./system-roles')

    await expect(
      listRoles({
        page: 1,
        pageSize: 20,
        keyword: '部门',
        filters: [],
        sorts: [{ field: 'roleName', direction: 'asc' }],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ roleCode: 'DEPT_MANAGER' }],
    })

    await expect(getRoleDetail('role_dept_manager')).resolves.toMatchObject({
      roleName: '部门经理',
      dataScopes: [{ scopeType: 'DEPARTMENT_AND_CHILDREN' }],
    })

    await expect(getRoleFormOptions()).resolves.toMatchObject({
      menus: [{ id: 'menu_system_user' }],
      scopeTypes: expect.arrayContaining([{ code: 'SELF', name: '仅本人' }]),
      companies: [{ id: 'cmp_001' }],
    })

    await expect(
      createRole({
        roleName: '财务管理员',
        roleCode: 'FINANCE_ADMIN',
        roleCategory: 'BUSINESS',
        description: '财务域角色',
        menuIds: ['menu_system_user'],
        dataScopes: [{ scopeType: 'DEPARTMENT', scopeValue: 'dept_001' }],
        enabled: true,
      })
    ).resolves.toEqual({ roleId: 'role_new' })

    await expect(
      updateRole('role_new', {
        roleName: '财务管理员',
        roleCode: 'FINANCE_ADMIN',
        roleCategory: 'BUSINESS',
        description: '财务域角色',
        menuIds: ['menu_system_user', 'menu_system_menu'],
        dataScopes: [{ scopeType: 'COMPANY', scopeValue: 'cmp_001' }],
        enabled: false,
      })
    ).resolves.toEqual({ roleId: 'role_new' })
  })
})
