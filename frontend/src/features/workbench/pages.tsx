import { getRouteApi, Link } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { CheckCircle2, Clock3, FolderGit2, Sparkles } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'

const workbenchRoute = getRouteApi('/_authenticated/workbench/todos/list')

type TodoRow = {
  id: string
  title: string
  process: string
  applicant: string
  currentNode: string
  dueAt: string
  priority: '高优先级' | '标准'
}

const todoColumns: ColumnDef<TodoRow>[] = [
  {
    accessorKey: 'title',
    header: '待办标题',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.title}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.process}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'applicant',
    header: '发起人',
  },
  {
    accessorKey: 'currentNode',
    header: '当前节点',
  },
  {
    accessorKey: 'dueAt',
    header: '要求完成',
  },
  {
    accessorKey: 'priority',
    header: '优先级',
    cell: ({ row }) => (
      <Badge variant={row.original.priority === '高优先级' ? 'destructive' : 'secondary'}>
        {row.original.priority}
      </Badge>
    ),
  },
]

const todoRows: TodoRow[] = [
  {
    id: 'todo_001',
    title: '报销单审批',
    process: '差旅报销流程 V1',
    applicant: '张三',
    currentNode: '部门负责人审批',
    dueAt: '今天 18:00',
    priority: '高优先级',
  },
  {
    id: 'todo_002',
    title: '请假申请审批',
    process: '请假流程 V2',
    applicant: '李四',
    currentNode: '人事复核',
    dueAt: '明天 12:00',
    priority: '标准',
  },
  {
    id: 'todo_003',
    title: '通用申请确认',
    process: '通用审批流程 V1',
    applicant: '王五',
    currentNode: '流程管理员确认',
    dueAt: '03-24 10:30',
    priority: '标准',
  },
]

export function Dashboard() {
  return (
    <PageShell
      title='平台总览'
      description='M0 前端基线已接管 shadcn-admin，并替换为 AIBPMN 平台的中文导航、独立 CRUD 页面骨架和流程工作区入口。'
      actions={
        <>
          <Button asChild>
            <Link to='/workbench/todos/list'>进入待办列表</Link>
          </Button>
          <Button asChild variant='outline'>
            <Link to='/workflow/designer'>打开流程设计器</Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 lg:grid-cols-4'>
        {[
          {
            title: '今日待办',
            value: '28',
            description: '按岗位上下文聚合，后续接入真实待办接口。',
            icon: Clock3,
          },
          {
            title: '流程定义',
            value: '12',
            description: '覆盖 OA 请假、报销、通用申请等首批流程。',
            icon: FolderGit2,
          },
          {
            title: '已完成审批',
            value: '216',
            description: '展示当前月审批闭环数量，后续接入统计分析。',
            icon: CheckCircle2,
          },
          {
            title: 'AI 入口',
            value: '1',
            description: '统一保留一个 Copilot 能力入口，不拆分次级导航。',
            icon: Sparkles,
          },
        ].map((item) => (
          <Card key={item.title}>
            <CardHeader className='gap-3'>
              <div className='flex items-center justify-between gap-3'>
                <CardDescription>{item.title}</CardDescription>
                <item.icon className='text-muted-foreground' />
              </div>
              <CardTitle className='text-3xl'>{item.value}</CardTitle>
            </CardHeader>
            <CardContent className='text-sm text-muted-foreground'>
              {item.description}
            </CardContent>
          </Card>
        ))}
      </div>

      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]'>
        <Card>
          <CardHeader>
            <CardTitle>首波页面骨架</CardTitle>
            <CardDescription>
              当前已拆分为独立路由，后续会在这些页面上逐步接入真实接口和权限校验。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            {[
              ['工作台待办列表', '/workbench/todos/list'],
              ['系统用户列表', '/system/users/list'],
              ['新建系统用户', '/system/users/create'],
              ['流程定义列表', '/workflow/definitions/list'],
              ['流程设计器', '/workflow/designer'],
            ].map(([label, href]) => (
              <Button key={href} asChild variant='outline' className='justify-start'>
                <Link to={href}>{label}</Link>
              </Button>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>M0 约束摘要</CardTitle>
            <CardDescription>所有可见菜单与占位页文案均使用中文，不引入 i18n 抽象。</CardDescription>
          </CardHeader>
          <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
            <p>1. CRUD 页面一律独立路由，不使用页签或抽屉代替标准页面。</p>
            <p>2. 主列表页默认保留分页、关键词、分组、排序和列显隐能力。</p>
            <p>3. 流程设计器独立成工作区页面，不嵌入普通列表页。</p>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

export function WorkbenchTodoListPage() {
  const search = workbenchRoute.useSearch()
  const navigate = workbenchRoute.useNavigate()

  return (
    <ResourceListPage
      title='工作台待办列表'
      description='聚合当前岗位上下文下的审批待办。返回列表时保留查询态，后续直接映射到统一分页协议。'
      endpoint='/api/v1/workbench/todos/page'
      searchPlaceholder='搜索待办标题、发起人或流程名称'
      search={search}
      navigate={navigate}
      columns={todoColumns}
      data={todoRows}
      summaries={[
        {
          label: '待办总量',
          value: '28',
          hint: '按当前激活岗位与数据权限汇总。',
        },
        {
          label: '今日到期',
          value: '6',
          hint: '需要优先处理的审批任务会在这里提醒。',
        },
        {
          label: '超时预警',
          value: '2',
          hint: '后续将联动催办与超时通知能力。',
        },
      ]}
    />
  )
}
