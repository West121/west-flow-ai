import { startTransition, useEffect, useMemo, useRef, useState } from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch, type Path, type UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'
import {
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  BriefcaseBusiness,
  Building2,
  ChevronDown,
  ChevronRight,
  GitBranch,
  Loader2,
  Save,
  ShieldCheck,
  Trash2,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/confirm-dialog'
import {
  Card,
  CardContent,
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
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { getApiErrorResponse } from '@/lib/api/client'
import {
  createCompany,
  createDepartment,
  createPost,
  deleteCompany,
  deleteDepartment,
  deletePost,
  getCompanyDetail,
  getCompanyFormOptions,
  getDepartmentDetail,
  getDepartmentFormOptions,
  getDepartmentUsers,
  getDepartmentTree,
  getPostDetail,
  getPostFormOptions,
  getPostUsers,
  listCompanies,
  listPosts,
  updateCompany,
  updateDepartment,
  updatePost,
  type DepartmentTreeNode,
  type SaveCompanyPayload,
} from '@/lib/api/system-org'
import { handleServerError } from '@/lib/handle-server-error'
import { ProFormActions, ProFormShell } from '@/features/shared/pro-form'
import { ProTable } from '@/features/shared/pro-table'
import { PageShell } from '@/features/shared/page-shell'
import { normalizeListQuerySearch } from '@/features/shared/table/query-contract'
import { AssociatedUsersDialog } from './associated-users-dialog'
import {
  exportAllCompaniesCsv,
  exportAllPostsCsv,
  exportCompaniesCsv,
  exportDepartmentsCsv,
  exportPostsCsv,
  importCompaniesCsv,
  importDepartmentsCsv,
  importPostsCsv,
} from './org-csv'

const rolesRoute = getRouteApi('/_authenticated/system/roles/list')
const companiesRoute = getRouteApi('/_authenticated/system/companies/list')
const departmentsRoute = getRouteApi('/_authenticated/system/departments/list')
const postsRoute = getRouteApi('/_authenticated/system/posts/list')

const companyFormSchema = z.object({
  companyName: z.string().min(2, '公司名称至少需要 2 个字符'),
  enabled: z.boolean(),
})

const departmentFormSchema = z.object({
  companyId: z.string().min(1, '请选择所属公司'),
  parentDepartmentId: z.string().nullable(),
  departmentName: z.string().min(2, '部门名称至少需要 2 个字符'),
  enabled: z.boolean(),
})

const postFormSchema = z.object({
  companyId: z.string().min(1, '请选择所属公司'),
  departmentId: z.string().min(1, '请选择所属部门'),
  postName: z.string().min(2, '岗位名称至少需要 2 个字符'),
  enabled: z.boolean(),
})

type CompanyFormValues = z.infer<typeof companyFormSchema>
type DepartmentFormValues = z.infer<typeof departmentFormSchema>
type PostFormValues = z.infer<typeof postFormSchema>
type SubmitAction = 'list' | 'continue'

type CompanyRow = {
  companyId: string
  companyName: string
  status: '启用' | '停用'
  createdAt: string
}

type PostRow = {
  postId: string
  companyName: string
  departmentName: string
  postName: string
  status: '启用' | '停用'
  createdAt: string
}

const basicColumns: ColumnDef<{
  id: string
  name: string
  owner: string
  scope: string
  status: string
}>[] = [
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

// 统一把组织管理页里的时间格式化成中文展示。
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

// 把后端启停状态映射成页面文案。
function resolveStatusLabel(status: string) {
  return status === 'ENABLED' ? '启用' : '停用'
}

function flattenDepartmentTree(nodes: DepartmentTreeNode[]): DepartmentTreeNode[] {
  return nodes.flatMap((node) => [node, ...flattenDepartmentTree(node.children)])
}

function countDepartmentNodes(nodes: DepartmentTreeNode[]): number {
  return nodes.reduce(
    (count, node) => count + 1 + countDepartmentNodes(node.children),
    0
  )
}

// 详情页的指标卡只负责展示一个图标、标题和数值。
function DetailMetric({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Building2
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

// 页面加载失败时统一显示错误态和返回入口。
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
          组织数据请求未成功，请重试或先返回列表页。
        </AlertDescription>
      </Alert>
      <div className='flex flex-wrap gap-2'>
        {retry ? <Button onClick={retry}>重新加载</Button> : null}
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

// 编辑页和详情页共用同一套骨架屏。
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
            {Array.from({ length: 4 }).map((_, index) => (
              <div key={index} className='flex flex-col gap-2'>
                <Skeleton className='h-4 w-20' />
                <Skeleton className='h-10 w-full' />
              </div>
            ))}
          </CardContent>
        </Card>
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
      </div>
    </PageShell>
  )
}

function applyFieldErrors<T extends Record<string, unknown>>(
  form: UseFormReturn<T>,
  error: unknown,
  fields: Array<Path<T>>,
  duplicateCodes: string[] = [],
  duplicateTargetField?: Path<T>
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    const matchedField = fields.find((field) => field === fieldError.field)
    if (matchedField) {
      form.setError(matchedField, {
        type: 'server',
        message: fieldError.message,
      })
    }
  })

  if (apiError && duplicateCodes.includes(apiError.code)) {
    const defaultField = duplicateTargetField ?? fields[0]
    form.setError(defaultField, {
      type: 'server',
      message: apiError.message,
    })
  }

  return apiError
}

function buildCompanyColumns(
  onDelete: (row: CompanyRow) => void
): ColumnDef<CompanyRow>[] {
  return [
  {
    accessorKey: 'companyName',
    header: '公司名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.companyName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.companyId}
        </span>
      </div>
    ),
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
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/companies/$companyId'
            params={{ companyId: row.original.companyId }}
          >
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/companies/$companyId/edit'
            params={{ companyId: row.original.companyId }}
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

function buildDepartmentColumns(
  onShowUsers: (row: DepartmentTreeNode) => void,
  onDelete: (row: DepartmentTreeNode) => void
): ColumnDef<DepartmentTreeNode>[] {
  return [
    {
      accessorKey: 'departmentName',
      header: '部门名称',
      cell: ({ row }) => (
        <div
          className='flex items-start gap-2'
          style={{ paddingLeft: `${row.depth * 20}px` }}
        >
          {row.getCanExpand() ? (
            <Button
              type='button'
              variant='ghost'
              size='icon'
              className='size-7 shrink-0'
              aria-label={
                row.getIsExpanded()
                  ? `收起 ${row.original.departmentName}`
                  : `展开 ${row.original.departmentName}`
              }
              onClick={row.getToggleExpandedHandler()}
            >
              {row.getIsExpanded() ? (
                <ChevronDown className='size-4' />
              ) : (
                <ChevronRight className='size-4' />
              )}
            </Button>
          ) : (
            <span className='inline-flex size-7 shrink-0 items-center justify-center text-muted-foreground'>
              ·
            </span>
          )}
          <div className='flex min-w-0 flex-col gap-1'>
            <span className='font-medium'>{row.original.departmentName}</span>
            <span className='text-xs text-muted-foreground'>
              {row.original.departmentId}
            </span>
            <span className='text-xs text-muted-foreground'>
              上级部门：{row.original.parentDepartmentName || '无上级部门'}
            </span>
          </div>
        </div>
      ),
    },
    {
      accessorKey: 'companyName',
      header: '所属公司',
    },
    {
      accessorKey: 'status',
      header: '状态',
      cell: ({ row }) => (
        <Badge
          variant={row.original.status === 'ENABLED' ? 'secondary' : 'outline'}
        >
          {row.original.status}
        </Badge>
      ),
    },
    {
      accessorKey: 'createdAt',
      header: '创建时间',
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
            <Link
              to='/system/departments/$departmentId'
              params={{ departmentId: row.original.departmentId }}
            >
              详情
            </Link>
          </Button>
          <Button asChild variant='ghost' className='h-8 px-2'>
            <Link
              to='/system/departments/$departmentId/edit'
              params={{ departmentId: row.original.departmentId }}
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

function buildPostColumns(
  onShowUsers: (row: PostRow) => void,
  onDelete: (row: PostRow) => void
): ColumnDef<PostRow>[] {
  return [
  {
    accessorKey: 'postName',
    header: '岗位名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.postName}</span>
        <span className='text-xs text-muted-foreground'>{row.original.postId}</span>
      </div>
    ),
  },
  {
    accessorKey: 'companyName',
    header: '所属公司',
  },
  {
    accessorKey: 'departmentName',
    header: '所属部门',
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
          <Link to='/system/posts/$postId' params={{ postId: row.original.postId }} search={{}}>
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/posts/$postId/edit'
            params={{ postId: row.original.postId }}
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

// 公司表单页负责创建和编辑共用逻辑。
function CompanyFormPage({
  mode,
  companyId,
}: {
  mode: 'create' | 'edit'
  companyId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitAction, setSubmitAction] = useState<SubmitAction>('list')
  const isEdit = mode === 'edit'
  const form = useForm<CompanyFormValues>({
    resolver: zodResolver(companyFormSchema),
    defaultValues: {
      companyName: '',
      enabled: true,
    },
  })

  const detailQuery = useQuery({
    queryKey: ['system-company', companyId],
    queryFn: () => getCompanyDetail(companyId!),
    enabled: isEdit && Boolean(companyId),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset({
        companyName: detailQuery.data.companyName,
        enabled: detailQuery.data.enabled,
      })
    }
  }, [detailQuery.data, form])

  const createMutation = useMutation({
    mutationFn: createCompany,
    onError: () => undefined,
  })
  const updateMutation = useMutation({
    mutationFn: ({
      id,
      payload,
    }: {
      id: string
      payload: SaveCompanyPayload
    }) => updateCompany(id, payload),
    onError: () => undefined,
  })
  const companyName = useWatch({ control: form.control, name: 'companyName' })
  const enabled = useWatch({ control: form.control, name: 'enabled' })

  async function onSubmit(values: CompanyFormValues) {
    try {
      const result = isEdit
        ? await updateMutation.mutateAsync({ id: companyId!, payload: values })
        : await createMutation.mutateAsync(values)

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['system-companies'] }),
        queryClient.invalidateQueries({
          queryKey: ['system-company', result.companyId],
        }),
      ])

      if (submitAction === 'continue') {
        startTransition(() => {
          navigate({
            to: '/system/companies/$companyId/edit',
            params: { companyId: result.companyId },
            replace: isEdit,
          })
        })
        return
      }

      startTransition(() => {
        navigate({ to: '/system/companies/list' })
      })
    } catch (error) {
      const apiError = applyFieldErrors(
        form,
        error,
        ['companyName'],
        ['BIZ.COMPANY_NAME_DUPLICATED'],
        'companyName'
      )

      if (!apiError || (!apiError.fieldErrors?.length && apiError.code !== 'BIZ.COMPANY_NAME_DUPLICATED')) {
        handleServerError(error)
      }
    }
  }

  if (isEdit && detailQuery.isLoading) {
    return (
      <PageLoadingState
        title='编辑公司'
        description='正在加载公司详情，请稍候。'
      />
    )
  }

  if (isEdit && (detailQuery.isError || !detailQuery.data)) {
    return (
      <PageErrorState
        title='编辑公司'
        description='公司详情未能成功加载。'
        retry={() => void detailQuery.refetch()}
        listHref='/system/companies/list'
      />
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <PageShell
      title={isEdit ? '编辑公司' : '新建公司'}
      description='公司页面独立承载组织顶层实体维护，不和部门、岗位混合在同一个弹窗里处理。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/companies/list' search={{}}>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Form {...form}>
          <form
            id='company-form'
            onSubmit={form.handleSubmit(onSubmit)}
            className='flex flex-col gap-4'
          >
            <ProFormShell
              title='公司信息'
              description='维护公司名称和启用状态。'
              actions={
                <ProFormActions>
                  <Button
                    type='submit'
                    form='company-form'
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
                    form='company-form'
                    variant='outline'
                    disabled={isSubmitting}
                    onClick={() => setSubmitAction('continue')}
                  >
                    保存并继续编辑
                  </Button>
                </ProFormActions>
              }
            >
              <div className='grid gap-4'>
                <FormField
                  control={form.control}
                  name='companyName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>公司名称</FormLabel>
                      <FormControl>
                        <Input placeholder='请输入公司名称' {...field} />
                      </FormControl>
                      <FormDescription>
                        公司是组织、权限和业务数据边界的最上层实体。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='enabled'
                  render={({ field }) => (
                    <FormItem className='rounded-lg border p-4'>
                      <div className='flex items-center justify-between gap-4'>
                        <div className='grid gap-1'>
                          <FormLabel>启用状态</FormLabel>
                          <FormDescription>
                            停用后不会影响历史数据，但新组织实体不应继续挂靠到该公司。
                          </FormDescription>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </div>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </ProFormShell>
          </form>
        </Form>

        <div className='flex flex-col gap-4'>
          <Card>
            <CardHeader>
              <CardTitle>当前预览</CardTitle>
            </CardHeader>
            <CardContent className='flex flex-col gap-3'>
              <DetailMetric
                icon={Building2}
                label='公司名称'
                value={companyName || '待填写'}
              />
              <DetailMetric
                icon={BadgeCheck}
                label='状态'
                value={enabled ? '启用' : '停用'}
              />
            </CardContent>
          </Card>
        </div>
      </div>
    </PageShell>
  )
}

// 部门表单页负责创建和编辑共用逻辑。
function DepartmentFormPage({
  mode,
  departmentId,
}: {
  mode: 'create' | 'edit'
  departmentId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitAction, setSubmitAction] = useState<SubmitAction>('list')
  const isEdit = mode === 'edit'
  const form = useForm<DepartmentFormValues>({
    resolver: zodResolver(departmentFormSchema),
    defaultValues: {
      companyId: '',
      parentDepartmentId: null,
      departmentName: '',
      enabled: true,
    },
  })

  const detailQuery = useQuery({
    queryKey: ['system-department', departmentId],
    queryFn: () => getDepartmentDetail(departmentId!),
    enabled: isEdit && Boolean(departmentId),
  })
  const companyId = useWatch({ control: form.control, name: 'companyId' })
  const optionsQuery = useQuery({
    queryKey: ['system-department-form-options', companyId || 'all'],
    queryFn: () => getDepartmentFormOptions(companyId || undefined),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset({
        companyId: detailQuery.data.companyId,
        parentDepartmentId: detailQuery.data.parentDepartmentId,
        departmentName: detailQuery.data.departmentName,
        enabled: detailQuery.data.enabled,
      })
    }
  }, [detailQuery.data, form])

  const createMutation = useMutation({
    mutationFn: (values: DepartmentFormValues) =>
      createDepartment({
        companyId: values.companyId,
        parentDepartmentId: values.parentDepartmentId,
        departmentName: values.departmentName,
        enabled: values.enabled,
      }),
    onError: () => undefined,
  })
  const updateMutation = useMutation({
    mutationFn: ({
      id,
      values,
    }: {
      id: string
      values: DepartmentFormValues
    }) =>
      updateDepartment(id, {
        companyId: values.companyId,
        parentDepartmentId: values.parentDepartmentId,
        departmentName: values.departmentName,
        enabled: values.enabled,
      }),
    onError: () => undefined,
  })
  const departmentName = useWatch({
    control: form.control,
    name: 'departmentName',
  })
  const enabled = useWatch({ control: form.control, name: 'enabled' })
  const parentDepartmentId = useWatch({
    control: form.control,
    name: 'parentDepartmentId',
  })

  async function onSubmit(values: DepartmentFormValues) {
    try {
      const result = isEdit
        ? await updateMutation.mutateAsync({ id: departmentId!, values })
        : await createMutation.mutateAsync(values)

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['system-departments'] }),
        queryClient.invalidateQueries({
          queryKey: ['system-department', result.departmentId],
        }),
      ])

      if (submitAction === 'continue') {
        startTransition(() => {
          navigate({
            to: '/system/departments/$departmentId/edit',
            params: { departmentId: result.departmentId },
            replace: isEdit,
          })
        })
        return
      }

      startTransition(() => {
        navigate({ to: '/system/departments/list' })
      })
    } catch (error) {
      const apiError = applyFieldErrors(
        form,
        error,
        ['companyId', 'parentDepartmentId', 'departmentName'],
        ['BIZ.DEPARTMENT_NAME_DUPLICATED'],
        'departmentName'
      )

      if (!apiError || (!apiError.fieldErrors?.length && apiError.code !== 'BIZ.DEPARTMENT_NAME_DUPLICATED')) {
        handleServerError(error)
      }
    }
  }

  if (optionsQuery.isLoading || (isEdit && detailQuery.isLoading)) {
    return (
      <PageLoadingState
        title={isEdit ? '编辑部门' : '新建部门'}
        description='正在加载部门表单数据，请稍候。'
      />
    )
  }

  if (optionsQuery.isError || (isEdit && (detailQuery.isError || !detailQuery.data))) {
    return (
      <PageErrorState
        title={isEdit ? '编辑部门' : '新建部门'}
        description='部门页面所需的数据未能成功加载。'
        retry={() => {
          void optionsQuery.refetch()
          if (isEdit) {
            void detailQuery.refetch()
          }
        }}
        listHref='/system/departments/list'
      />
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const selectedCompany = optionsQuery.data?.companies.find(
    (company) => company.id === companyId
  )
  const parentOptions = (optionsQuery.data?.parentDepartments ?? []).filter(
    (item) => item.companyId === companyId && item.id !== departmentId
  )
  const selectedParentDepartment = parentOptions.find(
    (item) => item.id === parentDepartmentId
  )

  return (
    <PageShell
      title={isEdit ? '编辑部门' : '新建部门'}
      description='部门页独立承载组织层级维护，支持公司归属和父子部门关系配置。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/departments/list' search={{}}>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Form {...form}>
          <form
            id='department-form'
            onSubmit={form.handleSubmit(onSubmit)}
            className='flex flex-col gap-4'
          >
            <ProFormShell
              title='部门信息'
              description='维护部门名称、公司归属和树形父级。'
              actions={
                <ProFormActions>
                  <Button
                    type='submit'
                    form='department-form'
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
                    form='department-form'
                    variant='outline'
                    disabled={isSubmitting}
                    onClick={() => setSubmitAction('continue')}
                  >
                    保存并继续编辑
                  </Button>
                </ProFormActions>
              }
            >
              <div className='grid gap-4'>
                <FormField
                  control={form.control}
                  name='companyId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>所属公司</FormLabel>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <FormControl>
                          <SelectTrigger className='w-full'>
                            <SelectValue placeholder='请选择所属公司' />
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
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='parentDepartmentId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>上级部门</FormLabel>
                      <Select
                        value={field.value ?? '__root__'}
                        onValueChange={(value) =>
                          field.onChange(value === '__root__' ? null : value)
                        }
                      >
                        <FormControl>
                          <SelectTrigger className='w-full'>
                            <SelectValue placeholder='请选择上级部门' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectGroup>
                            <SelectLabel>部门层级</SelectLabel>
                            <SelectItem value='__root__'>无上级部门</SelectItem>
                            {parentOptions.map((item) => (
                              <SelectItem key={item.id} value={item.id}>
                                {item.name}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                      <FormDescription>
                        仅显示当前公司下可选的上级部门。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='departmentName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>部门名称</FormLabel>
                      <FormControl>
                        <Input placeholder='请输入部门名称' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='enabled'
                  render={({ field }) => (
                    <FormItem className='rounded-lg border p-4'>
                      <div className='flex items-center justify-between gap-4'>
                        <div className='grid gap-1'>
                          <FormLabel>启用状态</FormLabel>
                          <FormDescription>
                            停用后部门仍保留历史记录，但不再用于新的岗位挂靠。
                          </FormDescription>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </div>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </ProFormShell>
          </form>
        </Form>

        <div className='flex flex-col gap-4'>
          <Card>
            <CardHeader>
              <CardTitle>当前预览</CardTitle>
            </CardHeader>
            <CardContent className='flex flex-col gap-3'>
              <DetailMetric
                icon={Building2}
                label='所属公司'
                value={selectedCompany?.name || '待选择'}
              />
              <DetailMetric
                icon={GitBranch}
                label='上级部门'
                value={selectedParentDepartment?.name || '无上级部门'}
              />
              <DetailMetric
                icon={Building2}
                label='部门名称'
                value={departmentName || '待填写'}
              />
              <DetailMetric
                icon={BadgeCheck}
                label='状态'
                value={enabled ? '启用' : '停用'}
              />
            </CardContent>
          </Card>
        </div>
      </div>
    </PageShell>
  )
}

// 岗位表单页负责创建和编辑共用逻辑。
function PostFormPage({
  mode,
  postId,
}: {
  mode: 'create' | 'edit'
  postId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitAction, setSubmitAction] = useState<SubmitAction>('list')
  const isEdit = mode === 'edit'
  const form = useForm<PostFormValues>({
    resolver: zodResolver(postFormSchema),
    defaultValues: {
      companyId: '',
      departmentId: '',
      postName: '',
      enabled: true,
    },
  })

  const detailQuery = useQuery({
    queryKey: ['system-post', postId],
    queryFn: () => getPostDetail(postId!),
    enabled: isEdit && Boolean(postId),
  })
  const companyOptionsQuery = useQuery({
    queryKey: ['system-company-form-options'],
    queryFn: getCompanyFormOptions,
  })
  const companyId = useWatch({ control: form.control, name: 'companyId' })
  const postOptionsQuery = useQuery({
    queryKey: ['system-post-form-options', companyId || 'all'],
    queryFn: () => getPostFormOptions(companyId || undefined),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset({
        companyId: detailQuery.data.companyId,
        departmentId: detailQuery.data.departmentId,
        postName: detailQuery.data.postName,
        enabled: detailQuery.data.enabled,
      })
    }
  }, [detailQuery.data, form])

  const createMutation = useMutation({
    mutationFn: (values: PostFormValues) =>
      createPost({
        departmentId: values.departmentId,
        postName: values.postName,
        enabled: values.enabled,
      }),
    onError: () => undefined,
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, values }: { id: string; values: PostFormValues }) =>
      updatePost(id, {
        departmentId: values.departmentId,
        postName: values.postName,
        enabled: values.enabled,
      }),
    onError: () => undefined,
  })
  const departmentId = useWatch({ control: form.control, name: 'departmentId' })
  const postName = useWatch({ control: form.control, name: 'postName' })
  const enabled = useWatch({ control: form.control, name: 'enabled' })

  async function onSubmit(values: PostFormValues) {
    try {
      const result = isEdit
        ? await updateMutation.mutateAsync({ id: postId!, values })
        : await createMutation.mutateAsync(values)

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['system-posts'] }),
        queryClient.invalidateQueries({ queryKey: ['system-post', result.postId] }),
      ])

      if (submitAction === 'continue') {
        startTransition(() => {
          navigate({
            to: '/system/posts/$postId/edit',
            params: { postId: result.postId },
            replace: isEdit,
          })
        })
        return
      }

      startTransition(() => {
        navigate({ to: '/system/posts/list' })
      })
    } catch (error) {
      const apiError = applyFieldErrors(
        form,
        error,
        ['companyId', 'departmentId', 'postName'],
        ['BIZ.POST_NAME_DUPLICATED'],
        'postName'
      )

      if (!apiError || (!apiError.fieldErrors?.length && apiError.code !== 'BIZ.POST_NAME_DUPLICATED')) {
        handleServerError(error)
      }
    }
  }

  if (
    companyOptionsQuery.isLoading ||
    postOptionsQuery.isLoading ||
    (isEdit && detailQuery.isLoading)
  ) {
    return (
      <PageLoadingState
        title={isEdit ? '编辑岗位' : '新建岗位'}
        description='正在加载岗位表单数据，请稍候。'
      />
    )
  }

  if (
    companyOptionsQuery.isError ||
    postOptionsQuery.isError ||
    (isEdit && (detailQuery.isError || !detailQuery.data))
  ) {
    return (
      <PageErrorState
        title={isEdit ? '编辑岗位' : '新建岗位'}
        description='岗位页面所需的数据未能成功加载。'
        retry={() => {
          void companyOptionsQuery.refetch()
          void postOptionsQuery.refetch()
          if (isEdit) {
            void detailQuery.refetch()
          }
        }}
        listHref='/system/posts/list'
      />
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const departments = (postOptionsQuery.data?.departments ?? []).filter(
    (item) => item.companyId === companyId
  )
  const selectedCompany = companyOptionsQuery.data?.companies.find(
    (company) => company.id === companyId
  )
  const selectedDepartment = departments.find((item) => item.id === departmentId)

  return (
    <PageShell
      title={isEdit ? '编辑岗位' : '新建岗位'}
      description='岗位页独立承载岗位与部门挂靠配置，后续审批节点默认会基于岗位查找处理人。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/posts/list' search={{}}>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Form {...form}>
          <form
            id='post-form'
            onSubmit={form.handleSubmit(onSubmit)}
            className='flex flex-col gap-4'
          >
            <ProFormShell
              title='岗位信息'
              description='维护岗位和部门挂靠关系。'
              actions={
                <ProFormActions>
                  <Button
                    type='submit'
                    form='post-form'
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
                    form='post-form'
                    variant='outline'
                    disabled={isSubmitting}
                    onClick={() => setSubmitAction('continue')}
                  >
                    保存并继续编辑
                  </Button>
                </ProFormActions>
              }
            >
              <div className='grid gap-4'>
                <FormField
                  control={form.control}
                  name='companyId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>所属公司</FormLabel>
                      <Select
                        value={field.value}
                        onValueChange={(value) => {
                          field.onChange(value)
                          form.setValue('departmentId', '')
                        }}
                      >
                        <FormControl>
                          <SelectTrigger className='w-full'>
                            <SelectValue placeholder='请选择所属公司' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectGroup>
                            <SelectLabel>公司列表</SelectLabel>
                            {companyOptionsQuery.data?.companies.map((company) => (
                              <SelectItem key={company.id} value={company.id}>
                                {company.name}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
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
                      <Select value={field.value} onValueChange={field.onChange}>
                        <FormControl>
                          <SelectTrigger className='w-full'>
                            <SelectValue placeholder='请选择所属部门' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectGroup>
                            <SelectLabel>部门列表</SelectLabel>
                            {departments.map((department) => (
                              <SelectItem key={department.id} value={department.id}>
                                {department.name}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='postName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>岗位名称</FormLabel>
                      <FormControl>
                        <Input placeholder='请输入岗位名称' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='enabled'
                  render={({ field }) => (
                    <FormItem className='rounded-lg border p-4'>
                      <div className='flex items-center justify-between gap-4'>
                        <div className='grid gap-1'>
                          <FormLabel>启用状态</FormLabel>
                          <FormDescription>
                            停用岗位后，新的审批指派不应再引用该岗位。
                          </FormDescription>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </div>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </ProFormShell>
          </form>
        </Form>

        <div className='flex flex-col gap-4'>
          <Card>
            <CardHeader>
              <CardTitle>当前预览</CardTitle>
            </CardHeader>
            <CardContent className='flex flex-col gap-3'>
              <DetailMetric
                icon={Building2}
                label='所属公司'
                value={selectedCompany?.name || '待选择'}
              />
              <DetailMetric
                icon={GitBranch}
                label='所属部门'
                value={selectedDepartment?.name || '待选择'}
              />
              <DetailMetric
                icon={BriefcaseBusiness}
                label='岗位名称'
                value={postName || '待填写'}
              />
              <DetailMetric
                icon={BadgeCheck}
                label='状态'
                value={enabled ? '启用' : '停用'}
              />
            </CardContent>
          </Card>
        </div>
      </div>
    </PageShell>
  )
}

export function RolesListPage() {
  const search = normalizeListQuerySearch(rolesRoute.useSearch())
  const navigate = rolesRoute.useNavigate()

  return (
    <ProTable
      title='角色列表'
      description='角色模块列表页展示基础角色信息和状态。'
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
      onRefresh={() => void 0}
      onExport={() => void 0}
      createActionNode={null}
    />
  )
}

export function CompaniesListPage() {
  const search = normalizeListQuerySearch(companiesRoute.useSearch())
  const navigate = companiesRoute.useNavigate()
  const queryClient = useQueryClient()
  const [pendingDeleteRows, setPendingDeleteRows] = useState<CompanyRow[]>([])
  const clearSelectionRef = useRef<(() => void) | null>(null)
  const query = useQuery({
    queryKey: ['system-companies', search],
    queryFn: () => listCompanies(search),
    placeholderData: (previous) => previous,
  })
  const deleteMutation = useMutation({
    mutationFn: async (companyIds: string[]) => {
      await Promise.all(companyIds.map((companyId) => deleteCompany(companyId)))
    },
    onSuccess: async (_, companyIds) => {
      toast.success(
        companyIds.length > 1
          ? `已删除 ${companyIds.length} 个公司`
          : '公司已删除'
      )
      setPendingDeleteRows([])
      clearSelectionRef.current?.()
      clearSelectionRef.current = null
      await queryClient.invalidateQueries({ queryKey: ['system-companies'] })
      await query.refetch()
    },
    onError: handleServerError,
  })

  const rows = useMemo<CompanyRow[]>(
    () =>
      (query.data?.records ?? []).map((record) => ({
        companyId: record.companyId,
        companyName: record.companyName,
        status: resolveStatusLabel(record.status),
        createdAt: formatDateTime(record.createdAt),
      })),
    [query.data?.records]
  )
  const createActionNode = (
    <Button asChild>
      <Link to='/system/companies/create' search={{}}>
        <Save data-icon='inline-start' />
        新建公司
      </Link>
    </Button>
  )

  const summaries = useMemo(() => {
    const records = query.data?.records ?? []
    const enabledCount = records.filter((item) => item.status === 'ENABLED').length

    return [
      {
        label: '公司总量',
        value: `${query.data?.total ?? 0}`,
        hint: '公司列表已接入真实分页接口，可直接维护组织顶层实体。',
      },
      {
        label: '当前页启用',
        value: `${enabledCount}`,
        hint: '用于快速确认当前组织体系的可用公司范围。',
      },
      {
        label: '当前页停用',
        value: `${records.length - enabledCount}`,
        hint: '停用公司保留历史数据，但不再作为新组织挂靠目标。',
      },
    ]
  }, [query.data])
  const groupOptions = useMemo(
    () => [
      {
        field: 'status',
        label: '状态',
        getValue: (row: CompanyRow) => row.status,
      },
    ],
    []
  )

  return (
    <>
      <ProTable<CompanyRow>
        title='公司列表'
        description='公司列表页承载组织顶层实体维护，已接入真实分页接口和独立详情/编辑页。'
        searchPlaceholder='搜索公司名称'
        search={search}
        navigate={navigate}
        columns={buildCompanyColumns((row) => setPendingDeleteRows([row]))}
        data={rows}
        total={query.data?.total}
        summaries={summaries}
        createActionNode={createActionNode}
        onRefresh={() => void query.refetch()}
        onExport={(scope, exportRows) => {
          if (scope === 'filtered-results') {
            void exportAllCompaniesCsv(search).catch((error) => {
              handleServerError(error)
            })
            return
          }
          exportCompaniesCsv(exportRows)
        }}
        onImport={(file) => {
          void importCompaniesCsv(file)
            .then((summary) => {
              toast.success(
                `公司导入完成，新增 ${summary.created} 条，跳过 ${summary.skipped} 条。`
              )
              return query.refetch()
            })
            .catch((error) => {
              handleServerError(error)
            })
        }}
        enableRowSelection
        getRowId={(row) => row.companyId}
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
        title={pendingDeleteRows.length > 1 ? '批量删除公司' : '删除公司'}
        desc={
          pendingDeleteRows.length > 1
            ? `确认删除已选中的 ${pendingDeleteRows.length} 个公司吗？删除后无法恢复。`
            : `确认删除公司「${pendingDeleteRows[0]?.companyName ?? ''}」吗？删除后无法恢复。`
        }
        destructive
        isLoading={deleteMutation.isPending}
        confirmText={deleteMutation.isPending ? '删除中…' : '确认删除'}
        handleConfirm={() =>
          void deleteMutation.mutate(
            pendingDeleteRows.map((row) => row.companyId)
          )
        }
      />
    </>
  )
}

export function DepartmentsListPage() {
  const search = normalizeListQuerySearch(departmentsRoute.useSearch())
  const navigate = departmentsRoute.useNavigate()
  const queryClient = useQueryClient()
  const [selectedDepartment, setSelectedDepartment] = useState<DepartmentTreeNode | null>(
    null
  )
  const [pendingDeleteRows, setPendingDeleteRows] = useState<DepartmentTreeNode[]>([])
  const clearSelectionRef = useRef<(() => void) | null>(null)
  const query = useQuery({
    queryKey: ['system-departments', 'tree'],
    queryFn: getDepartmentTree,
    placeholderData: (previous) => previous,
  })
  const usersQuery = useQuery({
    queryKey: ['system-department-users', selectedDepartment?.departmentId],
    queryFn: () => getDepartmentUsers(selectedDepartment!.departmentId),
    enabled: Boolean(selectedDepartment?.departmentId),
  })
  const deleteMutation = useMutation({
    mutationFn: async (departmentIds: string[]) => {
      await Promise.all(
        departmentIds.map((departmentId) => deleteDepartment(departmentId))
      )
    },
    onSuccess: async (_, departmentIds) => {
      toast.success(
        departmentIds.length > 1
          ? `已删除 ${departmentIds.length} 个部门`
          : '部门已删除'
      )
      setPendingDeleteRows([])
      clearSelectionRef.current?.()
      clearSelectionRef.current = null
      await queryClient.invalidateQueries({ queryKey: ['system-departments'] })
      await query.refetch()
    },
    onError: handleServerError,
  })

  const rows = useMemo(() => query.data ?? [], [query.data])
  const createActionNode = (
    <Button asChild>
      <Link to='/system/departments/create' search={{}}>
        <Save data-icon='inline-start' />
        新建部门
      </Link>
    </Button>
  )

  const summaries = useMemo(() => {
    const records = query.data ?? []
    const flatRecords = flattenDepartmentTree(records)
    return [
      {
        label: '部门总量',
        value: `${countDepartmentNodes(records)}`,
        hint: '部门列表已接入树形接口，支持展开/折叠查看层级结构。',
      },
      {
        label: '一级部门',
        value: `${records.length}`,
        hint: '无上级部门的节点通常对应组织树的第一层。',
      },
      {
        label: '当前页公司数',
        value: `${new Set(flatRecords.map((item) => item.companyName)).size}`,
        hint: '用于快速核对部门在公司层面的覆盖范围。',
      },
    ]
  }, [query.data])
  const groupOptions = useMemo(
    () => [
      {
        field: 'companyName',
        label: '公司',
        getValue: (row: DepartmentTreeNode) => row.companyName,
      },
      {
        field: 'status',
        label: '状态',
        getValue: (row: DepartmentTreeNode) =>
          resolveStatusLabel(row.status),
      },
    ],
    []
  )

  return (
    <>
      <ProTable<DepartmentTreeNode>
        title='部门列表'
        description='部门列表页已接入树形接口，支持展开/折叠、独立创建、详情和编辑页面。'
        searchPlaceholder='搜索部门名称、所属公司或上级部门'
        search={search}
        navigate={navigate}
        columns={buildDepartmentColumns(setSelectedDepartment, (row) => setPendingDeleteRows([row]))}
        data={rows}
        total={countDepartmentNodes(rows)}
        summaries={summaries}
        createActionNode={createActionNode}
        onRefresh={() => void query.refetch()}
        onExport={(_scope, exportRows) => {
          exportDepartmentsCsv(exportRows)
        }}
        onImport={(file) => {
          void importDepartmentsCsv(file)
            .then((summary) => {
              toast.success(
                `部门导入完成，新增 ${summary.created} 条，跳过 ${summary.skipped} 条。`
              )
              return query.refetch()
            })
            .catch((error) => {
              handleServerError(error)
            })
        }}
        enableRowSelection
        getRowId={(row) => row.departmentId}
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
        getSubRows={(row) => row.children}
      />
      <ConfirmDialog
        open={pendingDeleteRows.length > 0}
        onOpenChange={(open) => {
          if (!open && !deleteMutation.isPending) {
            setPendingDeleteRows([])
            clearSelectionRef.current = null
          }
        }}
        title={pendingDeleteRows.length > 1 ? '批量删除部门' : '删除部门'}
        desc={
          pendingDeleteRows.length > 1
            ? `确认删除已选中的 ${pendingDeleteRows.length} 个部门吗？删除后无法恢复。`
            : `确认删除部门「${pendingDeleteRows[0]?.departmentName ?? ''}」吗？删除后无法恢复。`
        }
        destructive
        isLoading={deleteMutation.isPending}
        confirmText={deleteMutation.isPending ? '删除中…' : '确认删除'}
        handleConfirm={() =>
          void deleteMutation.mutate(
            pendingDeleteRows.map((row) => row.departmentId)
          )
        }
      />
      <AssociatedUsersDialog
        open={Boolean(selectedDepartment)}
        title='部门关联用户'
        description={
          selectedDepartment
            ? `查看部门「${selectedDepartment.departmentName}」当前关联的用户。`
            : '查看当前部门关联的用户。'
        }
        users={usersQuery.data}
        isLoading={usersQuery.isLoading}
        isError={usersQuery.isError}
        onRetry={() => void usersQuery.refetch()}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedDepartment(null)
          }
        }}
      />
    </>
  )
}

export function PostsListPage() {
  const search = normalizeListQuerySearch(postsRoute.useSearch())
  const navigate = postsRoute.useNavigate()
  const queryClient = useQueryClient()
  const [selectedPost, setSelectedPost] = useState<PostRow | null>(null)
  const [pendingDeleteRows, setPendingDeleteRows] = useState<PostRow[]>([])
  const clearSelectionRef = useRef<(() => void) | null>(null)
  const query = useQuery({
    queryKey: ['system-posts', search],
    queryFn: () => listPosts(search),
    placeholderData: (previous) => previous,
  })
  const departmentTreeQuery = useQuery({
    queryKey: ['system-departments', 'tree', 'for-post-export'],
    queryFn: getDepartmentTree,
    placeholderData: (previous) => previous,
  })
  const usersQuery = useQuery({
    queryKey: ['system-post-users', selectedPost?.postId],
    queryFn: () => getPostUsers(selectedPost!.postId),
    enabled: Boolean(selectedPost?.postId),
  })
  const deleteMutation = useMutation({
    mutationFn: async (postIds: string[]) => {
      await Promise.all(postIds.map((postId) => deletePost(postId)))
    },
    onSuccess: async (_, postIds) => {
      toast.success(
        postIds.length > 1 ? `已删除 ${postIds.length} 个岗位` : '岗位已删除'
      )
      setPendingDeleteRows([])
      clearSelectionRef.current?.()
      clearSelectionRef.current = null
      await queryClient.invalidateQueries({ queryKey: ['system-posts'] })
      await query.refetch()
    },
    onError: handleServerError,
  })

  const rows = useMemo<PostRow[]>(
    () =>
      (query.data?.records ?? []).map((record) => ({
        postId: record.postId,
        companyName: record.companyName || '-',
        departmentName: record.departmentName || '-',
        postName: record.postName,
        status: resolveStatusLabel(record.status),
        createdAt: formatDateTime(record.createdAt),
      })),
    [query.data?.records]
  )
  const createActionNode = (
    <Button asChild>
      <Link to='/system/posts/create' search={{}}>
        <Save data-icon='inline-start' />
        新建岗位
      </Link>
    </Button>
  )

  const summaries = useMemo(() => {
    const records = query.data?.records ?? []
    return [
      {
        label: '岗位总量',
        value: `${query.data?.total ?? 0}`,
        hint: '岗位列表已接通真实接口，可直接维护组织岗位配置。',
      },
      {
        label: '当前页部门数',
        value: `${new Set(records.map((item) => item.departmentName)).size}`,
        hint: '岗位应明确挂靠到部门，避免流程指派出现孤立岗位。',
      },
      {
        label: '当前页启用',
        value: `${records.filter((item) => item.status === 'ENABLED').length}`,
        hint: '启用岗位会继续参与流程节点候选计算。',
      },
    ]
  }, [query.data])
  const groupOptions = useMemo(
    () => [
      {
        field: 'status',
        label: '状态',
        getValue: (row: PostRow) => row.status,
      },
      {
        field: 'companyName',
        label: '公司',
        getValue: (row: PostRow) => row.companyName,
      },
      {
        field: 'departmentName',
        label: '部门',
        getValue: (row: PostRow) => row.departmentName,
      },
    ],
    []
  )

  return (
    <>
      <ProTable<PostRow>
        title='岗位列表'
        description='岗位列表页已接通真实接口，承接岗位维护和与审批节点的后续映射关系。'
        searchPlaceholder='搜索岗位名称、所属部门或所属公司'
        search={search}
        navigate={navigate}
        columns={buildPostColumns(setSelectedPost, (row) => setPendingDeleteRows([row]))}
        data={rows}
        total={query.data?.total}
      summaries={summaries}
      createActionNode={createActionNode}
      onRefresh={() => void query.refetch()}
      onExport={(scope, exportRows) => {
        if (scope === 'filtered-results') {
          void exportAllPostsCsv(search, departmentTreeQuery.data ?? []).catch((error) => {
            handleServerError(error)
          })
          return
        }
        exportPostsCsv(exportRows, departmentTreeQuery.data ?? [])
      }}
        onImport={(file) => {
          void importPostsCsv(file)
          .then((summary) => {
            toast.success(
              `岗位导入完成，新增 ${summary.created} 条，跳过 ${summary.skipped} 条。`
            )
            return query.refetch()
          })
          .catch((error) => {
            handleServerError(error)
          })
        }}
        enableRowSelection
        getRowId={(row) => row.postId}
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
        title={pendingDeleteRows.length > 1 ? '批量删除岗位' : '删除岗位'}
        desc={
          pendingDeleteRows.length > 1
            ? `确认删除已选中的 ${pendingDeleteRows.length} 个岗位吗？删除后无法恢复。`
            : `确认删除岗位「${pendingDeleteRows[0]?.postName ?? ''}」吗？删除后无法恢复。`
        }
        destructive
        isLoading={deleteMutation.isPending}
        confirmText={deleteMutation.isPending ? '删除中…' : '确认删除'}
        handleConfirm={() =>
          void deleteMutation.mutate(
            pendingDeleteRows.map((row) => row.postId)
          )
        }
      />
      <AssociatedUsersDialog
        open={Boolean(selectedPost)}
        title='岗位关联用户'
        description={
          selectedPost
            ? `查看岗位「${selectedPost.postName}」当前关联的用户。`
            : '查看当前岗位关联的用户。'
        }
        users={usersQuery.data}
        isLoading={usersQuery.isLoading}
        isError={usersQuery.isError}
        onRetry={() => void usersQuery.refetch()}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedPost(null)
          }
        }}
      />
    </>
  )
}

// 公司新建页复用同一套表单容器。
export function CompanyCreatePage() {
  return <CompanyFormPage mode='create' />
}

// 公司编辑页只负责接收路由参数并下沉给表单。
export function CompanyEditPage({ companyId }: { companyId: string }) {
  return <CompanyFormPage mode='edit' companyId={companyId} />
}

// 公司详情页复用通用详情布局。
export function CompanyDetailPage({ companyId }: { companyId: string }) {
  const query = useQuery({
    queryKey: ['system-company', companyId],
    queryFn: () => getCompanyDetail(companyId),
  })

  if (query.isLoading) {
    return (
      <PageLoadingState
        title='公司详情'
        description='正在加载公司详情，请稍候。'
      />
    )
  }
  if (query.isError || !query.data) {
    return (
      <PageErrorState
        title='公司详情'
        description='公司详情数据未能成功加载。'
        retry={() => void query.refetch()}
        listHref='/system/companies/list'
      />
    )
  }

  return (
    <PageShell
      title='公司详情'
      description='公司详情页独立展示基础信息和当前状态。'
      actions={
        <>
          <Button asChild>
            <Link
              to='/system/companies/$companyId/edit'
              params={{ companyId: query.data.companyId }}
            >
              <Save data-icon='inline-start' />
              编辑公司
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/companies/list' search={{}}>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='flex flex-wrap gap-2'>
        <Badge variant='secondary'>{query.data.enabled ? '启用中' : '已停用'}</Badge>
      </div>
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>基础信息</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            <DetailMetric
              icon={Building2}
              label='公司名称'
              value={query.data.companyName}
            />
            <DetailMetric
              icon={BadgeCheck}
              label='当前状态'
              value={query.data.enabled ? '启用' : '停用'}
            />
          </CardContent>
        </Card>
        <Alert>
          <ShieldCheck />
          <AlertTitle>页面定位</AlertTitle>
          <AlertDescription>公司详情页为独立页面。</AlertDescription>
        </Alert>
      </div>
    </PageShell>
  )
}

// 部门新建页复用同一套表单容器。
export function DepartmentCreatePage() {
  return <DepartmentFormPage mode='create' />
}

// 部门编辑页只负责接收路由参数并下沉给表单。
export function DepartmentEditPage({
  departmentId,
}: {
  departmentId: string
}) {
  return <DepartmentFormPage mode='edit' departmentId={departmentId} />
}

// 部门详情页复用通用详情布局。
export function DepartmentDetailPage({
  departmentId,
}: {
  departmentId: string
}) {
  const query = useQuery({
    queryKey: ['system-department', departmentId],
    queryFn: () => getDepartmentDetail(departmentId),
  })

  if (query.isLoading) {
    return (
      <PageLoadingState
        title='部门详情'
        description='正在加载部门详情，请稍候。'
      />
    )
  }
  if (query.isError || !query.data) {
    return (
      <PageErrorState
        title='部门详情'
        description='部门详情数据未能成功加载。'
        retry={() => void query.refetch()}
        listHref='/system/departments/list'
      />
    )
  }

  return (
    <PageShell
      title='部门详情'
      description='部门详情页独立展示公司归属、树形父级和当前状态。'
      actions={
        <>
          <Button asChild>
            <Link
              to='/system/departments/$departmentId/edit'
              params={{ departmentId: query.data.departmentId }}
            >
              <Save data-icon='inline-start' />
              编辑部门
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/departments/list' search={{}}>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='flex flex-wrap gap-2'>
        <Badge variant='secondary'>{query.data.companyName}</Badge>
        <Badge variant='secondary'>
          {query.data.enabled ? '启用中' : '已停用'}
        </Badge>
      </div>
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>基础信息</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            <DetailMetric
              icon={Building2}
              label='所属公司'
              value={query.data.companyName}
            />
            <DetailMetric
              icon={GitBranch}
              label='上级部门'
              value={query.data.parentDepartmentName || '无上级部门'}
            />
            <DetailMetric
              icon={Building2}
              label='部门名称'
              value={query.data.departmentName}
            />
            <DetailMetric
              icon={BadgeCheck}
              label='当前状态'
              value={query.data.enabled ? '启用' : '停用'}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>标识信息</CardTitle>
          </CardHeader>
          <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
            <p>部门 ID：{query.data.departmentId}</p>
            <p>公司 ID：{query.data.companyId}</p>
            <p>上级部门 ID：{query.data.parentDepartmentId || '-'}</p>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

// 岗位新建页复用同一套表单容器。
export function PostCreatePage() {
  return <PostFormPage mode='create' />
}

// 岗位编辑页只负责接收路由参数并下沉给表单。
export function PostEditPage({ postId }: { postId: string }) {
  return <PostFormPage mode='edit' postId={postId} />
}

// 岗位详情页复用通用详情布局。
export function PostDetailPage({ postId }: { postId: string }) {
  const query = useQuery({
    queryKey: ['system-post', postId],
    queryFn: () => getPostDetail(postId),
  })

  if (query.isLoading) {
    return (
      <PageLoadingState
        title='岗位详情'
        description='正在加载岗位详情，请稍候。'
      />
    )
  }
  if (query.isError || !query.data) {
    return (
      <PageErrorState
        title='岗位详情'
        description='岗位详情数据未能成功加载。'
        retry={() => void query.refetch()}
        listHref='/system/posts/list'
      />
    )
  }

  return (
    <PageShell
      title='岗位详情'
      description='岗位详情页独立展示公司、部门和岗位基础信息。'
      actions={
        <>
          <Button asChild>
            <Link
              to='/system/posts/$postId/edit'
              params={{ postId: query.data.postId }}
            >
              <Save data-icon='inline-start' />
              编辑岗位
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/posts/list' search={{}}>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='flex flex-wrap gap-2'>
        <Badge variant='secondary'>{query.data.companyName}</Badge>
        <Badge variant='secondary'>{query.data.departmentName}</Badge>
        <Badge variant='secondary'>{query.data.enabled ? '启用中' : '已停用'}</Badge>
      </div>
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>基础信息</CardTitle>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            <DetailMetric
              icon={Building2}
              label='所属公司'
              value={query.data.companyName}
            />
            <DetailMetric
              icon={GitBranch}
              label='所属部门'
              value={query.data.departmentName}
            />
            <DetailMetric
              icon={BriefcaseBusiness}
              label='岗位名称'
              value={query.data.postName}
            />
            <DetailMetric
              icon={BadgeCheck}
              label='当前状态'
              value={query.data.enabled ? '启用' : '停用'}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>标识信息</CardTitle>
          </CardHeader>
          <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
            <p>岗位 ID：{query.data.postId}</p>
            <p>公司 ID：{query.data.companyId}</p>
            <p>部门 ID：{query.data.departmentId}</p>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}
