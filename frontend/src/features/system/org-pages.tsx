import { startTransition, useEffect, useMemo, useState } from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch, type Path, type UseFormReturn } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  BriefcaseBusiness,
  Building2,
  GitBranch,
  Loader2,
  Save,
  ShieldCheck,
} from 'lucide-react'
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
  getCompanyDetail,
  getCompanyFormOptions,
  getDepartmentDetail,
  getDepartmentFormOptions,
  getPostDetail,
  getPostFormOptions,
  listCompanies,
  listDepartments,
  listPosts,
  updateCompany,
  updateDepartment,
  updatePost,
  type SaveCompanyPayload,
} from '@/lib/api/system-org'
import { handleServerError } from '@/lib/handle-server-error'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'

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

type DepartmentRow = {
  departmentId: string
  companyName: string
  parentDepartmentName: string
  departmentName: string
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

function resolveStatusLabel(status: string) {
  return status === 'ENABLED' ? '启用' : '停用'
}

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

const companyColumns: ColumnDef<CompanyRow>[] = [
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
      </div>
    ),
  },
]

const departmentColumns: ColumnDef<DepartmentRow>[] = [
  {
    accessorKey: 'departmentName',
    header: '部门名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.departmentName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.departmentId}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'companyName',
    header: '所属公司',
  },
  {
    accessorKey: 'parentDepartmentName',
    header: '上级部门',
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
      </div>
    ),
  },
]

