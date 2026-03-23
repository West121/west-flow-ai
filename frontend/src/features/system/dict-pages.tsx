import { startTransition, useEffect, useMemo, useState, type ReactNode } from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch, type UseFormReturn } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  Calendar,
  Hash,
  Layers3,
  ListTree,
  Loader2,
  Save,
  Tag,
} from 'lucide-react'
import { toast } from 'sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
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
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import { getApiErrorResponse } from '@/lib/api/client'
import {
  createSystemDictItem,
  createSystemDictType,
  getSystemDictItemDetail,
  getSystemDictItemFormOptions,
  getSystemDictTypeDetail,
  getSystemDictTypeFormOptions,
  listSystemDictItems,
  listSystemDictTypes,
  updateSystemDictItem,
  updateSystemDictType,
  type SaveSystemDictItemPayload,
  type SaveSystemDictTypePayload,
  type SystemDictItemDetail,
  type SystemDictItemDictTypeOption,
  type SystemDictItemFormOptions,
  type SystemDictItemPageResponse,
  type SystemDictItemRecord,
  type SystemDictStatus,
  type SystemDictTypeDetail,
  type SystemDictTypeFormOptions,
  type SystemDictTypePageResponse,
  type SystemDictTypeRecord,
} from '@/lib/api/system-dicts'
import { handleServerError } from '@/lib/handle-server-error'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'

const dictTypeFormSchema = z.object({
  typeCode: z.string().trim().min(2, '字典类型编码至少需要 2 个字符'),
  typeName: z.string().trim().min(2, '字典类型名称至少需要 2 个字符'),
  description: z.string().max(500, '描述最多 500 个字符').nullable(),
  enabled: z.boolean(),
})

const dictItemFormSchema = z.object({
  dictTypeId: z.string().trim().min(1, '请选择所属字典类型'),
  itemCode: z.string().trim().min(1, '字典项编码不能为空'),
  itemLabel: z.string().trim().min(1, '字典项名称不能为空'),
  itemValue: z.string().trim().min(1, '字典项值不能为空'),
  sortOrder: z.number().int().min(0, '排序值不能小于 0'),
  remark: z.string().max(500, '备注最多 500 个字符').nullable(),
  enabled: z.boolean(),
})

type DictTypeFormValues = z.infer<typeof dictTypeFormSchema>
type DictItemFormValues = z.infer<typeof dictItemFormSchema>
type SubmitAction = 'list' | 'continue'

type DictPageErrorProps = {
  title: string
  description: string
  retry?: () => void
  listHref?: string
}

type DictTypePageSearch = {
  search: ListQuerySearch
  navigate: NavigateFn
}

type DictItemPageSearch = {
  search: ListQuerySearch
  navigate: NavigateFn
}

// 格式化后端返回的时间，避免展示无效字符串导致页面闪烁。
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
    second: '2-digit',
    hour12: false,
  }).format(date)
}

// 字典的启用/停用状态在多个页面复用，统一转换成中文文案。
function resolveDictStatusLabel(status: SystemDictStatus) {
  return status === 'ENABLED' ? '启用' : '停用'
}

// 字典状态 badge 视觉语义与其他系统页一致。
function resolveDictStatusVariant(status: SystemDictStatus) {
  return status === 'ENABLED' ? 'secondary' : 'outline'
}

