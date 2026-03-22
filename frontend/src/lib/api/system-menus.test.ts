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

describe('system menu api', () => {
  beforeEach(() => {
    vi.resetModules()
    getMock.mockReset()
    postMock.mockReset()
    putMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('lists and mutates menus', async () => {
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
              menuId: 'menu_system_menu',
              parentMenuName: '系统管理',
              menuName: '菜单管理',
              menuType: 'MENU',
              routePath: '/system/menus/list',
              permissionCode: 'system:menu:view',
              sortOrder: 60,
              visible: true,
              status: 'ENABLED',
              createdAt: '2026-03-22T09:00:00+08:00',
            },
          ],
        })
      )
      .mockResolvedValueOnce(okResponse({ menuId: 'menu_new' }))

    getMock
      .mockResolvedValueOnce(
        okResponse({
          menuId: 'menu_system_menu',
          parentMenuId: 'menu_system',
          parentMenuName: '系统管理',
          menuName: '菜单管理',
          menuType: 'MENU',
          routePath: '/system/menus/list',
          componentPath: 'system/menus/list',
          permissionCode: 'system:menu:view',
          iconName: 'SquareMenu',
          sortOrder: 60,
          visible: true,
          enabled: true,
        })
      )
      .mockResolvedValueOnce(
        okResponse({
          menuTypes: [
            { code: 'DIRECTORY', name: '目录' },
            { code: 'MENU', name: '菜单' },
            { code: 'BUTTON', name: '按钮' },
          ],
          parentMenus: [
            {
              id: 'menu_system',
              name: '系统管理',
              menuType: 'DIRECTORY',
              enabled: true,
            },
          ],
        })
      )

    putMock.mockResolvedValueOnce(okResponse({ menuId: 'menu_new' }))

    const {
      listMenus,
      getMenuDetail,
      getMenuFormOptions,
      createMenu,
      updateMenu,
    } = await import('./system-menus')

    await expect(
      listMenus({
        page: 1,
        pageSize: 20,
        keyword: '菜单',
        filters: [],
        sorts: [{ field: 'sortOrder', direction: 'asc' }],
        groups: [],
      })
    ).resolves.toMatchObject({
      total: 1,
      records: [{ menuId: 'menu_system_menu' }],
    })

    await expect(getMenuDetail('menu_system_menu')).resolves.toMatchObject({
      menuName: '菜单管理',
    })

    await expect(getMenuFormOptions()).resolves.toMatchObject({
      menuTypes: expect.arrayContaining([{ code: 'DIRECTORY', name: '目录' }]),
      parentMenus: expect.arrayContaining([
        {
          id: 'menu_system',
          name: '系统管理',
          menuType: 'DIRECTORY',
          enabled: true,
        },
      ]),
    })

    await expect(
      createMenu({
        parentMenuId: 'menu_system',
        menuName: '日志管理',
        menuType: 'MENU',
        routePath: '/system/logs/list',
        componentPath: 'system/logs/list',
        permissionCode: 'system:log:view',
        iconName: 'ScrollText',
        sortOrder: 70,
        visible: true,
        enabled: true,
      })
    ).resolves.toEqual({ menuId: 'menu_new' })

    await expect(
      updateMenu('menu_new', {
        parentMenuId: 'menu_workflow',
        menuName: '日志管理',
        menuType: 'MENU',
        routePath: '/system/logs/list',
        componentPath: 'system/logs/list',
        permissionCode: 'system:log:view',
        iconName: 'ScrollText',
        sortOrder: 80,
        visible: false,
        enabled: false,
      })
    ).resolves.toEqual({ menuId: 'menu_new' })
  })
})
