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
    ])
  })

  it('flattens menu tree for command menu search', () => {
    expect(flattenSidebarMenuItems(tree)).toEqual([
      {
        groupTitle: '流程中心',
        title: '待办列表',
        url: '/workbench/todos/list',
      },
      {
        groupTitle: '流程中心',
        title: '发起流程 / 业务发起',
        url: '/workbench/start',
      },
    ])
  })
})