const postColumns: ColumnDef<PostRow>[] = [
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
      </div>
    ),
  },
]

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
        <>
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
          <Button asChild variant='ghost'>
            <Link to='/system/companies/list' search={{}}>
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
            id='company-form'
            onSubmit={form.handleSubmit(onSubmit)}
            className='flex flex-col gap-4'
          >
            <Card>
              <CardHeader>
                <CardTitle>公司信息</CardTitle>
                <CardDescription>
                  当前基线先维护公司名称和启用状态，后续会继续扩展企业编码、法人和租户隔离配置。
                </CardDescription>
              </CardHeader>
              <CardContent className='grid gap-4'>
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
              </CardContent>
            </Card>
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
          <Alert>
            <ShieldCheck />
            <AlertTitle>页面规则</AlertTitle>
            <AlertDescription>
              公司维护页为独立 CRUD 页面，不使用弹窗或多页签替代。
            </AlertDescription>
          </Alert>
        </div>
      </div>
    </PageShell>
  )
}

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
        <>
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
          <Button asChild variant='ghost'>
            <Link to='/system/departments/list' search={{}}>
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
            id='department-form'
            onSubmit={form.handleSubmit(onSubmit)}
            className='flex flex-col gap-4'
          >
            <Card>
              <CardHeader>
                <CardTitle>部门信息</CardTitle>
                <CardDescription>
                  维护部门名称、公司归属和树形父级。后续可在这里继续扩展负责人和数据范围。
                </CardDescription>
              </CardHeader>
              <CardContent className='grid gap-4'>
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
              </CardContent>
            </Card>
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
        <>
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
          <Button asChild variant='ghost'>
            <Link to='/system/posts/list' search={{}}>
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
            id='post-form'
            onSubmit={form.handleSubmit(onSubmit)}
            className='flex flex-col gap-4'
          >
            <Card>
              <CardHeader>
                <CardTitle>岗位信息</CardTitle>
                <CardDescription>
                  当前基线维护岗位和部门挂靠。后续会继续扩展岗位职责、审批策略和兼任限制。
                </CardDescription>
              </CardHeader>
              <CardContent className='grid gap-4'>
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
              </CardContent>
            </Card>
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
  const search = rolesRoute.useSearch()
  const navigate = rolesRoute.useNavigate()

  return (
    <ResourceListPage
      title='角色列表'
      description='角色模块下一阶段会继续接真实接口。本轮优先完成组织管理闭环，因此暂保留角色列表基线。'
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

export function CompaniesListPage() {
  const search = companiesRoute.useSearch()
  const navigate = companiesRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-companies', search],
    queryFn: () => listCompanies(search),
    placeholderData: (previous) => previous,
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

  const summaries = useMemo(() => {
    const records = query.data?.records ?? []
    const enabledCount = records.filter((item) => item.status === 'ENABLED').length

    return [
      {
        label: '公司总量',
        value: `${query.data?.total ?? 0}`,
        hint: '公司列表已接入真实分页接口，可继续扩展租户和业务域隔离。',
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

  return (
    <ResourceListPage<CompanyRow>
      title='公司列表'
      description='公司列表页承载组织顶层实体维护，已接入真实分页接口和独立详情/编辑页。'
      endpoint='/api/v1/system/companies/page'
      searchPlaceholder='搜索公司名称'
      search={search}
      navigate={navigate}
      columns={companyColumns}
      data={rows}
      total={query.data?.total}
      summaries={summaries}
      createAction={{ label: '新建公司', href: '/system/companies/create' }}
    />
  )
}

export function DepartmentsListPage() {
  const search = departmentsRoute.useSearch()
  const navigate = departmentsRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-departments', search],
    queryFn: () => listDepartments(search),
    placeholderData: (previous) => previous,
  })

  const rows = useMemo<DepartmentRow[]>(
    () =>
      (query.data?.records ?? []).map((record) => ({
        departmentId: record.departmentId,
        companyName: record.companyName || '-',
        parentDepartmentName: record.parentDepartmentName || '无上级部门',
        departmentName: record.departmentName,
        status: resolveStatusLabel(record.status),
        createdAt: formatDateTime(record.createdAt),
      })),
    [query.data?.records]
  )

  const summaries = useMemo(() => {
    const records = query.data?.records ?? []
    const topLevelCount = records.filter(
      (item) => !item.parentDepartmentName
    ).length
    return [
      {
        label: '部门总量',
        value: `${query.data?.total ?? 0}`,
        hint: '部门列表已接入真实分页接口，可继续扩展树形视图和数据范围。',
      },
      {
        label: '当前页一级部门',
        value: `${topLevelCount}`,
        hint: '无上级部门的节点通常对应组织树的第一层。',
      },
      {
        label: '当前页公司数',
        value: `${new Set(records.map((item) => item.companyName)).size}`,
        hint: '用于快速核对部门在公司层面的覆盖范围。',
      },
    ]
  }, [query.data])

  return (
    <ResourceListPage<DepartmentRow>
      title='部门列表'
      description='部门列表页已接通真实接口，支持独立创建、详情和编辑页面，后续继续扩展组织树能力。'
      endpoint='/api/v1/system/departments/page'
      searchPlaceholder='搜索部门名称或所属公司'
      search={search}
      navigate={navigate}
      columns={departmentColumns}
      data={rows}
      total={query.data?.total}
      summaries={summaries}
      createAction={{ label: '新建部门', href: '/system/departments/create' }}
    />
  )
}

export function PostsListPage() {
  const search = postsRoute.useSearch()
  const navigate = postsRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-posts', search],
    queryFn: () => listPosts(search),
    placeholderData: (previous) => previous,
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

  const summaries = useMemo(() => {
    const records = query.data?.records ?? []
    return [
      {
        label: '岗位总量',
        value: `${query.data?.total ?? 0}`,
        hint: '岗位列表已接通真实接口，可继续扩展兼任岗位和审批职责绑定。',
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

  return (
    <ResourceListPage<PostRow>
      title='岗位列表'
      description='岗位列表页已接通真实接口，承接岗位维护和与审批节点的后续映射关系。'
      endpoint='/api/v1/system/posts/page'
      searchPlaceholder='搜索岗位名称、所属部门或所属公司'
      search={search}
      navigate={navigate}
      columns={postColumns}
      data={rows}
      total={query.data?.total}
      summaries={summaries}
      createAction={{ label: '新建岗位', href: '/system/posts/create' }}
    />
  )
}

export function CompanyCreatePage() {
  return <CompanyFormPage mode='create' />
}

export function CompanyEditPage({ companyId }: { companyId: string }) {
  return <CompanyFormPage mode='edit' companyId={companyId} />
}

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
      description='公司详情页独立展示基础信息和当前状态，便于后续扩展租户配置和业务边界。'
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
          <AlertDescription>
            公司详情页为独立页面，后续会继续承载公司级业务配置与 AI 功能授权边界。
          </AlertDescription>
        </Alert>
      </div>
    </PageShell>
  )
}

export function DepartmentCreatePage() {
  return <DepartmentFormPage mode='create' />
}

export function DepartmentEditPage({
  departmentId,
}: {
  departmentId: string
}) {
  return <DepartmentFormPage mode='edit' departmentId={departmentId} />
}

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

export function PostCreatePage() {
  return <PostFormPage mode='create' />
}

export function PostEditPage({ postId }: { postId: string }) {
  return <PostFormPage mode='edit' postId={postId} />
}

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
      description='岗位详情页独立展示公司、部门和岗位基础信息，后续继续扩展审批职责与委派策略。'
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
