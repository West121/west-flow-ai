import { type ColumnDef } from '@tanstack/react-table'
import { Badge } from '@/components/ui/badge'
import { getRouteApi } from '@tanstack/react-router'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'

type BasicRow = {
  id: string
  name: string
  owner: string
  scope: string
  status: string
}

const rolesRoute = getRouteApi('/_authenticated/system/roles/list')
const departmentsRoute = getRouteApi('/_authenticated/system/departments/list')
const postsRoute = getRouteApi('/_authenticated/system/posts/list')

const basicColumns: ColumnDef<BasicRow>[] = [
  {
    accessorKey: 'name',
    header: '名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.name}</span>
        <span className='text-xs text-muted-foreground'>{row.original.id}</span>
      </div>
    ),
  },
  {
    accessorKey: 'owner',
    header: '负责人',
  },
  {
    accessorKey: 'scope',
    header: '关联范围',
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => <Badge variant='secondary'>{row.original.status}</Badge>,
  },
]

export function RolesListPage() {
  const search = rolesRoute.useSearch()
  const navigate = rolesRoute.useNavigate()

  return (
    <ResourceListPage
      title='角色列表'
      description='角色列表页独立展示角色、数据范围和授权边界，不与用户管理混排在同一页签中。'
      endpoint='/api/v1/system/roles/page'
      searchPlaceholder='搜索角色名称或权限范围'
      search={search}
      navigate={navigate}
      columns={basicColumns}
      data={[
        {
          id: 'role_oa_user',
          name: 'OA 普通用户',
          owner: '组织管理员',
          scope: '流程发起、待办处理',
          status: '启用',
        },
        {
          id: 'role_process_admin',
          name: '流程管理员',
          owner: '平台管理员',
          scope: '流程定义、发布、版本管理',
          status: '启用',
        },
      ]}
      summaries={[
        { label: '角色总数', value: '18', hint: '后续与按钮权限矩阵联动。' },
        { label: '系统角色', value: '6', hint: '默认角色由平台预置维护。' },
        { label: '自定义角色', value: '12', hint: '业务管理员可扩展配置。' },
      ]}
    />
  )
}

export function DepartmentsListPage() {
  const search = departmentsRoute.useSearch()
  const navigate = departmentsRoute.useNavigate()

  return (
    <ResourceListPage
      title='部门列表'
      description='部门列表页将承载树形组织关系、负责人和数据权限范围。M0 先提供独立列表骨架。'
      endpoint='/api/v1/system/departments/page'
      searchPlaceholder='搜索部门名称、负责人或上级部门'
      search={search}
      navigate={navigate}
      columns={basicColumns}
      data={[
        {
          id: 'dept_finance',
          name: '财务部',
          owner: '刘经理',
          scope: '财务审批、报销复核',
          status: '启用',
        },
        {
          id: 'dept_hr',
          name: '人力资源部',
          owner: '陈经理',
          scope: '考勤、请假、人事档案',
          status: '启用',
        },
      ]}
      summaries={[
        { label: '部门总数', value: '23', hint: '后续支持部门树与子部门统计。' },
        { label: '一级部门', value: '7', hint: '集团级组织边界保持清晰。' },
        { label: '数据范围', value: '5', hint: '对齐 ALL/SELF/DEPARTMENT 等协议。' },
      ]}
    />
  )
}

export function PostsListPage() {
  const search = postsRoute.useSearch()
  const navigate = postsRoute.useNavigate()

  return (
    <ResourceListPage
      title='岗位列表'
      description='岗位列表页用于维护当前激活岗位、兼任岗位和审批链岗位职责。'
      endpoint='/api/v1/system/posts/page'
      searchPlaceholder='搜索岗位名称、所在部门或职责'
      search={search}
      navigate={navigate}
      columns={basicColumns}
      data={[
        {
          id: 'post_expense_approver',
          name: '报销审核岗',
          owner: '张三',
          scope: '报销单审批、补件确认',
          status: '启用',
        },
        {
          id: 'post_leave_reviewer',
          name: '请假复核岗',
          owner: '李四',
          scope: '请假审批、人事复核',
          status: '启用',
        },
      ]}
      summaries={[
        { label: '岗位总数', value: '41', hint: '后续支持岗位和流程节点绑定。' },
        { label: '兼任岗位', value: '14', hint: '切换上下文时回刷 current-user。' },
        { label: '审批岗位', value: '19', hint: '流程运行时默认按岗位查询待办。' },
      ]}
    />
  )
}
