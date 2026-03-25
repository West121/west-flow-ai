import { describe, expect, it } from 'vitest'
import { buildNavGroups, flattenSidebarMenuItems } from './sidebar-menu-helpers'

describe('sidebar menu helpers', () => {
  const tree = [
    {
      menuId: 'menu_workflow_center',
      parentMenuId: null,
      title: '流程中心',
      menuType: 'DIRECTORY' as const,
      routePath: '/workbench',
      iconName: 'CheckCircle2',
      sortOrder: 40,
      children: [
        {
          menuId: 'menu_workbench_todo',
          parentMenuId: 'menu_workflow_center',
          title: '待办列表',
          menuType: 'MENU' as const,
          routePath: '/workbench/todos/list',
          iconName: 'ListTodo',
          sortOrder: 10,
          children: [],
        },
        {
          menuId: 'menu_workflow_start_group',
          parentMenuId: 'menu_workflow_center',
          title: '发起流程',
          menuType: 'DIRECTORY' as const,
          routePath: null,
          iconName: 'FileText',
          sortOrder: 20,
          children: [
            {
              menuId: 'menu_workflow_center_start',
              parentMenuId: 'menu_workflow_start_group',
              title: '业务发起',
              menuType: 'MENU' as const,
              routePath: '/workbench/start',
              iconName: 'FileText',
              sortOrder: 10,
              children: [],
            },
          ],
        },
      ],
    },
  ]

  it('builds nav groups from queried menu tree', () => {
    expect(buildNavGroups(tree)).toMatchObject([
      {
        items: [
          {
            title: '流程中心',
            items: [
              {
                title: '待办列表',
                url: '/workbench/todos/list',
              },
              {
                title: '发起流程',
                items: [
                  {
                    title: '业务发起',
                    url: '/workbench/start',
                  },
                ],
              },
            ],
          },
        ],
      },
    ])
  })

  it('keeps workbench and organization branches in the queried tree', () => {
    const actual = buildNavGroups([
      {
        menuId: 'menu_workbench',
        parentMenuId: null,
        title: '工作台',
        menuType: 'DIRECTORY' as const,
        routePath: '/workbench',
        iconName: 'FolderKanban',
        sortOrder: 10,
        children: [
          {
            menuId: 'menu_workbench_dashboard',
            parentMenuId: 'menu_workbench',
            title: '工作台',
            menuType: 'MENU' as const,
            routePath: '/',
            iconName: 'FolderKanban',
            sortOrder: 10,
            children: [],
          },
        ],
      },
      {
        menuId: 'menu_org',
        parentMenuId: null,
        title: '组织管理',
        menuType: 'DIRECTORY' as const,
        routePath: '/org',
        iconName: 'Users',
        sortOrder: 20,
        children: [
          {
            menuId: 'menu_system_role',
            parentMenuId: 'menu_org',
            title: '角色管理',
            menuType: 'MENU' as const,
            routePath: '/system/roles/list',
            iconName: 'ShieldCheck',
            sortOrder: 30,
            children: [],
          },
        ],
      },
    ])

    expect(actual).toMatchObject([
      {
        items: [
          {
            title: '工作台',
            items: [
              {
                title: '工作台',
                url: '/',
              },
            ],
          },
          {
            title: '组织管理',
            items: [
              {
                title: '角色管理',
                url: '/system/roles/list',
              },
            ],
          },
        ],
      },
    ])
  })

  it('flattens menu tree for command menu search', () => {
    expect(flattenSidebarMenuItems(tree)).toEqual([
      {
        groupTitle: '流程中心',
        title: '流程中心',
        url: '/workbench',
      },
      {
        groupTitle: '流程中心',
        title: '流程中心 / 待办列表',
        url: '/workbench/todos/list',
      },
      {
        groupTitle: '流程中心',
        title: '流程中心 / 发起流程 / 业务发起',
        url: '/workbench/start',
      },
    ])
  })
})
