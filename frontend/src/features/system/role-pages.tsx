import { startTransition, useEffect, useMemo, useState } from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useFieldArray, useForm, useWatch, type UseFormReturn } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  ChevronDown,
  ChevronRight,
  DatabaseZap,
  KeyRound,
  Loader2,
  Plus,
  Save,
  ShieldCheck,
  Trash2,
  Users,
} from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { normalizeListQuerySearch } from '@/features/shared/table/query-contract'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { getApiErrorResponse } from '@/lib/api/client'
import { handleServerError } from '@/lib/handle-server-error'
import {
  createRole,
  getRoleDetail,
  getRoleFormOptions,
  getRoleUsers,
  listRoles,
  updateRole,
  type DataScopeType,
  type SaveSystemRolePayload,
  type SystemRoleCategory,
  type SystemRoleDetail,
  type SystemRoleFormOptions,
  type SystemRoleRecord,
} from '@/lib/api/system-roles'
import { getMenuTree, type MenuTreeNode } from '@/lib/api/system-menus'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'
import { AssociatedUsersDialog } from './associated-users-dialog'

const rolesRoute = getRouteApi('/_authenticated/system/roles/list')

const roleFormSchema = z.object({
  roleName: z.string().trim().min(2, '角色名称至少需要 2 个字符'),
  roleCode: z
    .string()
    .trim()
    .min(2, '角色编码至少需要 2 个字符')
    .regex(/^[A-Z0-9_]+$/, '角色编码仅支持大写字母、数字和下划线'),
  roleCategory: z.enum(['SYSTEM', 'BUSINESS']),
  description: z.string().nullable(),
  menuIds: z.array(z.string()).min(1, '请至少选择一个菜单权限'),
  dataScopes: z.array(
    z.object({
      scopeType: z.enum([
        'ALL',
        'SELF',
        'DEPARTMENT',
        'DEPARTMENT_AND_CHILDREN',
        'COMPANY',
      ]),
      scopeValue: z.string().min(1, '请选择数据权限范围'),
    })
  ),
  enabled: z.boolean(),
})

type RoleFormValues = z.infer<typeof roleFormSchema>
type SubmitAction = 'list' | 'continue'

// 角色管理页统一用这个方法格式化时间展示。
function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

// 角色状态只保留启用/停用两种文案。
function resolveStatusLabel(status: SystemRoleRecord['status']) {
  return status === 'ENABLED' ? '启用' : '停用'
}

// 角色类别标签用于列表和详情保持一致。
function resolveRoleCategoryLabel(category: SystemRoleCategory) {
  return category === 'SYSTEM' ? '系统角色' : '业务角色'
}

// 数据权限范围使用统一的中文标签。
function resolveScopeTypeLabel(scopeType: DataScopeType) {
  return (
    {
      ALL: '全部数据',
      SELF: '仅本人',
      DEPARTMENT: '指定部门',
      DEPARTMENT_AND_CHILDREN: '部门及子部门',
      COMPANY: '指定公司',
    } satisfies Record<DataScopeType, string>
  )[scopeType]
}

function collectMenuTreeIds(nodes: MenuTreeNode[]): string[] {
  return nodes.flatMap((node) => [node.menuId, ...collectMenuTreeIds(node.children)])
}

function resolveMenuTreeCheckState(node: MenuTreeNode, selectedMenuIds: string[]) {
  const descendantIds = collectMenuTreeIds([node])
  const selectedCount = descendantIds.filter((id) => selectedMenuIds.includes(id)).length

  if (selectedCount === 0) {
    return false
  }
  if (selectedCount === descendantIds.length) {
    return true
  }
  return 'indeterminate' as const
}

