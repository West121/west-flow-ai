import { startTransition, useEffect } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { useForm, type UseFormReturn, useWatch } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  Loader2,
  Save,
  Zap,
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
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { getApiErrorResponse } from '@/lib/api/client'
import {
  createTrigger,
  getTriggerDetail,
  getTriggerFormOptions,
  listTriggers,
  type SaveTriggerPayload,
  type TriggerAutomationStatus,
  type TriggerDetail,
  type TriggerRecord,
  updateTrigger,
} from '@/lib/api/triggers'
import { handleServerError } from '@/lib/handle-server-error'
import {
  normalizeListQuerySearch,
  type ListQuerySearch,
} from '@/features/shared/table/query-contract'

const triggersRoute = getRouteApi('/_authenticated/system/triggers/list')

const triggerFormSchema = z.object({
  triggerName: z.string().trim().min(2, '触发器名称至少需要 2 个字符'),
  triggerKey: z.string().trim().min(2, '触发器编码至少需要 2 个字符'),
  triggerEvent: z.string().trim().min(1, '请选择触发事件'),
  businessType: z.string().trim(),
  channelIdsText: z.string().trim(),
  conditionExpression: z.string().max(500, '条件表达式最多 500 个字符'),
  description: z.string().max(500, '说明最多 500 个字符'),
  enabled: z.boolean(),
})

type TriggerFormValues = z.infer<typeof triggerFormSchema>
type SubmitAction = 'list' | 'continue'

// 触发器管理页统一用这个方法格式化时间。
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

// 自动化状态只展示最关键的中文标签。
function resolveAutomationStatusLabel(status: TriggerRecord['automationStatus']) {
  switch (status) {
    case 'ACTIVE':
      return '启用'
    case 'PAUSED':
      return '已暂停'
    default:
      return '停用'
  }
}

// 自动化状态 badge 保持统一视觉语义。
function resolveAutomationStatusVariant(status: TriggerAutomationStatus | string) {
  switch (status) {
    case 'ACTIVE':
      return 'secondary'
    case 'PAUSED':
      return 'outline'
    default:
      return 'destructive'
  }
}

// 编辑页回填时把详情转换成表单默认值。
function toFormValues(detail?: TriggerDetail): TriggerFormValues {
  return {
    triggerName: detail?.triggerName ?? '',
    triggerKey: detail?.triggerKey ?? '',
    triggerEvent: detail?.triggerEvent ?? '',
    businessType: detail?.businessType ?? '',
    channelIdsText: (detail?.channelIds ?? []).join(', '),
    conditionExpression: detail?.conditionExpression ?? '',
    description: detail?.description ?? '',
    enabled: detail?.enabled ?? true,
  }
}

// 后端字段错误直接回写到触发器表单。
function applyTriggerFieldErrors(
  form: UseFormReturn<TriggerFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    if (
      fieldError.field === 'triggerName' ||
      fieldError.field === 'triggerKey' ||
      fieldError.field === 'triggerEvent' ||
      fieldError.field === 'businessType' ||
      fieldError.field === 'channelIdsText' ||
      fieldError.field === 'conditionExpression' ||
      fieldError.field === 'description' ||
      fieldError.field === 'enabled'
    ) {
      form.setError(fieldError.field, {
        type: 'server',
        message: fieldError.message,
      })
    }
  })

  return apiError
}

// 列表请求失败时先返回空分页兜底。
function buildEmptyTriggerPage(search: ListQuerySearch) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

