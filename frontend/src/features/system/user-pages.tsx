import { type ColumnDef } from '@tanstack/react-table'
import { getRouteApi, Link } from '@tanstack/react-router'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ResourceDetailPage } from '@/features/shared/crud/resource-detail-page'
import { ResourceFormPage } from '@/features/shared/crud/resource-form-page'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'

const usersListRoute = getRouteApi('/_authenticated/system/users/list')

type UserRow = {
  id: string
  name: string
  username: string
  department: string
  post: string
  status: '启用' | '停用'
}

const userRows: UserRow[] = [
  {
    id: 'usr_001',
    name: '张三',
    username: 'zhangsan',
    department: '财务部',
    post: '报销审核岗',
    status: '启用',
  },
  {
    id: 'usr_002',
    name: '李四',
    username: 'lisi',
    department: '人力资源部',
    post: '请假复核岗',
    status: '启用',
  },
  {
    id: 'usr_003',
    name: '王五',
    username: 'wangwu',
    department: '信息管理部',
    post: '流程管理员',
    status: '停用',
  },
]

const userColumns: ColumnDef<UserRow>[] = [
  {
    accessorKey: 'name',
    header: '用户姓名',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.name}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.username}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'department',
    header: '所属部门',
  },
  {
    accessorKey: 'post',
    header: '当前岗位',
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={row.original.status === '启用' ? 'secondary' : 'outline'}>
        {row.original.status}
      </Badge>
    ),
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link to='/system/users/$userId' params={{ userId: row.original.id }}>
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/users/$userId/edit'
            params={{ userId: row.original.id }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

const userFormSections = [
  {
    title: '基础信息',
    description: '当前为表单骨架，后续接入字段校验、组织选择器和 AI 智能填报入口。',
    fields: [
      { label: '用户姓名', value: '请输入真实姓名', hint: '支持唯一性校验。' },
      { label: '登录账号', value: '请输入英文账号', hint: '与统一认证账号绑定。' },
      { label: '手机号', value: '请输入手机号', hint: '用于通知与找回身份。' },
      { label: '邮箱', value: '请输入邮箱地址', hint: '后续支持邮件通知。' },
    ],
  },
  {
    title: '组织身份',
    description: '岗位、部门、角色与数据范围统一在这里维护，切换上下文后联动当前用户模型。',
    fields: [
      { label: '所属公司', value: '选择公司', hint: '对齐 current-user.companyId。' },
      { label: '主部门', value: '选择主部门', hint: '对齐 activeDepartmentId。' },
      { label: '主岗位', value: '选择主岗位', hint: '对齐 activePostId。' },
      { label: '角色集合', value: '选择角色', hint: '支持多个角色并联。' },
    ],
  },
]

export function UsersListPage() {
  const search = usersListRoute.useSearch()
  const navigate = usersListRoute.useNavigate()

  return (
    <ResourceListPage
      title='系统用户列表'
      description='用户列表页按 M0 查询协议保留关键词、排序和分页态。详情、创建、编辑均拆分为独立页面。'
      endpoint='/api/v1/system/users/page'
      searchPlaceholder='搜索姓名、账号、部门或岗位'
      search={search}
      navigate={navigate}
      columns={userColumns}
      data={userRows}
      createAction={{ label: '新建系统用户', href: '/system/users/create' }}
      summaries={[
        {
          label: '在岗用户',
          value: '86',
          hint: '后续由组织架构接口返回真实在岗人数。',
        },
        {
          label: '兼任岗位',
          value: '14',
          hint: '对应 auth.currentUser.partTimePosts 能力模型。',
        },
        {
          label: '代理关系',
          value: '5',
          hint: '与流程委派、代理链路保持一致。',
        },
      ]}
    />
  )
}

export function UserCreatePage() {
  return (
    <ResourceFormPage
      title='新建系统用户'
      description='创建页作为独立表单页面存在，不借助弹窗或抽屉承载复杂录入。'
      listHref='/system/users/list'
      sections={userFormSections}
    />
  )
}

export function UserEditPage({ userId }: { userId: string }) {
  return (
    <ResourceFormPage
      title='编辑系统用户'
      description={`正在编辑用户 ${userId} 的组织身份、权限与联系信息。保存后将回写统一 current-user 上下文模型。`}
      listHref='/system/users/list'
      sections={userFormSections}
    />
  )
}

export function UserDetailPage({ userId }: { userId: string }) {
  return (
    <ResourceDetailPage
      title='系统用户详情'
      description={`用户 ${userId} 的详情页独立承载组织身份、权限集合和 AI 能力概览，返回列表时保留原查询态。`}
      editHref={`/system/users/${userId}/edit`}
      listHref='/system/users/list'
      statusBadges={['启用中', '财务部', '报销审核岗']}
      sections={[
        {
          title: '身份信息',
          description: '对齐 auth/current-user 契约的基础字段。',
          items: [
            { label: '用户 ID', value: userId },
            { label: '用户名', value: 'zhangsan' },
            { label: '显示名称', value: '张三' },
            { label: '手机号', value: '13800000000' },
          ],
        },
        {
          title: '组织与权限',
          description: '围绕岗位、角色、数据范围和 AI 能力聚合展示。',
          items: [
            { label: '主部门', value: '财务部' },
            { label: '主岗位', value: '报销审核岗' },
            { label: '角色集合', value: 'OA_USER, DEPT_MANAGER' },
            { label: 'AI 能力', value: 'ai:copilot:open, ai:task:handle' },
          ],
        },
      ]}
    />
  )
}