function RoleMenuTreeSelector({
  nodes,
  selectedMenuIds,
  onChange,
}: {
  nodes: MenuTreeNode[]
  selectedMenuIds: string[]
  onChange: (value: string[]) => void
}) {
  const topLevelNodeIds = useMemo(() => new Set(nodes.map((node) => node.menuId)), [nodes])
  const [collapsedTopLevelNodeIds, setCollapsedTopLevelNodeIds] = useState<Set<string>>(
    () => new Set()
  )
  const [expandedChildNodeIds, setExpandedChildNodeIds] = useState<Set<string>>(() => new Set())

  function toggleExpanded(menuId: string, isTopLevel: boolean) {
    if (isTopLevel) {
      setCollapsedTopLevelNodeIds((current) => {
        const next = new Set(current)
        if (next.has(menuId)) {
          next.delete(menuId)
        } else {
          next.add(menuId)
        }
        return next
      })
      return
    }
    setExpandedChildNodeIds((current) => {
      const next = new Set(current)
      if (next.has(menuId)) {
        next.delete(menuId)
      } else {
        next.add(menuId)
      }
      return next
    })
  }

  function toggleNode(node: MenuTreeNode, checked: boolean) {
    const descendantIds = collectMenuTreeIds([node])
    const nextIds = checked
      ? Array.from(new Set([...selectedMenuIds, ...descendantIds]))
      : selectedMenuIds.filter((id) => !descendantIds.includes(id))
    onChange(nextIds)
  }

  function renderNode(node: MenuTreeNode, depth = 0): React.ReactNode {
    const hasChildren = node.children.length > 0
    const isTopLevel = topLevelNodeIds.has(node.menuId)
    const expanded = isTopLevel
      ? !collapsedTopLevelNodeIds.has(node.menuId)
      : expandedChildNodeIds.has(node.menuId)
    const checkedState = resolveMenuTreeCheckState(node, selectedMenuIds)

    return (
      <div key={node.menuId} className='space-y-2'>
        <div
          className='flex items-start gap-3 rounded-lg border p-3'
          style={{ marginLeft: depth * 16 }}
        >
          <div className='mt-0.5 flex items-center gap-1'>
            {hasChildren ? (
              <Button
                type='button'
                variant='ghost'
                size='icon'
                className='size-6'
                onClick={() => toggleExpanded(node.menuId, isTopLevel)}
              >
                {expanded ? <ChevronDown className='size-4' /> : <ChevronRight className='size-4' />}
              </Button>
            ) : (
              <span className='block size-6' />
            )}
            <Checkbox
              checked={checkedState}
              onCheckedChange={(value) => toggleNode(node, value === true)}
            />
          </div>
          <div className='min-w-0 space-y-1'>
            <div className='flex flex-wrap items-center gap-2'>
              <span className='text-sm font-medium'>{node.menuName}</span>
              <Badge variant='outline'>{node.menuType}</Badge>
            </div>
            <p className='text-xs text-muted-foreground'>
              {node.permissionCode
                ?? node.routePath
                ?? (node.parentMenuId ? '子级菜单' : '顶级菜单')}
            </p>
          </div>
        </div>
        {hasChildren && expanded ? (
          <div className='space-y-2'>
            {node.children.map((child) => renderNode(child, depth + 1))}
          </div>
        ) : null}
      </div>
    )
  }

  return <div className='space-y-2'>{nodes.map((node) => renderNode(node))}</div>
}

// 详情回填时把后端数据整理成表单默认值。
function toFormValues(detail?: SystemRoleDetail): RoleFormValues {
  return {
    roleName: detail?.roleName ?? '',
    roleCode: detail?.roleCode ?? '',
    roleCategory: detail?.roleCategory ?? 'BUSINESS',
    description: detail?.description ?? '',
    menuIds: detail?.menuIds ?? [],
    dataScopes:
      detail?.dataScopes.length
        ? detail.dataScopes
        : [{ scopeType: 'SELF', scopeValue: '*' }],
    enabled: detail?.enabled ?? true,
  }
}

// 后端字段错误直接回填到对应表单控件。
function applyRoleFieldErrors(
  form: UseFormReturn<RoleFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    switch (fieldError.field) {
      case 'roleName':
      case 'roleCode':
      case 'roleCategory':
      case 'description':
      case 'menuIds':
      case 'dataScopes':
      case 'enabled':
        form.setError(fieldError.field, {
          type: 'server',
          message: fieldError.message,
        })
        break
      default:
        break
    }
  })

  if (apiError?.code === 'BIZ.ROLE_CODE_DUPLICATED') {
    form.setError('roleCode', {
      type: 'server',
      message: apiError.message,
    })
  }

  return apiError
}