// 独立骨架态和错误态，页面间复用，便于后续抽象。
function DictPageErrorState({
  title,
  description,
  retry,
  listHref,
}: DictPageErrorProps) {
  return (
    <PageShell title={title} description={description}>
      <Alert variant='destructive'>
        <AlertCircle />
        <AlertTitle>页面加载失败</AlertTitle>
        <AlertDescription>字典管理接口请求未成功，请重试或返回列表。</AlertDescription>
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

// 页面加载骨架，保证四页模式下的统一占位感。
function DictPageLoadingState({
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

// 字典类型列表页采用通用 ResourceListPage 骨架，支持分页和关键字段展示。
const dictTypeColumns: ColumnDef<SystemDictTypeRecord>[] = [
  {
    accessorKey: 'typeCode',
    header: '类型编码',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.typeCode}</span>
        <span className='text-xs text-muted-foreground'>{row.original.dictTypeId}</span>
      </div>
    ),
  },
  {
    accessorKey: 'typeName',
    header: '类型名称',
  },
  {
    accessorKey: 'description',
    header: '说明',
    cell: ({ row }) => row.original.description ?? '-',
  },
  {
    accessorKey: 'itemCount',
    header: '字典项数量',
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveDictStatusVariant(row.original.status)}>
        {resolveDictStatusLabel(row.original.status)}
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
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link to='/system/dict-types/$dictTypeId' params={{ dictTypeId: row.original.dictTypeId }}>
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/dict-types/$dictTypeId/edit'
            params={{ dictTypeId: row.original.dictTypeId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

function buildEmptyDictTypePage(search: ListQuerySearch): SystemDictTypePageResponse {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

export function DictTypesListPage({ search, navigate }: DictTypePageSearch) {
  const query = useQuery({
    queryKey: ['system-dict-types', search],
    queryFn: () => listSystemDictTypes(search),
  })

  const data = query.data ?? buildEmptyDictTypePage(search)

  if (query.isLoading) {
    return (
      <PageShell
        title='字典类型'
        description='字典类型使用独立列表页维护，支持分页、关键字和排序。'
      >
        <Skeleton className='h-64 w-full' />
      </PageShell>
    )
  }

  if (query.isError) {
    return (
      <DictPageErrorState
        title='字典类型'
        description='字典类型列表未能加载成功。'
        retry={() => void query.refetch()}
      />
    )
  }

  const enabledCount = data.records.filter((item) => item.status === 'ENABLED').length
  const totalItemCount = data.records.reduce((sum, item) => sum + item.itemCount, 0)

  return (
    <ResourceListPage
      title='字典类型'
      description='维护全局字典分类信息，如任务状态、审批动作、业务标签等。'
      endpoint='/system/dict-types/page'
      searchPlaceholder='搜索类型编码、名称或说明'
      search={search}
      navigate={navigate}
      columns={dictTypeColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '类型总量', value: String(data.total), hint: '每个字典类型可挂载多个字典项。' },
        { label: '当前页可见', value: String(data.records.length), hint: '与关键字和分页参数联动。' },
        {
          label: '已启用类型',
          value: String(enabledCount),
          hint: `${totalItemCount} 个字典项分布在已启用类型中。`,
        },
      ]}
      createAction={{
        label: '新建字典类型',
        href: '/system/dict-types/create',
      }}
    />
  )
}

// 字典项列表页展示所属类型和排序，支持独立分页查询。
const dictItemColumns: ColumnDef<SystemDictItemRecord>[] = [
  {
    accessorKey: 'itemCode',
    header: '字典项编码',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.itemCode}</span>
        <span className='text-xs text-muted-foreground'>{row.original.dictItemId}</span>
      </div>
    ),
  },
  {
    accessorKey: 'itemLabel',
    header: '字典项名称',
  },
  {
    accessorKey: 'itemValue',
    header: '字典项值',
  },
  {
    accessorKey: 'dictTypeCode',
    header: '所属类型',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span>{row.original.dictTypeCode}</span>
        <span className='text-xs text-muted-foreground'>{row.original.dictTypeName}</span>
      </div>
    ),
  },
  {
    accessorKey: 'sortOrder',
    header: '排序',
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveDictStatusVariant(row.original.status)}>
        {resolveDictStatusLabel(row.original.status)}
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
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link to='/system/dict-items/$dictItemId' params={{ dictItemId: row.original.dictItemId }}>
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/dict-items/$dictItemId/edit'
            params={{ dictItemId: row.original.dictItemId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

function buildEmptyDictItemPage(search: ListQuerySearch): SystemDictItemPageResponse {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

export function DictItemsListPage({ search, navigate }: DictItemPageSearch) {
  const query = useQuery({
    queryKey: ['system-dict-items', search],
    queryFn: () => listSystemDictItems(search),
  })

  const data = query.data ?? buildEmptyDictItemPage(search)

  if (query.isLoading) {
    return (
      <PageShell
        title='字典项'
        description='字典项使用独立列表页维护，支持分页与关键字查询。'
      >
        <Skeleton className='h-64 w-full' />
      </PageShell>
    )
  }

  if (query.isError) {
    return (
      <DictPageErrorState
        title='字典项'
        description='字典项列表未能加载成功。'
        retry={() => void query.refetch()}
      />
    )
  }

  const enabledCount = data.records.filter((item) => item.status === 'ENABLED').length
  const typeCount = new Set(data.records.map((item) => item.dictTypeId)).size

  return (
    <ResourceListPage
      title='字典项'
      description='维护具体字典值与展示标签，支持排序与所属类型归档。'
      endpoint='/system/dict-items/page'
      searchPlaceholder='搜索字典项编码、名称或字典类型'
      search={search}
      navigate={navigate}
      columns={dictItemColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '字典项总量', value: String(data.total), hint: '含停用项和启用项，便于全量审视。' },
        { label: '当前页数量', value: String(data.records.length), hint: '默认每页 20 条，可按需调整。' },
        {
          label: '覆盖类型',
          value: String(typeCount),
          hint: `${enabledCount} 项已启用，可直接用于字典下拉联动。`,
        },
      ]}
      createAction={{
        label: '新建字典项',
        href: '/system/dict-items/create',
      }}
    />
  )
}

// 字典类型新增/编辑共享，表单提交后可返回列表或继续编辑。
function DictTypeFormPage({ mode, dictTypeId }: { mode: 'create' | 'edit'; dictTypeId?: string }) {
  const isEdit = mode === 'edit'
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitAction, setSubmitAction] = useState<SubmitAction>('list')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const form = useForm<DictTypeFormValues>({
    resolver: zodResolver(dictTypeFormSchema),
    defaultValues: {
      typeCode: '',
      typeName: '',
      description: '',
      enabled: true,
    },
  })

  const optionsQuery = useQuery<SystemDictTypeFormOptions>({
    queryKey: ['system-dict-type-form-options'],
    queryFn: getSystemDictTypeFormOptions,
  })

  const detailQuery = useQuery<SystemDictTypeDetail>({
    queryKey: ['system-dict-type', dictTypeId],
    enabled: isEdit && Boolean(dictTypeId),
    queryFn: () => getSystemDictTypeDetail(dictTypeId!),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset({
        typeCode: detailQuery.data.typeCode,
        typeName: detailQuery.data.typeName,
        description: detailQuery.data.description,
        enabled: detailQuery.data.status === 'ENABLED',
      })
    }
  }, [detailQuery.data, form])

  const createMutation = useMutation({
    mutationFn: createSystemDictType,
    onError: (error) => {
      const apiError = applyDictTypeFieldErrors(form, error)
      if (!apiError || !apiError.fieldErrors?.length) {
        setErrorMessage(apiError?.message ?? null)
        handleServerError(error)
      }
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ dictTypeId, payload }: { dictTypeId: string; payload: SaveSystemDictTypePayload }) =>
      updateSystemDictType(dictTypeId, payload),
    onError: (error) => {
      const apiError = applyDictTypeFieldErrors(form, error)
      if (!apiError || !apiError.fieldErrors?.length) {
        setErrorMessage(apiError?.message ?? null)
        handleServerError(error)
      }
    },
  })

  const enabled = useWatch({
    control: form.control,
    name: 'enabled',
    defaultValue: false,
  })
  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const isInitialLoading = optionsQuery.isLoading || (isEdit && detailQuery.isLoading)

  function toPayload(values: DictTypeFormValues): SaveSystemDictTypePayload {
    return {
      typeCode: values.typeCode.trim(),
      typeName: values.typeName.trim(),
      description: values.description?.trim() ? values.description.trim() : null,
      enabled: values.enabled,
    }
  }

  async function onSubmit(values: DictTypeFormValues) {
    form.clearErrors()
    setErrorMessage(null)

    try {
      const payload = toPayload(values)
      const result = isEdit
        ? await updateMutation.mutateAsync({ dictTypeId: dictTypeId!, payload })
        : await createMutation.mutateAsync(payload)

      await queryClient.invalidateQueries({ queryKey: ['system-dict-types'] })
      await queryClient.invalidateQueries({ queryKey: ['system-dict-type', result.dictTypeId] })

      toast.success(isEdit ? '字典类型已更新' : '字典类型已创建')

      if (submitAction === 'continue' && isEdit) {
        startTransition(() => {
          navigate({ to: '/system/dict-types/$dictTypeId/edit', params: { dictTypeId: result.dictTypeId } })
        })
        return
      }

      startTransition(() => {
        navigate({ to: '/system/dict-types/list' })
      })
    } catch (error) {
      // 这里的错误由 mutation 的 onError 处理，catch 用于保留兜底。
      if (error instanceof Error) {
        setErrorMessage(error.message)
      }
    }
  }

  if (isInitialLoading) {
    return (
      <DictPageLoadingState
        title={isEdit ? '编辑字典类型' : '新建字典类型'}
        description='正在加载字典类型数据与可选项。'
      />
    )
  }

  if (optionsQuery.isError || (isEdit && detailQuery.isError)) {
    return (
      <DictPageErrorState
        title={isEdit ? '编辑字典类型' : '新建字典类型'}
        description='表单依赖数据未加载成功。'
        retry={() => {
          void optionsQuery.refetch()
          if (isEdit) {
            void detailQuery.refetch()
          }
        }}
        listHref='/system/dict-types/list'
      />
    )
  }

  const enabledOption = (optionsQuery.data?.statusOptions ?? []).find(
    (option) => option.code === (enabled ? 'ENABLED' : 'DISABLED')
  )
  const enabledLabel = enabledOption?.name ?? (enabled ? '启用' : '停用')
  const pageTitle = isEdit ? '编辑字典类型' : '新建字典类型'
  const pageDescription = isEdit
    ? '编辑字典类型后会影响列表和字典项归属关系。'
    : '新建类型后可直接创建对应字典项。'

  return (
    <PageShell
      title={pageTitle}
      description={pageDescription}
      actions={
        <>
          <Button
            type='submit'
            form='system-dict-type-form'
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
            form='system-dict-type-form'
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
            <Link to='/system/dict-types/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>{pageTitle}</CardTitle>
          <CardDescription>当前状态选项为：{enabledLabel}</CardDescription>
          {errorMessage ? (
            <Alert variant='destructive'>
              <AlertCircle />
              <AlertTitle>提交失败</AlertTitle>
              <AlertDescription>{errorMessage}</AlertDescription>
            </Alert>
          ) : null}
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form
              id='system-dict-type-form'
              className='grid gap-6'
              onSubmit={form.handleSubmit(onSubmit)}
            >
              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='typeCode'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>类型编码</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：TASK_STATUS' {...field} />
                      </FormControl>
                      <FormDescription>类型编码用于字典项分组和程序约束，不支持中文。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='typeName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>类型名称</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：任务状态' {...field} />
                      </FormControl>
                      <FormDescription>显示给管理后台的中文名称。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <FormField
                control={form.control}
                name='description'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>类型说明</FormLabel>
                    <FormControl>
                      <Textarea
                        placeholder='可选，说明该类型用于哪个业务场景。'
                        rows={4}
                        {...field}
                        value={field.value ?? ''}
                        onChange={(event) => field.onChange(event.target.value || null)}
                      />
                    </FormControl>
                    <FormDescription>说明会在列表和详情中辅助区分相似分类。</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='enabled'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>状态</FormLabel>
                      <FormControl>
                        <div className='flex items-center gap-3 rounded-md border p-3'>
                          <Switch checked={field.value} onCheckedChange={field.onChange} />
                          <span className='text-sm text-muted-foreground'>
                            {field.value ? '启用' : '停用'}
                          </span>
                        </div>
                      </FormControl>
                      <FormDescription>停用后该字典类型会在管理界面保留，不再新增关联项。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </form>
          </Form>
        </CardContent>
      </Card>
    </PageShell>
  )
}

// 把后端字段级校验映射到字典类型表单控件。
function applyDictTypeFieldErrors(
  form: UseFormReturn<DictTypeFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    if (
      fieldError.field === 'typeCode' ||
      fieldError.field === 'typeName' ||
      fieldError.field === 'description' ||
      fieldError.field === 'enabled'
    ) {
      form.setError(fieldError.field as keyof DictTypeFormValues, {
        type: 'server',
        message: fieldError.message,
      })
    }
  })

  return apiError
}