const triggerColumns: ColumnDef<TriggerRecord>[] = [
  {
    accessorKey: 'triggerName',
    header: '触发器名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.triggerName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.triggerId}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'triggerKey',
    header: '触发器编码',
  },
  {
    accessorKey: 'triggerEvent',
    header: '触发事件',
  },
  {
    accessorKey: 'automationStatus',
    header: '自动化状态',
    cell: ({ row }) => (
      <Badge variant={resolveAutomationStatusVariant(row.original.automationStatus)}>
        {resolveAutomationStatusLabel(row.original.automationStatus)}
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
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link to='/system/triggers/$triggerId' params={{ triggerId: row.original.triggerId }}>
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/triggers/$triggerId/edit'
            params={{ triggerId: row.original.triggerId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

// 页面异常时统一展示错误态。
function TriggerPageErrorState({
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
        <AlertDescription>触发器数据请求未成功，请重试或先返回列表页。</AlertDescription>
      </Alert>
      <div className='flex flex-wrap gap-2'>
        {retry ? <Button onClick={retry}>重新加载</Button> : null}
        <Button asChild variant='outline'>
          <Link to='/system/triggers/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      </div>
    </PageShell>
  )
}

function TriggerPageLoadingState({
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

function resolveTriggerFormTitle(mode: 'create' | 'edit') {
  return mode === 'create' ? '新建触发器' : '编辑触发器'
}

function resolveTriggerFormDescription(mode: 'create' | 'edit') {
  return mode === 'create'
    ? '维护自动化触发器配置，系统会按事件和条件分发自动动作。'
    : '修改触发器配置，保存后会影响后续自动化分发逻辑。'
}

function TriggerFormPage({
  mode,
  triggerId,
}: {
  mode: 'create' | 'edit'
  triggerId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const optionsQuery = useQuery({
    queryKey: ['system', 'trigger-options'],
    queryFn: () => getTriggerFormOptions(),
  })
  const detailQuery = useQuery({
    queryKey: ['system', 'trigger-detail', triggerId],
    queryFn: () => getTriggerDetail(triggerId as string),
    enabled: mode === 'edit' && Boolean(triggerId),
  })

  const form = useForm<TriggerFormValues>({
    resolver: zodResolver(triggerFormSchema),
    defaultValues: {
      triggerName: '',
      triggerKey: '',
      triggerEvent: '',
      businessType: '',
      channelIdsText: '',
      conditionExpression: '',
      description: '',
      enabled: true,
    },
  })
  const currentValues = useWatch({ control: form.control })

  useEffect(() => {
    if (mode === 'edit' && detailQuery.data) {
      form.reset(toFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form, mode])

  const mutation = useMutation({
    mutationFn: async (payload: { values: TriggerFormValues; submitAction: SubmitAction }) => {
      const request: SaveTriggerPayload = {
        triggerName: payload.values.triggerName.trim(),
        triggerKey: payload.values.triggerKey.trim(),
        triggerEvent: payload.values.triggerEvent,
        businessType: payload.values.businessType.trim() || undefined,
        channelIds: payload.values.channelIdsText
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean),
        conditionExpression: payload.values.conditionExpression.trim() || undefined,
        description: payload.values.description.trim() || undefined,
        enabled: payload.values.enabled,
      }

      return mode === 'create' && !triggerId
        ? createTrigger(request)
        : updateTrigger(triggerId as string, request)
    },
    onSuccess: async (result, payload) => {
      await queryClient.invalidateQueries({ queryKey: ['system', 'triggers'] })
      await queryClient.invalidateQueries({ queryKey: ['system', 'trigger-detail'] })

      toast.success(mode === 'create' ? '触发器已创建' : '触发器已更新')

      if (payload.submitAction === 'continue') {
        if (mode === 'create') {
          startTransition(() => {
            navigate({
              to: '/system/triggers/$triggerId/edit',
              params: { triggerId: result.triggerId },
            })
          })
        }
        return
      }

      startTransition(() => {
        if (mode === 'create') {
          navigate({ to: '/system/triggers/list' })
          return
        }

        navigate({
          to: '/system/triggers/$triggerId',
          params: { triggerId: triggerId as string },
        })
      })
    },
    onError: (error) => {
      const apiError = applyTriggerFieldErrors(form, error)
      if (!apiError) {
        handleServerError(error)
      }
    },
  })

  const onSubmit = form.handleSubmit((values, event) => {
    const submitter = (event?.nativeEvent as { submitter?: HTMLButtonElement } | undefined)?.submitter
    const submitAction: SubmitAction =
      submitter?.dataset.submitAction === 'continue' ? 'continue' : 'list'

    mutation.mutate({ values, submitAction })
  })

  const pageTitle = resolveTriggerFormTitle(mode)
  const pageDescription = resolveTriggerFormDescription(mode)
  const triggerEvents = optionsQuery.data?.triggerEvents ?? []

  if (optionsQuery.isError || detailQuery.isError) {
    return (
      <TriggerPageErrorState
        title={pageTitle}
        description={pageDescription}
        retry={() => {
          optionsQuery.refetch()
          detailQuery.refetch()
        }}
      />
    )
  }

  if (optionsQuery.isLoading || (mode === 'edit' && detailQuery.isLoading)) {
    return <TriggerPageLoadingState title={pageTitle} description={pageDescription} />
  }

  return (
    <PageShell
      title={pageTitle}
      description={pageDescription}
      actions={
        <Button asChild variant='outline'>
          <Link search={{}} to='/system/triggers/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>触发器配置</CardTitle>
            <CardDescription>触发器负责把流程事件转换成自动动作执行入口。</CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form className='grid gap-4 md:grid-cols-2' onSubmit={onSubmit}>
                <FormField
                  control={form.control}
                  name='triggerName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>触发器名称</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：请假审批完成通知' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='triggerKey'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>触发器编码</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：LEAVE_DONE_NOTIFY' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='triggerEvent'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>触发事件</FormLabel>
                      <FormControl>
                        <select
                          className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                          value={field.value}
                          onChange={field.onChange}
                        >
                          <option value=''>请选择触发事件</option>
                          {triggerEvents.map((item) => (
                            <option key={item.value} value={item.value}>
                              {item.label}
                            </option>
                          ))}
                        </select>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='businessType'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>业务类型</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：OA_LEAVE' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='channelIdsText'
                  render={({ field }) => (
                    <FormItem className='md:col-span-2'>
                      <FormLabel>通知渠道 ID</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：chn_001, chn_002' {...field} />
                      </FormControl>
                      <FormDescription>多个渠道用英文逗号分隔，保存时会自动转成数组。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='conditionExpression'
                  render={({ field }) => (
                    <FormItem className='md:col-span-2'>
                      <FormLabel>条件表达式</FormLabel>
                      <FormControl>
                        <Textarea className='min-h-24' placeholder='例如：status == "COMPLETED"' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='description'
                  render={({ field }) => (
                    <FormItem className='md:col-span-2'>
                      <FormLabel>说明</FormLabel>
                      <FormControl>
                        <Textarea className='min-h-24' placeholder='请输入触发器说明' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='enabled'
                  render={({ field }) => (
                    <FormItem className='flex flex-row items-center justify-between rounded-lg border p-4 md:col-span-2'>
                      <div className='space-y-1'>
                        <FormLabel>启用状态</FormLabel>
                        <FormDescription>停用后该触发器不会继续参与自动化分发。</FormDescription>
                      </div>
                      <FormControl>
                        <Switch checked={field.value} onCheckedChange={field.onChange} />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <div className='flex flex-wrap gap-2 md:col-span-2'>
                  <Button type='submit' variant='outline' disabled={mutation.isPending} data-submit-action='list'>
                    <Save data-icon='inline-start' />
                    保存并返回列表
                  </Button>
                  <Button type='submit' disabled={mutation.isPending} data-submit-action='continue'>
                    {mutation.isPending ? (
                      <>
                        <Loader2 data-icon='inline-start' className='animate-spin' />
                        保存中
                      </>
                    ) : (
                      <>
                        <BadgeCheck data-icon='inline-start' />
                        保存并继续编辑
                      </>
                    )}
                  </Button>
                </div>
              </form>
            </Form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>配置预览</CardTitle>
            <CardDescription>实时回显当前触发器状态，避免把错误条件写进自动化规则。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 text-sm'>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <p className='text-xs text-muted-foreground'>触发器名称</p>
              <p className='mt-2 font-medium'>{currentValues.triggerName || '--'}</p>
            </div>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <p className='text-xs text-muted-foreground'>触发器编码</p>
              <p className='mt-2 font-medium'>{currentValues.triggerKey || '--'}</p>
            </div>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <p className='text-xs text-muted-foreground'>触发事件</p>
              <p className='mt-2 font-medium'>{currentValues.triggerEvent || '--'}</p>
            </div>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <p className='text-xs text-muted-foreground'>启用状态</p>
              <p className='mt-2 font-medium'>{currentValues.enabled ? '启用' : '停用'}</p>
            </div>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

function TriggerDetailMetric({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Zap
  label: string
  value: string
}) {
  return (
    <div className='rounded-lg border bg-muted/20 p-4'>
      <div className='flex items-center gap-2 text-sm text-muted-foreground'>
        <Icon className='size-4' />
        <span>{label}</span>
      </div>
      <p className='mt-3 text-sm font-medium break-all'>{value}</p>
    </div>
  )
}

export function TriggersListPage() {
  const search = normalizeListQuerySearch(triggersRoute.useSearch())
  const navigate = triggersRoute.useNavigate()
  const triggersQuery = useQuery({
    queryKey: ['system', 'triggers', search],
    queryFn: () => listTriggers(search),
  })

  const pageData = triggersQuery.data ?? buildEmptyTriggerPage(search)

  return (
    <>
      {triggersQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>触发器列表加载失败</AlertTitle>
          <AlertDescription>
            {triggersQuery.error instanceof Error ? triggersQuery.error.message : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <ResourceListPage
        title='触发器管理'
        description='独立维护自动化触发器配置，支持分页、模糊搜索和状态回查。'
        endpoint='/api/v1/system/triggers/page'
        searchPlaceholder='搜索触发器名称、编码或事件'
        search={search}
        navigate={navigate}
        columns={triggerColumns}
        data={pageData.records}
        total={pageData.total}
        summaries={[
          {
            label: '触发器总量',
            value: String(pageData.total),
            hint: '后端分页接口返回的真实触发器数量。',
          },
          {
            label: '当前页启用',
            value: String(pageData.records.filter((item) => item.automationStatus === 'ACTIVE').length),
            hint: '当前页里正在生效的自动化触发器数量。',
          },
          {
            label: '当前页停用',
            value: String(pageData.records.filter((item) => item.automationStatus === 'DISABLED').length),
            hint: '当前页里已停用的自动化触发器数量。',
          },
        ]}
        createAction={{
          label: '新建触发器',
          href: '/system/triggers/create',
        }}
      />
    </>
  )
}

export function TriggerCreatePage() {
  return <TriggerFormPage mode='create' />
}

export function TriggerEditPage({ triggerId }: { triggerId: string }) {
  return <TriggerFormPage mode='edit' triggerId={triggerId} />
}

export function TriggerDetailPage({ triggerId }: { triggerId: string }) {
  const detailQuery = useQuery({
    queryKey: ['system', 'trigger-detail', triggerId],
    queryFn: () => getTriggerDetail(triggerId),
  })

  if (detailQuery.isLoading) {
    return (
      <TriggerPageLoadingState
        title='触发器详情'
        description='查看触发器的基础配置、触发事件和最近更新时间。'
      />
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <TriggerPageErrorState
        title='触发器详情'
        description='查看触发器的基础配置、触发事件和最近更新时间。'
        retry={() => detailQuery.refetch()}
      />
    )
  }

  const detail = detailQuery.data

  return (
    <PageShell
      title='触发器详情'
      description='查看触发器的基础配置、触发事件和最近更新时间。'
      actions={
        <div className='flex flex-wrap gap-2'>
          <Button asChild variant='outline'>
            <Link search={{}} to='/system/triggers/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
          <Button asChild>
            <Link to='/system/triggers/$triggerId/edit' params={{ triggerId }} search={{}}>
              编辑
            </Link>
          </Button>
        </div>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>基础信息</CardTitle>
            <CardDescription>触发器用于把流程事件转成自动化执行任务。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4 md:grid-cols-2'>
            <TriggerDetailMetric icon={Zap} label='触发器名称' value={detail.triggerName} />
            <TriggerDetailMetric icon={Zap} label='触发器编码' value={detail.triggerKey} />
            <TriggerDetailMetric icon={Zap} label='触发事件' value={detail.triggerEvent} />
            <TriggerDetailMetric icon={Zap} label='自动化状态' value={resolveAutomationStatusLabel(detail.automationStatus)} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>附加说明</CardTitle>
            <CardDescription>管理员可以在这里快速核对业务范围、渠道和条件表达式。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <div className='rounded-lg border bg-muted/20 p-4 text-sm leading-6 text-muted-foreground'>
              {detail.description ?? '暂无说明'}
            </div>
            <div className='grid gap-2 text-sm'>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>业务类型</span>
                <span>{detail.businessType ?? '--'}</span>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>通知渠道</span>
                <span>{detail.channelIds.join('、') || '--'}</span>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>创建时间</span>
                <span>{formatDateTime(detail.createdAt)}</span>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>更新时间</span>
                <span>{formatDateTime(detail.updatedAt)}</span>
              </div>
            </div>
            <Badge variant={resolveAutomationStatusVariant(detail.automationStatus)}>
              {resolveAutomationStatusLabel(detail.automationStatus)}
            </Badge>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}