// 页面加载失败时统一展示错误态。
function PageErrorState({
  title,
  description,
  retry,
}: {
  title: string
  description: string
  retry?: () => void
}) {
  return (
    <PageShell title={title} description={description}>
      <Alert variant='destructive'>
        <AlertCircle />
        <AlertTitle>页面加载失败</AlertTitle>
        <AlertDescription>
          角色与数据权限数据请求未成功，请重试或返回列表页。
        </AlertDescription>
      </Alert>
      <div className='flex flex-wrap gap-2'>
        {retry ? <Button onClick={retry}>重新加载</Button> : null}
        <Button asChild variant='outline'>
          <Link to='/system/roles/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      </div>
    </PageShell>
  )
}

// 列表、创建和编辑页共用同一套骨架屏。
function PageLoadingState({
  title,
  description,
}: {
  title: string
  description: string
}) {
  return (
    <PageShell title={title} description={description}>
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <Skeleton className='h-6 w-40' />
            <Skeleton className='h-4 w-full max-w-xl' />
          </CardHeader>
          <CardContent className='grid gap-4 md:grid-cols-2'>
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className='flex flex-col gap-2'>
                <Skeleton className='h-4 w-20' />
                <Skeleton className='h-10 w-full' />
              </div>
            ))}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <Skeleton className='h-6 w-24' />
            <Skeleton className='h-4 w-full' />
          </CardHeader>
          <CardContent className='flex flex-col gap-3'>
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className='h-16 w-full' />
            ))}
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

