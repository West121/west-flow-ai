import {
  ArrowRightLeft,
  Building2,
  CheckCircle2,
  CircleUserRound,
  Command,
  FileText,
  FolderKanban,
  ListTodo,
  Mail,
  Network,
  NotebookText,
  ShieldCheck,
  ReceiptText,
  SquareMenu,
  Search,
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
      title: 'OA',
      items: [
        {
          title: '请假申请',
          url: '/oa/leave/create',
          icon: FileText,
        },
        {
          title: '报销申请',
          url: '/oa/expense/create',
          icon: ReceiptText,
        },
        {
          title: '通用申请',
          url: '/oa/common/create',
          icon: NotebookText,
        },
        {
          title: 'OA 流程查询',
          url: '/oa/query',
          icon: Search,
        },
      ],
    },
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
          title: '代理关系管理',
          icon: Users,
          items: [
            {
              title: '代理关系列表',
              url: '/system/agents/list',
            },
            {
              title: '新建代理关系',
              url: '/system/agents/create',
            },
          ],
        },
        {
          title: '离职转办执行',
          icon: ArrowRightLeft,
          items: [
            {
              title: '执行离职转办',
              url: '/system/handover/execute',
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
          icon: ShieldCheck,
          items: [
            {
              title: '角色列表',
              url: '/system/roles/list',
            },
            {
              title: '新建角色',
              url: '/system/roles/create',
            },
          ],
        },
        {
          title: '菜单管理',
          icon: SquareMenu,
          items: [
            {
              title: '菜单列表',
              url: '/system/menus/list',
            },
            {
              title: '新建菜单',
              url: '/system/menus/create',
            },
          ],
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
        {
          title: '代理关系管理',
          icon: Users,
          requiredRoles: ['PROCESS_ADMIN'],
          items: [
            {
              title: '代理关系列表',
              url: '/system/agents/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '新建代理关系',
              url: '/system/agents/create',
              requiredRoles: ['PROCESS_ADMIN'],
            },
          ],
        },
        {
          title: '离职转办执行',
          url: '/system/handover/execute',
          icon: ArrowRightLeft,
          requiredRoles: ['PROCESS_ADMIN'],
        },
      ],
    },
    {
      title: '流程管理',
      items: [
        {
          title: '流程中心',
          icon: ListTodo,
          items: [
            {
              title: '待办列表',
              url: '/workbench/todos/list',
            },
            {
              title: '已办列表',
              url: '/workbench/done/list',
              icon: CheckCircle2,
            },
            {
              title: '我发起',
              url: '/workbench/initiated/list',
              icon: FileText,
            },
            {
              title: '抄送我',
              url: '/workbench/copied/list',
              icon: Mail,
            },
            {
              title: '发起流程',
              url: '/workbench/start',
            },
          ],
        },
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