export function DictTypeCreatePage() {
  return <DictTypeFormPage mode='create' />
}

export function DictTypeEditPage({ dictTypeId }: { dictTypeId: string }) {
  return <DictTypeFormPage mode='edit' dictTypeId={dictTypeId} />
}

function DictTypeDetailField({
  label,
  value,
  icon,
}: {
  label: string
  value: ReactNode
  icon?: ReactNode
}) {
  return (
    <div className='space-y-1 rounded-lg border bg-muted/20 p-4'>
      <div className='flex items-center gap-2 text-sm text-muted-foreground'>
        {icon}
        <span>{label}</span>
      </div>
      <p className='text-sm text-foreground'>{value}</p>
    </div>
  )
}

export function DictTypeDetailPage({ dictTypeId }: { dictTypeId: string }) {
  const query = useQuery<SystemDictTypeDetail>({
    queryKey: ['system-dict-type', dictTypeId],
    queryFn: () => getSystemDictTypeDetail(dictTypeId),
  })

  if (query.isLoading) {
    return (
      <DictPageLoadingState
        title='字典类型详情'
        description='正在加载字典类型详细信息。'
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <DictPageErrorState
        title='字典类型详情'
        description='字典类型详情未能加载成功。'
        listHref='/system/dict-types/list'
        retry={() => void query.refetch()}
      />
    )
  }

  const detail = query.data

  return (
    <PageShell
      title='字典类型详情'
      description='查看字典类型信息，支持直接跳转到编辑与返回列表。'
      actions={
        <>
          <Button asChild variant='outline'>
            <Link
              to='/system/dict-types/$dictTypeId/edit'
              params={{ dictTypeId }}
            >
              编辑
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/dict-types/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-2'>
        <Card>
          <CardHeader>
            <CardTitle>基础信息</CardTitle>
            <CardDescription>该条目用于统一管理字典类型生命周期。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <DictTypeDetailField label='类型编码' value={detail.typeCode} icon={<Hash className='size-4' />} />
            <DictTypeDetailField label='类型名称' value={detail.typeName} icon={<ListTree className='size-4' />} />
            <DictTypeDetailField
              label='说明'
              value={detail.description ?? '-'}
              icon={<Tag className='size-4' />}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>状态与时间</CardTitle>
            <CardDescription>创建时间与更新时间可用于排查同步和变更问题。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <DictTypeDetailField
              label='状态'
              value={
                <Badge variant={resolveDictStatusVariant(detail.status)}>
                  {resolveDictStatusLabel(detail.status)}
                </Badge>
              }
              icon={<Layers3 className='size-4' />}
            />
            <DictTypeDetailField
              label='字典项数量'
              value={<Badge variant='outline'>{detail.itemCount}</Badge>}
            />
            <div className='grid gap-2 md:grid-cols-2'>
              <DictTypeDetailField
                label='创建时间'
                value={formatDateTime(detail.createdAt)}
                icon={<Calendar className='size-4' />}
              />
              <DictTypeDetailField
                label='更新时间'
                value={formatDateTime(detail.updatedAt)}
                icon={<Calendar className='size-4' />}
              />
            </div>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

// 字典项新增/编辑页面共享逻辑，包含所属类型列表与状态选择。
function DictItemFormPage({ mode, dictItemId }: { mode: 'create' | 'edit'; dictItemId?: string }) {
  const isEdit = mode === 'edit'
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitAction, setSubmitAction] = useState<SubmitAction>('list')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const form = useForm<DictItemFormValues>({
    resolver: zodResolver(dictItemFormSchema),
    defaultValues: {
      dictTypeId: '',
      itemCode: '',
      itemLabel: '',
      itemValue: '',
      sortOrder: 0,
      remark: '',
      enabled: true,
    },
  })

  const optionsQuery = useQuery<SystemDictItemFormOptions>({
    queryKey: ['system-dict-item-form-options'],
    queryFn: getSystemDictItemFormOptions,
  })

  const detailQuery = useQuery<SystemDictItemDetail>({
    queryKey: ['system-dict-item', dictItemId],
    enabled: isEdit && Boolean(dictItemId),
    queryFn: () => getSystemDictItemDetail(dictItemId!),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset({
        dictTypeId: detailQuery.data.dictTypeId,
        itemCode: detailQuery.data.itemCode,
        itemLabel: detailQuery.data.itemLabel,
        itemValue: detailQuery.data.itemValue,
        sortOrder: detailQuery.data.sortOrder,
        remark: detailQuery.data.remark ?? '',
        enabled: detailQuery.data.status === 'ENABLED',
      })
    }
  }, [detailQuery.data, form])

  const createMutation = useMutation({
    mutationFn: createSystemDictItem,
    onError: (error) => {
      const apiError = applyDictItemFieldErrors(form, error)
      if (!apiError || !apiError.fieldErrors?.length) {
        setErrorMessage(apiError?.message ?? null)
        handleServerError(error)
      }
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({
      dictItemId,
      payload,
    }: {
      dictItemId: string
      payload: SaveSystemDictItemPayload
    }) => updateSystemDictItem(dictItemId, payload),
    onError: (error) => {
      const apiError = applyDictItemFieldErrors(form, error)
      if (!apiError || !apiError.fieldErrors?.length) {
        setErrorMessage(apiError?.message ?? null)
        handleServerError(error)
      }
    },
  })

  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const isInitialLoading = optionsQuery.isLoading || (isEdit && detailQuery.isLoading)

  function toPayload(values: DictItemFormValues): SaveSystemDictItemPayload {
    return {
      dictTypeId: values.dictTypeId,
      itemCode: values.itemCode.trim(),
      itemLabel: values.itemLabel.trim(),
      itemValue: values.itemValue.trim(),
      sortOrder: values.sortOrder,
      remark: values.remark?.trim() ? values.remark.trim() : null,
      enabled: values.enabled,
    }
  }

  async function onSubmit(values: DictItemFormValues) {
    form.clearErrors()
    setErrorMessage(null)

    try {
      const payload = toPayload(values)
      const result = isEdit
        ? await updateMutation.mutateAsync({ dictItemId: dictItemId!, payload })
        : await createMutation.mutateAsync(payload)

      await queryClient.invalidateQueries({ queryKey: ['system-dict-items'] })
      await queryClient.invalidateQueries({ queryKey: ['system-dict-item', result.dictItemId] })

      toast.success(isEdit ? '字典项已更新' : '字典项已创建')

      if (submitAction === 'continue' && isEdit) {
        startTransition(() => {
          navigate({
            to: '/system/dict-items/$dictItemId/edit',
            params: { dictItemId: result.dictItemId },
          })
        })
        return
      }

      startTransition(() => {
        navigate({ to: '/system/dict-items/list' })
      })
    } catch (error) {
      if (error instanceof Error) {
        setErrorMessage(error.message)
      }
    }
  }

  const dictTypeOptions = useMemo(() => optionsQuery.data?.dictTypes ?? [], [optionsQuery.data?.dictTypes])
  const statusOptions = useMemo(
    () => optionsQuery.data?.statusOptions ?? [],
    [optionsQuery.data?.statusOptions]
  )

  const enabled = useWatch({
    control: form.control,
    name: 'enabled',
    defaultValue: false,
  })
  const statusLabel = statusOptions.find(
    (item) => item.code === (enabled ? 'ENABLED' : 'DISABLED')
  )?.name

  if (isInitialLoading) {
    return (
      <DictPageLoadingState
        title={isEdit ? '编辑字典项' : '新建字典项'}
        description='正在加载字典项依赖选项。'
      />
    )
  }

  if (optionsQuery.isError || (isEdit && detailQuery.isError)) {
    return (
      <DictPageErrorState
        title={isEdit ? '编辑字典项' : '新建字典项'}
        description='表单依赖数据未能加载成功。'
        listHref='/system/dict-items/list'
        retry={() => {
          void optionsQuery.refetch()
          if (isEdit) {
            void detailQuery.refetch()
          }
        }}
      />
    )
  }

  if (!dictTypeOptions.length) {
    return (
      <DictPageErrorState
        title={isEdit ? '编辑字典项' : '新建字典项'}
        description='尚未配置可选字典类型，请先维护至少一个字典类型。'
        listHref='/system/dict-items/list'
      />
    )
  }

  const pageTitle = isEdit ? '编辑字典项' : '新建字典项'
  const pageDescription = isEdit
    ? '编辑字典项会影响下游展示文案与下拉取值。'
    : '选择所属类型后维护编码、展示文案和排序。'

  return (
    <PageShell
      title={pageTitle}
      description={`${pageDescription} 当前状态：${statusLabel ?? '未命名状态'}`}
      actions={
        <>
          <Button
            type='submit'
            form='system-dict-item-form'
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
            form='system-dict-item-form'
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
            <Link to='/system/dict-items/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>{pageTitle}</CardTitle>
          <CardDescription>字典项与字典类型形成联动，新增后可用于各类下拉和配置。</CardDescription>
          {errorMessage ? (
            <Alert variant='destructive'>
              <AlertCircle />
              <AlertTitle>提交失败</AlertTitle>
              <AlertDescription>{errorMessage}</AlertDescription>
            </Alert>
          ) : null}
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form
              id='system-dict-item-form'
              className='grid gap-6'
              onSubmit={form.handleSubmit(onSubmit)}
            >
              <FormField
                control={form.control}
                name='dictTypeId'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>所属字典类型</FormLabel>
                    <Select
                      value={field.value}
                      onValueChange={(value) => field.onChange(value)}
                    >
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder='请选择所属类型' />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {dictTypeOptions.map((item: SystemDictItemDictTypeOption) => (
                          <SelectItem key={item.dictTypeId} value={item.dictTypeId}>
                            {item.typeCode} · {item.typeName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='itemCode'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>字典项编码</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：PENDING' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='itemLabel'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>字典项名称</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：待处理' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='itemValue'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>字典项值</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：01' {...field} />
                      </FormControl>
                      <FormDescription>字典项值为实际写入业务侧的字段值。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='sortOrder'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>排序值</FormLabel>
                      <FormControl>
                        <Input
                          type='number'
                          min={0}
                          step={1}
                          {...field}
                          onChange={(event) => field.onChange(Number(event.target.value || 0))}
                        />
                      </FormControl>
                      <FormDescription>越小越靠前，用于界面和下拉列表排序。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <FormField
                control={form.control}
                name='remark'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>备注</FormLabel>
                    <FormControl>
                      <Textarea placeholder='可选，填写使用场景或说明。' rows={3} {...field} value={field.value ?? ''} onChange={(event) => field.onChange(event.target.value || null)} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name='enabled'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>状态</FormLabel>
                    <FormControl>
                      <div className='flex items-center gap-3 rounded-md border p-3'>
                        <Switch checked={field.value} onCheckedChange={field.onChange} />
                        <span className='text-sm text-muted-foreground'>
                          {field.value ? '启用' : '停用'}
                        </span>
                      </div>
                    </FormControl>
                    <FormDescription>停用后该项将不再用于新增选择。</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </form>
          </Form>
        </CardContent>
      </Card>
    </PageShell>
  )
}

// 将后端字段错误回写到字典项表单，避免重复弹窗和误操作。
function applyDictItemFieldErrors(
  form: UseFormReturn<DictItemFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    if (
      fieldError.field === 'dictTypeId' ||
      fieldError.field === 'itemCode' ||
      fieldError.field === 'itemLabel' ||
      fieldError.field === 'itemValue' ||
      fieldError.field === 'sortOrder' ||
      fieldError.field === 'remark' ||
      fieldError.field === 'enabled'
    ) {
      form.setError(fieldError.field as keyof DictItemFormValues, {
        type: 'server',
        message: fieldError.message,
      })
    }
  })

  return apiError
}

export function DictItemCreatePage() {
  return <DictItemFormPage mode='create' />
}

export function DictItemEditPage({ dictItemId }: { dictItemId: string }) {
  return <DictItemFormPage mode='edit' dictItemId={dictItemId} />
}

// 字典项详情页面独立展示并跳转到编辑。
export function DictItemDetailPage({ dictItemId }: { dictItemId: string }) {
  const query = useQuery<SystemDictItemDetail>({
    queryKey: ['system-dict-item', dictItemId],
    queryFn: () => getSystemDictItemDetail(dictItemId),
  })

  if (query.isLoading) {
    return (
      <DictPageLoadingState
        title='字典项详情'
        description='正在加载字典项详细信息。'
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <DictPageErrorState
        title='字典项详情'
        description='字典项详情未能加载成功。'
        listHref='/system/dict-items/list'
        retry={() => void query.refetch()}
      />
    )
  }

  const detail = query.data

  return (
    <PageShell
      title='字典项详情'
      description='查看字典项基本属性并可直接跳转编辑。'
      actions={
        <>
          <Button asChild variant='outline'>
            <Link
              to='/system/dict-items/$dictItemId/edit'
              params={{ dictItemId }}
            >
              编辑
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/dict-items/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 xl:grid-cols-2'>
        <Card>
          <CardHeader>
            <CardTitle>基本信息</CardTitle>
            <CardDescription>字典项用于表单选项、枚举码和下拉回显。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <DictTypeDetailField
              label='所属类型'
              value={`${detail.dictTypeCode} · ${detail.dictTypeName}`}
              icon={<Tag className='size-4' />}
            />
            <DictTypeDetailField
              label='字典项编码'
              value={detail.itemCode}
              icon={<Hash className='size-4' />}
            />
            <DictTypeDetailField
              label='字典项名称'
              value={detail.itemLabel}
              icon={<ListTree className='size-4' />}
            />
            <DictTypeDetailField
              label='字典项值'
              value={detail.itemValue}
              icon={<Layers3 className='size-4' />}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>状态与排序</CardTitle>
            <CardDescription>排序影响显示顺序，状态用于下游枚举筛选。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <DictTypeDetailField
              label='状态'
              value={<Badge variant={resolveDictStatusVariant(detail.status)}>{resolveDictStatusLabel(detail.status)}</Badge>}
              icon={<Layers3 className='size-4' />}
            />
            <DictTypeDetailField
              label='排序'
              value={<Badge variant='outline'>{detail.sortOrder}</Badge>}
            />
            <div className='grid gap-2 md:grid-cols-2'>
              <DictTypeDetailField
                label='创建时间'
                value={formatDateTime(detail.createdAt)}
                icon={<Calendar className='size-4' />}
              />
              <DictTypeDetailField
                label='更新时间'
                value={formatDateTime(detail.updatedAt)}
                icon={<Calendar className='size-4' />}
              />
            </div>
            <DictTypeDetailField
              label='备注'
              value={detail.remark ?? '-'}
              icon={<Tag className='size-4' />}
            />
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}
