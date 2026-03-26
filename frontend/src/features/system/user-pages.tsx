import {
  startTransition,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useFieldArray, useForm, useWatch, type UseFormReturn } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  BriefcaseBusiness,
  Building2,
  Loader2,
  Mail,
  Phone,
  Plus,
  RefreshCw,
  Save,
  ShieldCheck,
  UserRound,
  Trash2,
} from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
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
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { MultiSelectDropdown } from '@/components/multi-select-dropdown'
import { ConfirmDialog } from '@/components/confirm-dialog'
import { getApiErrorResponse } from '@/lib/api/client'
import { cn } from '@/lib/utils'
import { normalizeListQuerySearch } from '@/features/shared/table/query-contract'
import {
  getDepartmentTree,
  type DepartmentTreeNode,
} from '@/lib/api/system-org'
import {
  createSystemUser,
  deleteSystemUser,
  getSystemUserDetail,
  getSystemUserFormOptions,
  listSystemUsers,
  updateSystemUser,
  type SaveSystemUserPayload,
  type SystemUserDetail,
  type SystemUserRecord,
} from '@/lib/api/system-users'
import { handleServerError } from '@/lib/handle-server-error'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'

const usersListRoute = getRouteApi('/_authenticated/system/users/list')

const systemUserFormSchema = z.object({
  displayName: z.string().min(2, '用户姓名至少需要 2 个字符'),
  username: z
    .string()
    .min(2, '登录账号至少需要 2 个字符')
    .regex(/^[a-zA-Z0-9_-]+$/, '登录账号仅支持字母、数字、下划线和中划线'),
  mobile: z.string().regex(/^1\d{10}$/, '请输入 11 位手机号'),
  email: z.email('请输入正确的邮箱地址'),
  companyId: z.string().min(1, '请选择所属公司'),
  departmentId: z.string().min(1, '请选择所属部门'),
  primaryPostId: z.string().min(1, '请选择主岗位'),
  roleIds: z.array(z.string()).min(1, '请至少选择一个角色'),
  enabled: z.boolean(),
  partTimeAssignments: z.array(
    z.object({
      companyId: z.string().min(1, '请选择所属公司'),
      departmentId: z.string().min(1, '请选择所属部门'),
      postId: z.string().min(1, '请选择岗位'),
      roleIds: z.array(z.string()).min(1, '请至少选择一个角色'),
      enabled: z.boolean(),
    })
  ),
})

type SystemUserFormValues = z.infer<typeof systemUserFormSchema>
type SubmitAction = 'list' | 'continue'

type UserRow = {
  userId: string
  displayName: string
  username: string
  mobile: string
  email: string
  departmentName: string
  postName: string
  status: '启用' | '停用'
  createdAt: string
}