function buildRoleColumns(
  onShowUsers: (row: SystemRoleRecord) => void
): ColumnDef<SystemRoleRecord>[] {
  return [
  {
    accessorKey: 'roleName',
    header: '角色名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.roleName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.roleCode}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'roleCategory',
    header: '角色分类',
    cell: ({ row }) => (
      <Badge variant='outline'>
        {resolveRoleCategoryLabel(row.original.roleCategory)}
      </Badge>
    ),
  },
  {
    accessorKey: 'dataScopeSummary',
    header: '数据权限',
  },
  {
    accessorKey: 'menuCount',
    header: '菜单数量',
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={row.original.status === 'ENABLED' ? 'secondary' : 'outline'}>
        {resolveStatusLabel(row.original.status)}
      </Badge>
    ),
  },
  {
    accessorKey: 'createdAt',
    header: '创建时间',
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button
          variant='ghost'
          className='h-8 px-2'
          onClick={() => onShowUsers(row.original)}
        >
          关联用户
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link to='/system/roles/$roleId' params={{ roleId: row.original.roleId }}>
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/roles/$roleId/edit'
            params={{ roleId: row.original.roleId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]
}

function ScopeValueSelect({
  options,
  scopeType,
  value,
  onChange,
}: {
  options: SystemRoleFormOptions
  scopeType: DataScopeType
  value: string
  onChange: (value: string) => void
}) {
  const items = useMemo(() => {
    switch (scopeType) {
      case 'ALL':
        return [{ id: '*', name: '全部数据' }]
      case 'SELF':
        return [{ id: '*', name: '当前持有该角色的用户本人' }]
      case 'COMPANY':
        return options.companies
      case 'DEPARTMENT':
      case 'DEPARTMENT_AND_CHILDREN':
        return options.departments.map((item) => ({
          id: item.id,
          name: `${item.companyName} / ${item.name}`,
        }))
      default:
        return []
    }
  }, [options, scopeType])

  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger>
        <SelectValue placeholder='请选择数据范围' />
      </SelectTrigger>
      <SelectContent>
        {items.map((item) => (
          <SelectItem key={item.id} value={item.id}>
            {item.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

export function RolesListPage() {
  const search = normalizeListQuerySearch(rolesRoute.useSearch())
  const navigate = rolesRoute.useNavigate()
  const [selectedRole, setSelectedRole] = useState<SystemRoleRecord | null>(null)
  const query = useQuery({
    queryKey: ['system-roles', search],
    queryFn: () => listRoles(search),
  })
  const usersQuery = useQuery({
    queryKey: ['system-role-users', selectedRole?.roleId],
    queryFn: () => getRoleUsers(selectedRole!.roleId),
    enabled: Boolean(selectedRole?.roleId),
  })

  const summaries = useMemo(() => {
    const records = query.data?.records ?? []
    const systemCount = records.filter(
      (item) => item.roleCategory === 'SYSTEM'
    ).length
    const enabledCount = records.filter(
      (item) => item.status === 'ENABLED'
    ).length
    const menuCount = records.reduce((sum, item) => sum + item.menuCount, 0)

    return [
      {
        label: '角色总数',
        value: String(query.data?.total ?? 0),
        hint: '角色统一承载菜单权限和数据权限配置，避免分散在用户页和业务页。'
      },
      {
        label: '当前页启用',
        value: String(enabledCount),
        hint: `系统角色 ${systemCount} 个，支持继续扩展业务管理员和 AI 专用角色。`,
      },
      {
        label: '菜单授权数',
        value: String(menuCount),
        hint: '菜单权限矩阵已经与角色绑定，后续可以继续补按钮级细粒度授权。'
      },
    ]
  }, [query.data])

  if (query.isLoading) {
    return (
      <PageShell
        title='角色管理'
        description='角色模块承载菜单权限和数据权限的统一配置。'
      >
        <Skeleton className='h-64 w-full' />
      </PageShell>
    )
  }

  if (query.isError || !query.data) {
    return (
      <PageErrorState
        title='角色管理'
    description='角色列表加载失败。'
        retry={() => void query.refetch()}
      />
    )
  }

  return (
    <>
      <ResourceListPage
        title='角色管理'
        description='角色列表页支持分页、模糊查询和排序，角色详情与编辑使用独立页面，不和组织模块混在同一个容器里。'
        endpoint='/system/roles/page'
        searchPlaceholder='搜索角色名称、角色编码或描述'
        search={search}
        navigate={navigate}
        columns={buildRoleColumns(setSelectedRole)}
        data={query.data.records}
        total={query.data.total}
        summaries={summaries}
        createAction={{
          label: '新建角色',
          href: '/system/roles/create',
        }}
      />
      <AssociatedUsersDialog
        open={Boolean(selectedRole)}
        title='角色关联用户'
        description={
          selectedRole
            ? `查看角色「${selectedRole.roleName}」当前关联的用户。`
            : '查看当前角色关联的用户。'
        }
        users={usersQuery.data}
        isLoading={usersQuery.isLoading}
        isError={usersQuery.isError}
        onRetry={() => void usersQuery.refetch()}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedRole(null)
          }
        }}
      />
    </>
  )
}

function toPayload(values: RoleFormValues): SaveSystemRolePayload {
  return {
    roleName: values.roleName.trim(),
    roleCode: values.roleCode.trim(),
    roleCategory: values.roleCategory,
    description: values.description?.trim() ? values.description.trim() : null,
    menuIds: values.menuIds,
    dataScopes: values.dataScopes.map((scope) => ({
      scopeType: scope.scopeType,
      scopeValue:
        scope.scopeType === 'ALL' || scope.scopeType === 'SELF'
          ? '*'
          : scope.scopeValue,
    })),
    enabled: values.enabled,
  }
}

function RoleFormPage({
  mode,
  roleId,
}: {
  mode: 'create' | 'edit'
  roleId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitAction, setSubmitAction] = useState<SubmitAction>('list')
  const isEdit = mode === 'edit'
  const form = useForm<RoleFormValues>({
    resolver: zodResolver(roleFormSchema),
    defaultValues: toFormValues(),
    mode: 'onBlur',
  })
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: 'dataScopes',
  })

  const optionsQuery = useQuery({
    queryKey: ['system-role-form-options'],
    queryFn: getRoleFormOptions,
  })
  const menuTreeQuery = useQuery({
    queryKey: ['system-menu-tree'],
    queryFn: getMenuTree,
  })

  const detailQuery = useQuery({
    queryKey: ['system-role', roleId],
    queryFn: () => getRoleDetail(roleId!),
    enabled: isEdit && Boolean(roleId),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset(toFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form])

  const createMutation = useMutation({
    mutationFn: createRole,
    onError: () => undefined,
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: SaveSystemRolePayload }) =>
      updateRole(id, payload),
    onError: () => undefined,
  })

  const selectedMenuIds = useWatch({
    control: form.control,
    name: 'menuIds',
  })
  const enabled = useWatch({
    control: form.control,
    name: 'enabled',
  })
  const roleCategory = useWatch({
    control: form.control,
    name: 'roleCategory',
  })
  const dataScopes = useWatch({
    control: form.control,
    name: 'dataScopes',
  })
  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const isInitialLoading =
    optionsQuery.isLoading || menuTreeQuery.isLoading || (isEdit && detailQuery.isLoading)

  async function onSubmit(values: RoleFormValues) {
    form.clearErrors()

    try {
      const payload = toPayload(values)
      const result = isEdit
        ? await updateMutation.mutateAsync({ id: roleId!, payload })
        : await createMutation.mutateAsync(payload)

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['system-roles'] }),
        queryClient.invalidateQueries({ queryKey: ['system-role', result.roleId] }),
      ])

      toast.success(isEdit ? '角色已更新' : '角色已创建')

      if (submitAction === 'continue') {
        startTransition(() => {
          navigate({
            to: '/system/roles/$roleId/edit',
            params: { roleId: result.roleId },
            replace: isEdit,
          })
        })
        return
      }

      startTransition(() => {
        navigate({
          to: '/system/roles/list',
        })
      })
    } catch (error) {
      const apiError = applyRoleFieldErrors(form, error)

      if (!apiError || (!apiError.fieldErrors?.length && apiError.code !== 'BIZ.ROLE_CODE_DUPLICATED')) {
        handleServerError(error)
      }
    }
  }

  if (isInitialLoading) {
    return (
      <PageLoadingState
        title={isEdit ? '编辑角色' : '新建角色'}
        description='正在加载角色详情、菜单权限和数据权限选项。'
      />
    )
  }

  if (
    optionsQuery.isError
    || menuTreeQuery.isError
    || (isEdit && detailQuery.isError)
    || !optionsQuery.data
    || !menuTreeQuery.data
  ) {
    return (
      <PageErrorState
        title={isEdit ? '编辑角色' : '新建角色'}
        description='角色表单依赖的数据未能加载成功。'
        retry={() => {
          void optionsQuery.refetch()
          void menuTreeQuery.refetch()
          if (isEdit) {
            void detailQuery.refetch()
          }
        }}
      />
    )
  }

  return (
    <PageShell
      title={isEdit ? '编辑角色' : '新建角色'}
      description={
        isEdit
          ? '编辑页统一维护角色基本信息、菜单授权和数据权限范围。'
          : '新建页统一配置角色与数据权限，不使用弹窗和抽屉代替正式页面。'
      }
      actions={
        <>
          <Button
            type='submit'
            form='system-role-form'
            disabled={isSubmitting}
            onClick={() => setSubmitAction('list')}
          >
            {isSubmitting ? (
              <Loader2 className='animate-spin' data-icon='inline-start' />
            ) : (
              <Save data-icon='inline-start' />
            )}
            保存并返回列表
          </Button>
          <Button
            type='submit'
            form='system-role-form'
            variant='outline'
            disabled={isSubmitting}
            onClick={() => setSubmitAction('continue')}
          >
            {isSubmitting ? (
              <Loader2 className='animate-spin' data-icon='inline-start' />
            ) : (
              <Save data-icon='inline-start' />
            )}
            保存并继续编辑
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/roles/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>角色表单</CardTitle>
            <CardDescription>
              角色承载菜单授权与数据权限范围，是后续审批、AI 助手和业务列表鉴权的统一入口。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form
                id='system-role-form'
                className='grid gap-6'
                onSubmit={form.handleSubmit(onSubmit)}
              >
                <div className='grid gap-4 md:grid-cols-2'>
                  <FormField
                    control={form.control}
                    name='roleName'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>角色名称</FormLabel>
                        <FormControl>
                          <Input placeholder='例如：财务管理员' {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name='roleCode'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>角色编码</FormLabel>
                        <FormControl>
                          <Input placeholder='FINANCE_ADMIN' {...field} />
                        </FormControl>
                        <FormDescription>
                          角色编码用于登录态、后端鉴权和后续用户角色分配。
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name='roleCategory'
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>角色分类</FormLabel>
                        <Select
                          value={field.value}
                          onValueChange={(value) =>
                            field.onChange(value as SystemRoleCategory)
                          }
                        >
                          <FormControl>
                            <SelectTrigger>
                              <SelectValue placeholder='请选择角色分类' />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            <SelectItem value='SYSTEM'>系统角色</SelectItem>
                            <SelectItem value='BUSINESS'>业务角色</SelectItem>
                          </SelectContent>
                        </Select>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name='enabled'
                    render={({ field }) => (
                      <FormItem className='flex items-center justify-between rounded-lg border p-4'>
                        <div className='space-y-1'>
                          <FormLabel>启用状态</FormLabel>
                          <FormDescription>
                            停用后角色仍保留配置，但不会继续参与权限聚合。
                          </FormDescription>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                </div>

                <FormField
                  control={form.control}
                  name='description'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>角色说明</FormLabel>
                      <FormControl>
                        <Textarea
                          rows={3}
                          placeholder='描述角色负责的业务范围、审批责任和数据范围。'
                          {...field}
                          value={field.value ?? ''}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='menuIds'
                  render={() => (
                    <FormItem>
                      <FormLabel>菜单权限</FormLabel>
                      <FormDescription>
                        角色和菜单绑定后，当前登录态会自动聚合权限标识与可见菜单。
                      </FormDescription>
                      <div className='rounded-lg border p-4'>
                        <RoleMenuTreeSelector
                          nodes={menuTreeQuery.data}
                          selectedMenuIds={selectedMenuIds}
                          onChange={(value) =>
                            form.setValue('menuIds', value, { shouldValidate: true })
                          }
                        />
                      </div>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className='grid gap-3'>
                  <div className='flex items-center justify-between'>
                    <div>
                      <FormLabel>数据权限</FormLabel>
                      <FormDescription>
                        数据权限范围会进入当前用户上下文，并用于后续列表过滤和 AI 能力范围控制。
                      </FormDescription>
                    </div>
                    <Button
                      type='button'
                      variant='outline'
                      onClick={() =>
                        append({ scopeType: 'SELF', scopeValue: '*' })
                      }
                    >
                      <Plus data-icon='inline-start' />
                      新增范围
                    </Button>
                  </div>
                  {fields.map((field, index) => {
                    const scopeType =
                      dataScopes?.[index]?.scopeType ?? 'SELF'

                    return (
                      <div
                        key={field.id}
                        className='grid gap-3 rounded-lg border p-4 md:grid-cols-[180px_minmax(0,1fr)_auto]'
                      >
                        <FormField
                          control={form.control}
                          name={`dataScopes.${index}.scopeType`}
                          render={({ field: scopeField }) => (
                            <FormItem>
                              <FormLabel>范围类型</FormLabel>
                              <Select
                                value={scopeField.value}
                                onValueChange={(value) => {
                                  scopeField.onChange(value as DataScopeType)
                                  form.setValue(
                                    `dataScopes.${index}.scopeValue`,
                                    value === 'ALL' || value === 'SELF' ? '*' : '',
                                    { shouldValidate: true }
                                  )
                                }}
                              >
                                <FormControl>
                                  <SelectTrigger>
                                    <SelectValue placeholder='请选择范围类型' />
                                  </SelectTrigger>
                                </FormControl>
                                <SelectContent>
                                  {optionsQuery.data.scopeTypes.map((item) => (
                                    <SelectItem key={item.code} value={item.code}>
                                      {item.name}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                              <FormMessage />
                            </FormItem>
                          )}
                        />

                        <FormField
                          control={form.control}
                          name={`dataScopes.${index}.scopeValue`}
                          render={({ field: valueField }) => (
                            <FormItem>
                              <FormLabel>范围值</FormLabel>
                              <FormControl>
                                <ScopeValueSelect
                                  options={optionsQuery.data}
                                  scopeType={scopeType}
                                  value={valueField.value}
                                  onChange={valueField.onChange}
                                />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />

                        <div className='flex items-end'>
                          <Button
                            type='button'
                            variant='ghost'
                            size='icon'
                            onClick={() => remove(index)}
                            disabled={fields.length === 1}
                          >
                            <Trash2 />
                          </Button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </form>
            </Form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>配置预览</CardTitle>
            <CardDescription>
              表单实时展示授权规模和角色状态，便于联调权限模型。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3'>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <div className='flex items-center gap-2 text-sm text-muted-foreground'>
                <ShieldCheck className='size-4' />
                <span>角色分类</span>
              </div>
              <p className='mt-3 text-sm font-medium'>
                {resolveRoleCategoryLabel(roleCategory)}
              </p>
            </div>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <div className='flex items-center gap-2 text-sm text-muted-foreground'>
                <KeyRound className='size-4' />
                <span>菜单授权</span>
              </div>
              <p className='mt-3 text-sm font-medium'>
                已选 {selectedMenuIds.length} 项菜单权限
              </p>
            </div>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <div className='flex items-center gap-2 text-sm text-muted-foreground'>
                <DatabaseZap className='size-4' />
                <span>角色状态</span>
              </div>
              <p className='mt-3 text-sm font-medium'>
                {enabled ? '启用中' : '已停用'}
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

function RoleDetailPage({ roleId }: { roleId: string }) {
  const detailQuery = useQuery({
    queryKey: ['system-role', roleId],
    queryFn: () => getRoleDetail(roleId),
  })
  const optionsQuery = useQuery({
    queryKey: ['system-role-form-options'],
    queryFn: getRoleFormOptions,
  })

  if (detailQuery.isLoading || optionsQuery.isLoading) {
    return (
      <PageLoadingState
        title='角色详情'
        description='正在加载角色详情与权限摘要。'
      />
    )
  }

  if (detailQuery.isError || optionsQuery.isError || !detailQuery.data || !optionsQuery.data) {
    return (
      <PageErrorState
        title='角色详情'
        description='角色详情加载失败。'
        retry={() => {
          void detailQuery.refetch()
          void optionsQuery.refetch()
        }}
      />
    )
  }

  const detail = detailQuery.data
  const menuMap = new Map(optionsQuery.data.menus.map((item) => [item.id, item]))

  return (
    <PageShell
      title='角色详情'
      description='详情页独立展示角色、菜单授权和数据权限范围，便于系统联调和权限审计。'
      actions={
        <>
          <Button asChild>
            <Link to='/system/roles/$roleId/edit' params={{ roleId }}>
              <Save data-icon='inline-start' />
              编辑角色
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/roles/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>{detail.roleName}</CardTitle>
            <CardDescription>
              {detail.roleCode} · {resolveRoleCategoryLabel(detail.roleCategory)}
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4 md:grid-cols-2'>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <div className='flex items-center gap-2 text-sm text-muted-foreground'>
                <ShieldCheck className='size-4' />
                <span>角色状态</span>
              </div>
              <p className='mt-3 text-sm font-medium'>
                {detail.enabled ? '启用' : '停用'}
              </p>
            </div>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <div className='flex items-center gap-2 text-sm text-muted-foreground'>
                <KeyRound className='size-4' />
                <span>菜单授权数量</span>
              </div>
              <p className='mt-3 text-sm font-medium'>
                {detail.menuIds.length} 项
              </p>
            </div>
            <div className='rounded-lg border bg-muted/20 p-4 md:col-span-2'>
              <div className='flex items-center gap-2 text-sm text-muted-foreground'>
                <Users className='size-4' />
                <span>角色说明</span>
              </div>
              <p className='mt-3 text-sm font-medium'>
                {detail.description || '未填写角色说明'}
              </p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>数据权限摘要</CardTitle>
            <CardDescription>
              登录态会继承下列数据范围，并应用到业务列表和 AI 能力。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3'>
            {detail.dataScopes.length === 0 ? (
              <p className='text-sm text-muted-foreground'>未配置数据权限范围。</p>
            ) : (
              detail.dataScopes.map((item) => (
                <div key={`${item.scopeType}-${item.scopeValue}`} className='rounded-lg border p-3'>
                  <div className='text-sm font-medium'>
                    {resolveScopeTypeLabel(item.scopeType)}
                  </div>
                  <div className='text-xs text-muted-foreground'>
                    {item.scopeValue}
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card className='xl:col-span-2'>
          <CardHeader>
            <CardTitle>菜单授权清单</CardTitle>
            <CardDescription>
              角色已授权菜单将聚合为当前用户的菜单、权限标识和按钮控制。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 md:grid-cols-2 xl:grid-cols-3'>
            {detail.menuIds.map((menuId) => {
              const menu = menuMap.get(menuId)

              return (
                <div key={menuId} className='rounded-lg border p-3'>
                  <div className='flex items-center gap-2'>
                    <span className='text-sm font-medium'>
                      {menu?.name || menuId}
                    </span>
                    {menu ? <Badge variant='outline'>{menu.menuType}</Badge> : null}
                  </div>
                  <div className='mt-1 text-xs text-muted-foreground'>
                    {menu?.parentMenuName || '顶级菜单'}
                  </div>
                </div>
              )
            })}
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

export function RoleCreatePage() {
  return <RoleFormPage mode='create' />
}

export function RoleEditPage({ roleId }: { roleId: string }) {
  return <RoleFormPage mode='edit' roleId={roleId} />
}

export function RoleDetailPageEntry({ roleId }: { roleId: string }) {
  return <RoleDetailPage roleId={roleId} />
}
