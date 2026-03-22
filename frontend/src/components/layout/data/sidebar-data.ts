import {
  Building2,
  CircleUserRound,
  Command,
  FolderKanban,
  ListTodo,
  Network,
  ShieldCheck,
  Users,
} from 'lucide-react'
import { type SidebarData } from '../types'

export const sidebarData: SidebarData = {
  user: {
    name: '平台用户',
    email: '登录后自动显示账号信息',
    avatar: '',
  },
  teams: [
    {
      name: '西流智能流程平台',
      logo: Command,
      plan: '流程平台',
    },
  ],
  navGroups: [
    {
      title: '工作台',
      items: [
        {
          title: '平台总览',
          url: '/',
          icon: FolderKanban,
        },
        {
          title: '待办列表',
          url: '/workbench/todos/list',
          icon: ListTodo,
        },
      ],
    },
    {
      title: '系统管理',
      items: [
        {
          title: '用户管理',
          icon: Users,
          items: [
            {
              title: '用户列表',
              url: '/system/users/list',
            },
            {
              title: '新建用户',
              url: '/system/users/create',
            },
          ],
        },
        {
          title: '公司管理',
          icon: Building2,
          items: [
            {
              title: '公司列表',
              url: '/system/companies/list',
            },
            {
              title: '新建公司',
              url: '/system/companies/create',
            },
          ],
        },
        {
          title: '角色管理',
          url: '/system/roles/list',
          icon: ShieldCheck,
        },
        {
          title: '部门管理',
          icon: Building2,
          items: [
            {
              title: '部门列表',
              url: '/system/departments/list',
            },
            {
              title: '新建部门',
              url: '/system/departments/create',
            },
          ],
        },
        {
          title: '岗位管理',
          icon: CircleUserRound,
          items: [
            {
              title: '岗位列表',
              url: '/system/posts/list',
            },
            {
              title: '新建岗位',
              url: '/system/posts/create',
            },
          ],
        },
      ],
    },
    {
      title: '流程平台',
      items: [
        {
          title: '流程定义',
          url: '/workflow/definitions/list',
          icon: Network,
        },
        {
          title: '流程设计器',
          icon: FolderKanban,
          items: [
            {
              title: '设计工作区',
              url: '/workflow/designer',
            },
          ],
        },
      ],
    },
  ],
}