// 用户列表和详情页统一用这个方法格式化时间。
function formatDateTime(value: string) {
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

// 用户状态只展示启用/停用，和系统管理其他页面保持一致。
function resolveStatusLabel(status: SystemUserRecord['status']) {
  return status === 'ENABLED' ? '启用' : '停用'
}

// 用户已选角色的类别在页面里统一显示成中文。
function resolveRoleCategoryLabel(roleCategory: 'SYSTEM' | 'BUSINESS') {
  return roleCategory === 'SYSTEM' ? '系统角色' : '业务角色'
}

function resolvePrimaryAssignmentValues(detail?: SystemUserDetail) {
  const primaryAssignment = detail?.primaryAssignment
  return {
    companyId: detail?.companyId || primaryAssignment?.companyId || '',
    companyName: detail?.companyName || primaryAssignment?.companyName || '',
    departmentId: detail?.departmentId || primaryAssignment?.departmentId || '',
    departmentName:
      detail?.departmentName || primaryAssignment?.departmentName || '',
    postId: detail?.postId || primaryAssignment?.postId || '',
    postName: detail?.postName || primaryAssignment?.postName || '',
    roleIds: (detail?.roleIds?.length ?? 0) > 0
      ? (detail?.roleIds ?? [])
      : (primaryAssignment?.roleIds ?? []),
  }
}

// 编辑场景下把详情数据转换成表单默认值。
// eslint-disable-next-line react-refresh/only-export-components
export function toFormValues(detail?: SystemUserDetail): SystemUserFormValues {
  const primaryAssignment = resolvePrimaryAssignmentValues(detail)
  return {
    displayName: detail?.displayName ?? '',
    username: detail?.username ?? '',
    mobile: detail?.mobile ?? '',
    email: detail?.email ?? '',
    companyId: primaryAssignment.companyId,
    departmentId: primaryAssignment.departmentId,
    primaryPostId: primaryAssignment.postId,
    roleIds: primaryAssignment.roleIds,
    enabled: detail?.enabled ?? true,
    partTimeAssignments:
      detail?.partTimeAssignments.map((assignment) => ({
        companyId: assignment.companyId,
        departmentId: assignment.departmentId,
        postId: assignment.postId,
        roleIds: assignment.roleIds,
        enabled: assignment.enabled,
      })) ?? [],
  }
}

// 只允许把已知字段的服务端错误回写到表单。
function isSystemUserField(
  field: string
): field is keyof SystemUserFormValues {
  return (
    field === 'displayName' ||
    field === 'username' ||
      field === 'mobile' ||
      field === 'email' ||
      field === 'companyId' ||
      field === 'departmentId' ||
      field === 'primaryPostId' ||
      field === 'roleIds' ||
      field === 'enabled'
  )
}

type DepartmentOption = DepartmentTreeNode & {
  depth: number
}

function flattenDepartmentTreeOptions(
  nodes: DepartmentTreeNode[],
  depth = 0
): DepartmentOption[] {
  return nodes.flatMap((node) => [
    { ...node, depth },
    ...flattenDepartmentTreeOptions(node.children, depth + 1),
  ])
}

function DepartmentTreeSelect({
  value,
  onChange,
  placeholder,
  disabled,
  options,
}: {
  value: string
  onChange: (value: string) => void
  placeholder: string
  disabled?: boolean
  options: DepartmentOption[]
}) {
  const selectedLabel = options.find(
    (department) => department.departmentId === value
  )?.departmentName

  return (
    <Select value={value} onValueChange={onChange} disabled={disabled}>
      <FormControl>
        <SelectTrigger className='w-full'>
          <span
            className={cn(
              'truncate',
              !selectedLabel && 'text-muted-foreground'
            )}
          >
            {selectedLabel || placeholder}
          </span>
        </SelectTrigger>
      </FormControl>
      <SelectContent>
        <SelectGroup>
          <SelectLabel>部门树</SelectLabel>
          {options.map((department) => (
            <SelectItem
              key={department.departmentId}
              value={department.departmentId}
            >
              {`${'　'.repeat(department.depth)}${department.departmentName}`}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}

// 把后端字段级错误映射到对应表单项。
function applySystemUserFieldErrors(
  form: UseFormReturn<SystemUserFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    if (isSystemUserField(fieldError.field)) {
      form.setError(fieldError.field, {
        type: 'server',
        message: fieldError.message,
      })
    }
  })

  if (apiError?.code === 'BIZ.USERNAME_DUPLICATED') {
    form.setError('username', {
      type: 'server',
      message: apiError.message,
    })
  }

  if (apiError?.code === 'VALIDATION.REQUEST_INVALID' && apiError.message.includes('角色')) {
    form.setError('roleIds', {
      type: 'server',
      message: apiError.message,
    })
  }

  return apiError
}

function buildUserColumns(
  onDelete: (row: UserRow) => void
): ColumnDef<UserRow>[] {
  return [
  {
    accessorKey: 'displayName',
    header: '用户姓名',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.displayName}</span>
        <span className='text-xs text-muted-foreground'>
          @{row.original.username}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'departmentName',
    header: '所属部门',
  },
  {
    accessorKey: 'postName',
    header: '当前岗位',
  },
  {
    accessorKey: 'mobile',
    header: '手机号',
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
    accessorKey: 'createdAt',
    header: '创建时间',
    cell: ({ row }) => (
      <span className='text-sm text-muted-foreground'>
        {row.original.createdAt}
      </span>
    ),
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link to='/system/users/$userId' params={{ userId: row.original.userId }} search={{}}>
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/users/$userId/edit'
            params={{ userId: row.original.userId }}
          >
            编辑
          </Link>
        </Button>
        <Button
          variant='ghost'
          className='h-8 px-2 text-destructive hover:text-destructive'
          onClick={() => onDelete(row.original)}
        >
          删除
        </Button>
      </div>
    ),
  },
]
}

function PageErrorState({
  title,
  description,
  retry,
  listHref,
}: {
  title: string
  description: string
  retry?: () => void
  listHref?: string
}) {
  return (
    <PageShell title={title} description={description}>
      <Alert variant='destructive'>
        <AlertCircle />
        <AlertTitle>页面加载失败</AlertTitle>
        <AlertDescription>
          数据请求未成功，请稍后重试或返回列表页继续其他操作。
        </AlertDescription>
      </Alert>

      <div className='flex flex-wrap gap-2'>
        {retry ? (
          <Button onClick={retry}>
            <RefreshCw data-icon='inline-start' />
            重新加载
          </Button>
        ) : null}
        {listHref ? (
          <Button asChild variant='outline'>
            <Link to={listHref} search={{}}>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        ) : null}
      </div>
    </PageShell>
  )
}

function PageLoadingState({
  title,
  description,
  sidebar,
}: {
  title: string
  description: string
  sidebar?: ReactNode
}) {
  return (
    <PageShell title={title} description={description}>
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <div className='flex flex-col gap-4'>
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
        </div>
        <div className='flex flex-col gap-4'>
          {sidebar ?? (
            <Card>
              <CardHeader>
                <Skeleton className='h-6 w-32' />
                <Skeleton className='h-4 w-full' />
              </CardHeader>
              <CardContent className='flex flex-col gap-3'>
                {Array.from({ length: 3 }).map((_, index) => (
                  <Skeleton key={index} className='h-16 w-full' />
                ))}
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </PageShell>
  )
}

function UserDetailMetric({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof UserRound
  label: string
  value: string
}) {
  return (
    <div className='rounded-lg border bg-muted/20 p-4'>
      <div className='flex items-center gap-2 text-sm text-muted-foreground'>
        <Icon className='size-4' />
        <span>{label}</span>
      </div>
      <p className='mt-3 text-sm font-medium'>{value}</p>
    </div>
  )
}

function SystemUserFormPage({
  mode,
  userId,
}: {
  mode: 'create' | 'edit'
  userId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitAction, setSubmitAction] = useState<SubmitAction>('list')
  const formInitializedRef = useRef(false)
  const isEdit = mode === 'edit'
  const form = useForm<SystemUserFormValues>({
    resolver: zodResolver(systemUserFormSchema),
    defaultValues: toFormValues(),
    mode: 'onBlur',
  })
  const partTimeFieldArray = useFieldArray({
    control: form.control,
    name: 'partTimeAssignments',
  })

  const optionsQuery = useQuery({
    queryKey: ['system-user-form-options'],
    queryFn: getSystemUserFormOptions,
  })
  const departmentTreeQuery = useQuery({
    queryKey: ['system-departments', 'tree', 'user-form'],
    queryFn: getDepartmentTree,
  })

  const detailQuery = useQuery({
    queryKey: ['system-user', userId],
    queryFn: () => getSystemUserDetail(userId!),
    enabled: isEdit && Boolean(userId),
  })

  useEffect(() => {
    if (formInitializedRef.current) {
      return
    }

    if (isEdit) {
      if (
        !detailQuery.data ||
        !optionsQuery.data ||
        !departmentTreeQuery.data
      ) {
        return
      }

      form.reset(toFormValues(detailQuery.data))
      formInitializedRef.current = true
      return
    }

    if (optionsQuery.data && departmentTreeQuery.data) {
      formInitializedRef.current = true
    }
  }, [
    departmentTreeQuery.data,
    detailQuery.data,
    form,
    isEdit,
    optionsQuery.data,
  ])

  const createMutation = useMutation({
    mutationFn: createSystemUser,
    onError: () => undefined,
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: SaveSystemUserPayload }) =>
      updateSystemUser(id, payload),
    onError: () => undefined,
  })

  const selectedCompanyId = useWatch({
    control: form.control,
    name: 'companyId',
  })
  const selectedDepartmentId = useWatch({
    control: form.control,
    name: 'departmentId',
  })
  const selectedPostId = useWatch({
    control: form.control,
    name: 'primaryPostId',
  })
  const selectedRoleIds = useWatch({
    control: form.control,
    name: 'roleIds',
  })
  const enabled = useWatch({
    control: form.control,
    name: 'enabled',
  })
  const partTimeAssignments = useWatch({
    control: form.control,
    name: 'partTimeAssignments',
  })
  const displayName = useWatch({
    control: form.control,
    name: 'displayName',
  })
  const initialPrimaryAssignment = useMemo(
    () => resolvePrimaryAssignmentValues(detailQuery.data),
    [detailQuery.data]
  )
  const effectiveCompanyId =
    selectedCompanyId || initialPrimaryAssignment.companyId
  const effectiveDepartmentId =
    selectedDepartmentId || initialPrimaryAssignment.departmentId
  const effectivePostId = selectedPostId || initialPrimaryAssignment.postId
  const selectedCompany = optionsQuery.data?.companies.find(
    (company) => company.id === effectiveCompanyId
  )
  const selectedPost = optionsQuery.data?.posts.find(
    (post) => post.id === effectivePostId
  )
  const departmentOptions = useMemo(
    () => flattenDepartmentTreeOptions(departmentTreeQuery.data ?? []),
    [departmentTreeQuery.data]
  )
  const primaryDepartmentOptions = useMemo(
    () =>
      departmentOptions.filter(
        (department) => department.companyId === effectiveCompanyId
      ),
    [departmentOptions, effectiveCompanyId]
  )
  const selectedDepartment = primaryDepartmentOptions.find(
    (department) => department.departmentId === effectiveDepartmentId
  )
  const primaryPostOptions = useMemo(
    () =>
      (optionsQuery.data?.posts ?? []).filter(
        (post) =>
          post.departmentId === effectiveDepartmentId &&
          (!effectiveCompanyId ||
            primaryDepartmentOptions.some(
              (department) =>
                department.departmentId === post.departmentId &&
                department.companyId === effectiveCompanyId
            ))
      ),
    [
      optionsQuery.data?.posts,
      primaryDepartmentOptions,
      effectiveCompanyId,
      effectiveDepartmentId,
    ]
  )
  const selectedRoleIdList = selectedRoleIds ?? []
  const selectedRoles =
    optionsQuery.data?.roles.filter((role) => selectedRoleIdList.includes(role.id)) ??
    []
  const selectedCompanyLabel =
    optionsQuery.data?.companies.find(
      (company) => company.id === effectiveCompanyId
    )
      ?.name ?? ''
  const selectedPrimaryPostLabel =
    primaryPostOptions.find((post) => post.id === effectivePostId)
      ? `${primaryPostOptions.find((post) => post.id === effectivePostId)!.name} / ${primaryPostOptions.find((post) => post.id === effectivePostId)!.departmentName}`
      : ''
  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const isInitialLoading =
    optionsQuery.isLoading ||
    departmentTreeQuery.isLoading ||
    (isEdit && detailQuery.isLoading)

  useEffect(() => {
    if (!formInitializedRef.current) {
      return
    }

    if (departmentTreeQuery.isLoading) {
      return
    }

    if (!selectedCompanyId) {
      if (selectedDepartmentId) {
        form.setValue('departmentId', '')
      }
      if (selectedPostId) {
        form.setValue('primaryPostId', '')
      }
      return
    }

    const departmentStillValid = primaryDepartmentOptions.some(
      (department) => department.departmentId === selectedDepartmentId
    )
    if (!departmentStillValid && selectedDepartmentId) {
      form.setValue('departmentId', '')
      form.setValue('primaryPostId', '')
    }
  }, [
    departmentTreeQuery.isLoading,
    form,
    primaryDepartmentOptions,
    selectedCompanyId,
    selectedDepartmentId,
    selectedPostId,
  ])

  useEffect(() => {
    if (!formInitializedRef.current) {
      return
    }

    if (optionsQuery.isLoading || departmentTreeQuery.isLoading) {
      return
    }

    if (!selectedDepartmentId) {
      if (selectedPostId) {
        form.setValue('primaryPostId', '')
      }
      return
    }

    const postStillValid = primaryPostOptions.some(
      (post) => post.id === selectedPostId
    )
    if (!postStillValid && selectedPostId) {
      form.setValue('primaryPostId', '')
    }
  }, [
    departmentTreeQuery.isLoading,
    form,
    optionsQuery.isLoading,
    primaryPostOptions,
    selectedDepartmentId,
    selectedPostId,
  ])

  useEffect(() => {
    if (!formInitializedRef.current) {
      return
    }

    if (optionsQuery.isLoading || departmentTreeQuery.isLoading) {
      return
    }

    partTimeAssignments?.forEach((assignment, index) => {
      const companyId = assignment?.companyId ?? ''
      const departmentId = assignment?.departmentId ?? ''
      const postId = assignment?.postId ?? ''
      const departmentOptionsForAssignment = departmentOptions.filter(
        (department) => department.companyId === companyId
      )

      const departmentStillValid = departmentOptionsForAssignment.some(
        (department) => department.departmentId === departmentId
      )

      if (departmentId && !departmentStillValid) {
        form.setValue(`partTimeAssignments.${index}.departmentId`, '')
        form.setValue(`partTimeAssignments.${index}.postId`, '')
        return
      }

      const validPosts = (optionsQuery.data?.posts ?? []).filter(
        (post) => post.departmentId === departmentId
      )

      if (postId && !validPosts.some((post) => post.id === postId)) {
        form.setValue(`partTimeAssignments.${index}.postId`, '')
      }
    })
  }, [
    departmentOptions,
    departmentTreeQuery.isLoading,
    form,
    optionsQuery.data?.posts,
    optionsQuery.isLoading,
    partTimeAssignments,
  ])

  async function onSubmit(values: SystemUserFormValues) {
    form.clearErrors()

    try {
      const payload: SaveSystemUserPayload = {
        displayName: values.displayName,
        username: values.username,
        mobile: values.mobile,
        email: values.email,
        companyId: values.companyId,
        primaryPostId: values.primaryPostId,
        roleIds: values.roleIds,
        enabled: values.enabled,
        primaryAssignment: {
          companyId: values.companyId,
          postId: values.primaryPostId,
          roleIds: values.roleIds,
          enabled: values.enabled,
        },
        partTimeAssignments: values.partTimeAssignments,
      }
      const result = isEdit
        ? await updateMutation.mutateAsync({ id: userId!, payload })
        : await createMutation.mutateAsync(payload)

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['system-users'] }),
        queryClient.invalidateQueries({ queryKey: ['system-user', result.userId] }),
      ])

      const nextUserId = result.userId
      const successMessage = isEdit ? '系统用户已更新' : '系统用户已创建'
      toast.success(successMessage)

      if (submitAction === 'continue') {
        startTransition(() => {
          navigate({
            to: '/system/users/$userId/edit',
            params: { userId: nextUserId },
            replace: isEdit,
          })
        })
        return
      }

      startTransition(() => {
        navigate({
          to: '/system/users/list',
        })
      })
    } catch (error) {
      const apiError = applySystemUserFieldErrors(form, error)

      if (!apiError || (!apiError.fieldErrors?.length && apiError.code !== 'BIZ.USERNAME_DUPLICATED')) {
        handleServerError(error)
      }
    }
  }

  if (isInitialLoading) {
    return (
      <PageLoadingState
        title={isEdit ? '编辑系统用户' : '新建系统用户'}
        description='正在加载表单选项与用户数据，请稍候。'
      />
    )
  }

  if (optionsQuery.isError || (isEdit && detailQuery.isError)) {
    return (
      <PageErrorState
        title={isEdit ? '编辑系统用户' : '新建系统用户'}
        description='页面需要的组织和用户数据未能加载完成。'
        retry={() => {
          void optionsQuery.refetch()
          void departmentTreeQuery.refetch()
          if (isEdit) {
            void detailQuery.refetch()
          }
        }}
        listHref='/system/users/list'
      />
    )
  }

  return (
    <PageShell
      title={isEdit ? '编辑系统用户' : '新建系统用户'}
      description={
        isEdit
          ? '编辑页独立承载账号、组织身份和启用状态维护，保存后可返回列表或继续编辑。'
          : '创建页独立承载系统用户录入，不使用弹窗或抽屉代替正式表单页面。'
      }
      actions={
        <>
          <Button
            type='submit'
            form='system-user-form'
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
            form='system-user-form'
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
            <Link to='/system/users/list' search={{}}>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Form {...form}>
          <form
            id='system-user-form'
            onSubmit={form.handleSubmit(onSubmit)}
            className='flex flex-col gap-4'
          >
            <Card>
              <CardHeader>
                <CardTitle>基础信息</CardTitle>
                <CardDescription>
                  维护用户姓名、登录账号和通知联系方式。登录账号支持唯一性校验。
                </CardDescription>
              </CardHeader>
              <CardContent className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='displayName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>用户姓名</FormLabel>
                      <FormControl>
                        <Input placeholder='请输入真实姓名' {...field} />
                      </FormControl>
                      <FormDescription>
                        用于审批记录展示、待办卡片与审计日志。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='username'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>登录账号</FormLabel>
                      <FormControl>
                        <Input placeholder='请输入登录账号' {...field} />
                      </FormControl>
                      <FormDescription>
                        建议与统一认证账号保持一致，避免后续身份映射冲突。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='mobile'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>手机号</FormLabel>
                      <FormControl>
                        <Input placeholder='请输入 11 位手机号' {...field} />
                      </FormControl>
                      <FormDescription>
                        用于催办、超时提醒和登录保护通知。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='email'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>邮箱地址</FormLabel>
                      <FormControl>
                        <Input placeholder='请输入邮箱地址' {...field} />
                      </FormControl>
                      <FormDescription>
                        用于邮件通知和账号联系。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>组织身份</CardTitle>
                <CardDescription>
                  主职与兼职都按任职记录维护，每条任职都对应公司、岗位和角色。
                </CardDescription>
              </CardHeader>
              <CardContent className='space-y-6'>
                <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='companyId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>所属公司</FormLabel>
                      <Select
                        value={field.value || effectiveCompanyId}
                        onValueChange={field.onChange}
                        disabled={isSubmitting}
                      >
                        <FormControl>
                          <SelectTrigger className='w-full'>
                            <span
                              className={cn(
                                'truncate',
                                !selectedCompanyLabel && 'text-muted-foreground'
                              )}
                            >
                              {selectedCompanyLabel || '请选择所属公司'}
                            </span>
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectGroup>
                            <SelectLabel>公司列表</SelectLabel>
                            {optionsQuery.data?.companies.map((company) => (
                              <SelectItem key={company.id} value={company.id}>
                                {company.name}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                      <FormDescription>
                        公司字段用于确定用户当前所属的组织身份范围。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='departmentId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>所属部门</FormLabel>
                      <DepartmentTreeSelect
                        value={field.value || effectiveDepartmentId}
                        onChange={field.onChange}
                        disabled={isSubmitting}
                        placeholder='请选择所属部门'
                        options={primaryDepartmentOptions}
                      />
                      <FormDescription>
                        先选部门，再按部门过滤岗位，重新编辑时也会优先回显这里。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='primaryPostId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>主岗位</FormLabel>
                      <Select
                        value={field.value || effectivePostId}
                        onValueChange={field.onChange}
                        disabled={isSubmitting || !effectiveDepartmentId}
                      >
                        <FormControl>
                          <SelectTrigger className='w-full'>
                            <span
                              className={cn(
                                'truncate',
                                !selectedPrimaryPostLabel &&
                                  'text-muted-foreground'
                              )}
                            >
                              {selectedPrimaryPostLabel || '请选择主岗位'}
                            </span>
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectGroup>
                            <SelectLabel>岗位列表</SelectLabel>
                            {primaryPostOptions.map((post) => (
                              <SelectItem key={post.id} value={post.id}>
                                {post.name} / {post.departmentName}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                      <FormDescription>
                        主岗位会决定当前主职任职的默认审批和数据上下文。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='roleIds'
                  render={({ field }) => (
                    <FormItem className='md:col-span-2'>
                      <FormLabel>角色分配</FormLabel>
                      <FormDescription>
                        用户可同时分配多个角色，使用下拉多选统一维护主职角色。
                      </FormDescription>
                      <FormControl>
                        <MultiSelectDropdown
                          value={field.value ?? []}
                          onChange={(nextValue) => {
                            field.onChange(nextValue)
                          }}
                          options={
                            optionsQuery.data?.roles.map((role) => ({
                              value: role.id,
                              label: role.name,
                              description: `${role.roleCode} · ${resolveRoleCategoryLabel(role.roleCategory)}`,
                            })) ?? []
                          }
                          placeholder='请选择角色'
                          searchPlaceholder='搜索角色'
                          emptyText='当前没有可分配的角色'
                          disabled={isSubmitting}
                          className='w-full'
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='enabled'
                  render={({ field }) => (
                    <FormItem className='rounded-lg border p-4 md:col-span-2'>
                      <div className='flex items-center justify-between gap-4'>
                        <div className='grid gap-1'>
                          <FormLabel>启用状态</FormLabel>
                          <FormDescription>
                            停用后用户不可登录，也不会再出现在新的审批指派候选中。
                          </FormDescription>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                            disabled={isSubmitting}
                          />
                        </FormControl>
                      </div>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                </div>

                <div className='space-y-4 rounded-lg border border-dashed p-4'>
                  <div className='flex items-center justify-between gap-3'>
                    <div className='space-y-1'>
                      <h3 className='text-sm font-semibold'>兼职任职</h3>
                      <p className='text-xs text-muted-foreground'>
                        兼职任职会作为可切换上下文存在，每条记录单独绑定岗位与角色。
                      </p>
                    </div>
                    <Button
                      type='button'
                      variant='outline'
                      size='sm'
                      onClick={() =>
                        partTimeFieldArray.append({
                          companyId: selectedCompanyId || '',
                          departmentId: selectedDepartmentId || '',
                          postId: '',
                          roleIds: [],
                          enabled: true,
                        })
                      }
                    >
                      <Plus className='size-4' />
                      新增兼职
                    </Button>
                  </div>

                  {partTimeFieldArray.fields.length === 0 ? (
                    <div className='rounded-lg bg-muted/40 px-3 py-4 text-sm text-muted-foreground'>
                      当前没有兼职任职，保存后用户只保留主职上下文。
                    </div>
                  ) : (
                    <div className='space-y-4'>
                      {partTimeFieldArray.fields.map((field, index) => {
                        const currentAssignment = partTimeAssignments?.[index]
                        const assignmentCompanyId = currentAssignment?.companyId ?? ''
                        const assignmentDepartmentId =
                          currentAssignment?.departmentId ?? ''
                        const assignmentDepartmentOptions = departmentOptions.filter(
                          (department) => department.companyId === assignmentCompanyId
                        )
                        const assignmentPostOptions = (
                          optionsQuery.data?.posts ?? []
                        ).filter(
                          (post) => post.departmentId === assignmentDepartmentId
                        )
                        const assignmentCompanyLabel =
                          optionsQuery.data?.companies.find(
                            (company) => company.id === assignmentCompanyId
                          )?.name ?? ''
                        const assignmentPostLabel =
                          assignmentPostOptions.find(
                            (post) => post.id === currentAssignment?.postId
                          )
                            ? `${assignmentPostOptions.find((post) => post.id === currentAssignment?.postId)!.name} / ${assignmentPostOptions.find((post) => post.id === currentAssignment?.postId)!.departmentName}`
                            : ''
                        return (
                          <div key={field.id} className='space-y-4 rounded-lg border p-4'>
                            <div className='flex items-center justify-between gap-3'>
                              <div>
                                <p className='text-sm font-medium'>兼职任职 {index + 1}</p>
                                <p className='text-xs text-muted-foreground'>
                                  指定该任职对应的公司、岗位和角色。
                                </p>
                              </div>
                              <Button
                                type='button'
                                variant='ghost'
                                size='sm'
                                onClick={() => partTimeFieldArray.remove(index)}
                              >
                                <Trash2 className='size-4' />
                                删除
                              </Button>
                            </div>

                            <div className='grid gap-4 md:grid-cols-2'>
                              <FormField
                                control={form.control}
                                name={`partTimeAssignments.${index}.companyId`}
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>所属公司</FormLabel>
                                    <Select value={field.value} onValueChange={field.onChange}>
                                      <FormControl>
                                        <SelectTrigger className='w-full'>
                                          <span
                                            className={cn(
                                              'truncate',
                                              !assignmentCompanyLabel &&
                                                'text-muted-foreground'
                                            )}
                                          >
                                            {assignmentCompanyLabel ||
                                              '请选择所属公司'}
                                          </span>
                                        </SelectTrigger>
                                      </FormControl>
                                      <SelectContent>
                                        {optionsQuery.data?.companies.map((company) => (
                                          <SelectItem key={company.id} value={company.id}>
                                            {company.name}
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
                                name={`partTimeAssignments.${index}.departmentId`}
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>所属部门</FormLabel>
                                    <DepartmentTreeSelect
                                      value={field.value}
                                      onChange={field.onChange}
                                      placeholder='请选择所属部门'
                                      disabled={isSubmitting || !assignmentCompanyId}
                                      options={assignmentDepartmentOptions}
                                    />
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <FormField
                                control={form.control}
                                name={`partTimeAssignments.${index}.postId`}
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>岗位</FormLabel>
                                    <Select
                                      value={field.value}
                                      onValueChange={field.onChange}
                                      disabled={
                                        isSubmitting || !assignmentDepartmentId
                                      }
                                    >
                                      <FormControl>
                                        <SelectTrigger className='w-full'>
                                          <span
                                            className={cn(
                                              'truncate',
                                              !assignmentPostLabel &&
                                                'text-muted-foreground'
                                            )}
                                          >
                                            {assignmentPostLabel ||
                                              '请选择岗位'}
                                          </span>
                                        </SelectTrigger>
                                      </FormControl>
                                      <SelectContent>
                                        {assignmentPostOptions.map((post) => (
                                          <SelectItem key={post.id} value={post.id}>
                                            {post.name} / {post.departmentName}
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
                                name={`partTimeAssignments.${index}.roleIds`}
                                render={({ field }) => (
                                  <FormItem className='md:col-span-2'>
                                    <FormLabel>角色分配</FormLabel>
                                    <FormControl>
                                      <MultiSelectDropdown
                                        value={field.value ?? []}
                                        onChange={(nextValue) => {
                                          field.onChange(nextValue)
                                        }}
                                        options={
                                          optionsQuery.data?.roles.map((role) => ({
                                            value: role.id,
                                            label: role.name,
                                            description: `${role.roleCode} · ${resolveRoleCategoryLabel(role.roleCategory)}`,
                                          })) ?? []
                                        }
                                        placeholder='请选择角色'
                                        searchPlaceholder='搜索角色'
                                        emptyText='当前没有可分配的角色'
                                        disabled={isSubmitting}
                                        className='w-full'
                                      />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <FormField
                                control={form.control}
                                name={`partTimeAssignments.${index}.enabled`}
                                render={({ field }) => (
                                  <FormItem className='rounded-lg border p-4 md:col-span-2'>
                                    <div className='flex items-center justify-between gap-4'>
                                      <div className='grid gap-1'>
                                        <FormLabel>任职状态</FormLabel>
                                        <FormDescription>
                                          停用后该兼职任职不会出现在上下文切换器里。
                                        </FormDescription>
                                      </div>
                                      <FormControl>
                                        <Switch checked={field.value} onCheckedChange={field.onChange} />
                                      </FormControl>
                                    </div>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          </form>
        </Form>

        <div className='flex flex-col gap-4'>
          <Card>
            <CardHeader>
              <CardTitle>当前预览</CardTitle>
              <CardDescription>
                随表单实时预览当前用户最终会写入的主身份上下文。
              </CardDescription>
            </CardHeader>
            <CardContent className='flex flex-col gap-3'>
              <UserDetailMetric
                icon={UserRound}
                label='显示名称'
                value={displayName || '待填写'}
              />
              <UserDetailMetric
                icon={Building2}
                label='所属公司'
                value={selectedCompany?.name || '待选择'}
              />
              <UserDetailMetric
                icon={BriefcaseBusiness}
                label='主岗位 / 主部门'
                value={
                  selectedPost && selectedDepartment
                    ? `${selectedPost.name} / ${selectedDepartment.departmentName}`
                    : '待选择'
                }
              />
              <UserDetailMetric
                icon={ShieldCheck}
                label='已选角色'
                  value={
                    selectedRoles.length > 0
                      ? selectedRoles.map((role) => role.name).join('、')
                      : '待选择'
                  }
                />
              <UserDetailMetric
                icon={BadgeCheck}
                label='状态'
                value={enabled ? '启用' : '停用'}
              />
              <UserDetailMetric
                icon={BriefcaseBusiness}
                label='兼职任职数'
                value={`${partTimeFieldArray.fields.length} 条`}
              />
            </CardContent>
          </Card>

        </div>
      </div>
    </PageShell>
  )
}

export function UsersListPage() {
  const search = normalizeListQuerySearch(usersListRoute.useSearch())
  const navigate = usersListRoute.useNavigate()
  const queryClient = useQueryClient()
  const [pendingDeleteRows, setPendingDeleteRows] = useState<UserRow[]>([])
  const clearSelectionRef = useRef<(() => void) | null>(null)
  const query = useQuery({
    queryKey: ['system-users', search],
    queryFn: () => listSystemUsers(search),
    placeholderData: (previous) => previous,
  })
  const deleteMutation = useMutation({
    mutationFn: async (userIds: string[]) => {
      await Promise.all(userIds.map((userId) => deleteSystemUser(userId)))
    },
    onSuccess: async (_, userIds) => {
      toast.success(
        userIds.length > 1
          ? `已删除 ${userIds.length} 个用户`
          : '用户已删除'
      )
      setPendingDeleteRows([])
      clearSelectionRef.current?.()
      clearSelectionRef.current = null
      await queryClient.invalidateQueries({ queryKey: ['system-users'] })
      await query.refetch()
    },
    onError: handleServerError,
  })

  const rows = useMemo<UserRow[]>(
    () =>
      (query.data?.records ?? []).map((record) => ({
        userId: record.userId,
        displayName: record.displayName,
        username: record.username,
        mobile: record.mobile,
        email: record.email,
        departmentName: record.departmentName || '-',
        postName: record.postName || '-',
        status: resolveStatusLabel(record.status),
        createdAt: formatDateTime(record.createdAt),
      })),
    [query.data?.records]
  )

  const summaries = useMemo(() => {
    const records = query.data?.records ?? []
    const enabledCount = records.filter(
      (record) => record.status === 'ENABLED'
    ).length
    const departmentCount = new Set(
      records.map((record) => record.departmentName).filter(Boolean)
    ).size

    return [
      {
        label: '用户总量',
        value: `${query.data?.total ?? 0}`,
        hint: '列表已接到真实分页接口，支持关键字模糊查询与排序联调。',
      },
      {
        label: '当前页启用',
        value: `${enabledCount}`,
        hint: '停用用户不会再进入新的审批候选范围。',
      },
      {
        label: '当前页部门数',
        value: `${departmentCount}`,
        hint: '按当前查询结果统计，便于核对组织覆盖范围。',
      },
    ]
  }, [query.data])
  const groupOptions = useMemo(
    () => [
      {
        field: 'status',
        label: '状态',
        getValue: (row: UserRow) => row.status,
      },
      {
        field: 'departmentName',
        label: '部门',
        getValue: (row: UserRow) => row.departmentName,
      },
    ],
    []
  )

  return (
    <>
      <ResourceListPage<UserRow>
        title='系统用户列表'
        description='系统用户列表页已接通真实后端分页接口，保留关键词模糊查询、分页、排序和独立详情/编辑页面跳转。'
        endpoint='/api/v1/system/users/page'
        searchPlaceholder='搜索姓名、账号、手机号、邮箱、部门或岗位'
        search={search}
        navigate={navigate}
        columns={buildUserColumns((row) => setPendingDeleteRows([row]))}
        data={rows}
        createAction={{ label: '新建系统用户', href: '/system/users/create' }}
        summaries={summaries}
        enableRowSelection
        getRowId={(row) => row.userId}
        groupOptions={groupOptions}
        renderBulkActions={({ selectedRows, clearSelection }) => (
          <Button
            variant='destructive'
            size='sm'
            onClick={() => {
              setPendingDeleteRows(selectedRows)
              clearSelectionRef.current = clearSelection
            }}
          >
            <Trash2 className='mr-2 size-4' />
            批量删除
          </Button>
        )}
      />
      <ConfirmDialog
        open={pendingDeleteRows.length > 0}
        onOpenChange={(open) => {
          if (!open && !deleteMutation.isPending) {
            setPendingDeleteRows([])
            clearSelectionRef.current = null
          }
        }}
        title={pendingDeleteRows.length > 1 ? '批量删除用户' : '删除用户'}
        desc={
          pendingDeleteRows.length > 1
            ? `确认删除已选中的 ${pendingDeleteRows.length} 个用户吗？删除后无法恢复。`
            : `确认删除用户「${pendingDeleteRows[0]?.displayName ?? ''}」吗？删除后无法恢复。`
        }
        destructive
        isLoading={deleteMutation.isPending}
        confirmText={deleteMutation.isPending ? '删除中…' : '确认删除'}
        handleConfirm={() =>
          void deleteMutation.mutate(
            pendingDeleteRows.map((row) => row.userId)
          )
        }
      />
    </>
  )
}

export function UserCreatePage() {
  return <SystemUserFormPage mode='create' />
}

export function UserEditPage({ userId }: { userId: string }) {
  return <SystemUserFormPage mode='edit' userId={userId} />
}

export function UserDetailPage({ userId }: { userId: string }) {
  const query = useQuery({
    queryKey: ['system-user', userId],
    queryFn: () => getSystemUserDetail(userId),
  })
  const optionsQuery = useQuery({
    queryKey: ['system-user-form-options'],
    queryFn: getSystemUserFormOptions,
  })

  if (query.isLoading) {
    return (
      <PageLoadingState
        title='系统用户详情'
        description='正在加载用户详情和组织身份信息，请稍候。'
        sidebar={
          <Card>
            <CardHeader>
              <Skeleton className='h-6 w-28' />
              <Skeleton className='h-4 w-full' />
            </CardHeader>
            <CardContent className='flex flex-col gap-3'>
              <Skeleton className='h-10 w-24' />
              <Skeleton className='h-10 w-28' />
              <Skeleton className='h-10 w-32' />
            </CardContent>
          </Card>
        }
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <PageErrorState
        title='系统用户详情'
        description='用户详情数据未能成功加载。'
        retry={() => void query.refetch()}
        listHref='/system/users/list'
      />
    )
  }

  const detail = query.data
  const primarySnapshot = resolvePrimaryAssignmentValues(detail)
  const roleNameById = new Map(
    (optionsQuery.data?.roles ?? []).map((role) => [role.id, role.name] as const)
  )
  const effectiveRoleIds =
    detail.roleIds.length > 0 ? detail.roleIds : primarySnapshot.roleIds

  return (
    <PageShell
      title='系统用户详情'
      description='详情页独立展示基础身份、组织归属和当前启用状态。'
      actions={
        <>
          <Button asChild>
            <Link to='/system/users/$userId/edit' params={{ userId }} search={{}}>
              <Save data-icon='inline-start' />
              编辑用户
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/users/list' search={{}}>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='flex flex-wrap gap-2'>
        <Badge variant='secondary'>
          {detail.enabled ? '启用中' : '已停用'}
        </Badge>
        <Badge variant='secondary'>
          {detail.companyName || primarySnapshot.companyName}
        </Badge>
        <Badge variant='secondary'>
          {detail.departmentName || primarySnapshot.departmentName}
        </Badge>
        <Badge variant='secondary'>
          {detail.postName || primarySnapshot.postName}
        </Badge>
        {effectiveRoleIds.length > 0 ? (
          effectiveRoleIds.map((roleId) => (
            <Badge key={roleId} variant='outline'>
              {roleNameById.get(roleId) ?? roleId}
            </Badge>
          ))
        ) : (
          <Badge variant='outline'>未分配角色</Badge>
        )}
      </div>

      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <div className='grid gap-4 lg:grid-cols-2'>
          <Card>
            <CardHeader>
              <CardTitle>基础信息</CardTitle>
              <CardDescription>对齐当前用户上下文的账号和联系字段。</CardDescription>
            </CardHeader>
            <CardContent className='grid gap-3 sm:grid-cols-2'>
              <UserDetailMetric
                icon={UserRound}
                label='用户姓名'
                value={detail.displayName}
              />
              <UserDetailMetric
                icon={BadgeCheck}
                label='登录账号'
                value={detail.username}
              />
              <UserDetailMetric
                icon={Phone}
                label='手机号'
                value={detail.mobile}
              />
              <UserDetailMetric
                icon={Mail}
                label='邮箱地址'
                value={detail.email}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>组织身份</CardTitle>
              <CardDescription>展示当前主任职对应的公司、部门和岗位。</CardDescription>
            </CardHeader>
            <CardContent className='grid gap-3 sm:grid-cols-2'>
              <UserDetailMetric
                icon={Building2}
                label='所属公司'
                value={detail.companyName || primarySnapshot.companyName}
              />
              <UserDetailMetric
                icon={Building2}
                label='主部门'
                value={detail.departmentName || primarySnapshot.departmentName}
              />
              <UserDetailMetric
                icon={BriefcaseBusiness}
                label='主岗位'
                value={detail.postName || primarySnapshot.postName}
              />
              <UserDetailMetric
                icon={ShieldCheck}
                label='当前状态'
                value={detail.enabled ? '启用' : '停用'}
              />
            </CardContent>
          </Card>
        </div>

        <div className='flex flex-col gap-4'>
          <Card>
            <CardHeader>
              <CardTitle>身份摘要</CardTitle>
              <CardDescription>汇总当前账号和主任职的关键标识。</CardDescription>
            </CardHeader>
            <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
              <p>用户 ID：{detail.userId}</p>
              <p>公司 ID：{detail.companyId || primarySnapshot.companyId}</p>
              <p>
                部门 ID：{detail.departmentId || primarySnapshot.departmentId}
              </p>
              <p>岗位 ID：{detail.postId || primarySnapshot.postId}</p>
            </CardContent>
          </Card>

          <Alert>
            <ShieldCheck />
            <AlertTitle>页面定位</AlertTitle>
            <AlertDescription>
              详情、编辑、创建均已拆成独立页面，符合平台统一 CRUD 规范，不再依赖弹窗式维护。
            </AlertDescription>
          </Alert>
        </div>
      </div>
    </PageShell>
  )
}
