import {
  ArrowRightLeft,
  BookText,
  Building2,
  CheckCircle2,
  CircleUserRound,
  Command,
  FileText,
  FolderKanban,
  Gauge,
  ListTodo,
  Mail,
  Network,
  NotebookText,
  ScrollText,
  ShieldCheck,
  ReceiptText,
  SquareMenu,
  Search,
  Users,
  Zap,
} from 'lucide-react'
import { type SidebarData } from '../types'

// 侧边栏静态导航数据，供布局和权限过滤逻辑直接消费。
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
          title: '组织管理',
          icon: Building2,
          items: [
            {
              title: '用户列表',
              url: '/system/users/list',
              icon: Users,
            },
            {
              title: '新建用户',
              url: '/system/users/create',
              icon: Users,
            },
            {
              title: '公司列表',
              url: '/system/companies/list',
              icon: Building2,
            },
            {
              title: '新建公司',
              url: '/system/companies/create',
              icon: Building2,
            },
            {
              title: '部门列表',
              url: '/system/departments/list',
              icon: Building2,
            },
            {
              title: '新建部门',
              url: '/system/departments/create',
              icon: Building2,
            },
          ],
        },
        {
          title: '权限管理',
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
            {
              title: '菜单列表',
              url: '/system/menus/list',
              icon: SquareMenu,
            },
            {
              title: '新建菜单',
              url: '/system/menus/create',
              icon: SquareMenu,
            },
          ],
        },
        {
          title: '字典管理',
          icon: BookText,
          requiredRoles: ['PROCESS_ADMIN'],
          items: [
            {
              title: '字典类型列表',
              url: '/system/dict-types/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '新建字典类型',
              url: '/system/dict-types/create',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '字典项列表',
              url: '/system/dict-items/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '新建字典项',
              url: '/system/dict-items/create',
              requiredRoles: ['PROCESS_ADMIN'],
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
          title: '日志管理',
          icon: ScrollText,
          requiredRoles: ['PROCESS_ADMIN'],
          items: [
            {
              title: '审计日志',
              url: '/system/logs/audit/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '登录日志',
              url: '/system/logs/login/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '通知日志',
              url: '/system/logs/notifications/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
          ],
        },
        {
          title: '监控管理',
          icon: Gauge,
          requiredRoles: ['PROCESS_ADMIN'],
          items: [
            {
              title: '编排扫描记录',
              url: '/system/monitor/orchestrator-scans/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '触发执行记录',
              url: '/system/monitor/trigger-executions/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '通知渠道健康',
              url: '/system/monitor/notification-channels/health/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
          ],
        },
        {
          title: '文件管理',
          icon: FolderKanban,
          requiredRoles: ['PROCESS_ADMIN'],
          items: [
            {
              title: '文件列表',
              url: '/system/files/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '上传文件',
              url: '/system/files/create',
              requiredRoles: ['PROCESS_ADMIN'],
            },
          ],
        },
        {
          title: '代理与转办',
          icon: ArrowRightLeft,
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
            {
              title: '离职转办执行',
              url: '/system/handover/execute',
              requiredRoles: ['PROCESS_ADMIN'],
            },
          ],
        },
        {
          title: '通知管理',
          icon: Mail,
          requiredRoles: ['PROCESS_ADMIN'],
          items: [
            {
              title: '消息列表',
              url: '/system/messages/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '新建消息',
              url: '/system/messages/create',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '通知模板列表',
              url: '/system/notifications/templates/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '新建通知模板',
              url: '/system/notifications/templates/create',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '通知记录列表',
              url: '/system/notifications/records/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '通知渠道列表',
              url: '/system/notification-channels/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '新建通知渠道',
              url: '/system/notification-channels/create',
              requiredRoles: ['PROCESS_ADMIN'],
            },
          ],
        },
        {
          title: '调度配置',
          icon: Zap,
          requiredRoles: ['PROCESS_ADMIN'],
          items: [
            {
              title: '触发器列表',
              url: '/system/triggers/list',
              requiredRoles: ['PROCESS_ADMIN'],
            },
            {
              title: '新建触发器',
              url: '/system/triggers/create',
              requiredRoles: ['PROCESS_ADMIN'],
            },
          ],
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
