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
  createNotificationChannel,
  getNotificationChannelDiagnostic,
  getNotificationChannelDetail,
  getNotificationChannelFormOptions,
  type NotificationChannelDiagnostic,
  listNotificationChannels,
  type NotificationChannelDetail,
  type NotificationChannelRecord,
  type SaveNotificationChannelPayload,
  updateNotificationChannel,
} from '@/lib/api/notification-channels'
import { handleServerError } from '@/lib/handle-server-error'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'

const notificationChannelsRoute = getRouteApi(
  '/_authenticated/system/notification-channels/list'
)

const channelFormSchema = z.object({
  channelName: z.string().trim().min(2, '渠道名称至少需要 2 个字符'),
  channelType: z.string().trim().min(1, '请选择渠道类型'),
  endpoint: z.string().trim().min(1, '请填写通知地址'),
  secret: z.string().max(500, '密钥最多 500 个字符'),
  remark: z.string().max(500, '说明最多 500 个字符'),
  enabled: z.boolean(),
})

type ChannelFormValues = z.infer<typeof channelFormSchema>
type SubmitAction = 'list' | 'continue'

// 通知渠道管理页统一用这个方法格式化时间。
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

// 渠道状态只展示启用/停用。
function resolveStatusLabel(status: NotificationChannelRecord['status']) {
  return status === 'ENABLED' ? '启用' : '停用'
}

// 状态 badge 颜色和系统其他页面保持一致。
function resolveStatusVariant(status: NotificationChannelRecord['status']) {
  return status === 'ENABLED' ? 'secondary' : 'outline'
}

// 编辑页回填时把详情转换成表单默认值。
function toFormValues(detail?: NotificationChannelDetail): ChannelFormValues {
  return {
    channelName: detail?.channelName ?? '',
    channelType: detail?.channelType ?? '',
    endpoint: detail?.endpoint ?? '',
    secret: detail?.secret ?? '',
    remark: detail?.remark ?? '',
    enabled: detail?.status ? detail.status === 'ENABLED' : true,
  }
}

// 把后端字段错误回写到通知渠道表单。
function applyChannelFieldErrors(
  form: UseFormReturn<ChannelFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    if (
      fieldError.field === 'channelName' ||
      fieldError.field === 'channelType' ||
      fieldError.field === 'endpoint' ||
      fieldError.field === 'secret' ||
      fieldError.field === 'remark' ||
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

// 请求失败时先用空分页兜底。
function buildEmptyChannelPage(search: ListQuerySearch) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

const channelColumns: ColumnDef<NotificationChannelRecord>[] = [
  {
    accessorKey: 'channelName',
    header: '渠道名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.channelName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.channelId}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'channelType',
    header: '渠道类型',
    cell: ({ row }) => row.original.channelType,
  },
  {
    accessorKey: 'endpoint',
    header: '通知地址',
    cell: ({ row }) => row.original.endpoint,
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveStatusVariant(row.original.status)}>
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
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/notification-channels/$channelId'
            params={{ channelId: row.original.channelId }}
          >
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/notification-channels/$channelId/edit'
            params={{ channelId: row.original.channelId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

// 页面加载失败时统一展示错误态和返回按钮。
function ChannelPageErrorState({
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
        <AlertDescription>通知渠道数据请求未成功，请重试或先返回列表页。</AlertDescription>
      </Alert>
      <div className='flex flex-wrap gap-2'>
        {retry ? <Button onClick={retry}>重新加载</Button> : null}
        <Button asChild variant='outline'>
          <Link to='/system/notification-channels/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      </div>
    </PageShell>
  )
}

function ChannelPageLoadingState({
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

function resolveChannelFormTitle(mode: 'create' | 'edit') {
  return mode === 'create' ? '新建通知渠道' : '编辑通知渠道'
}

function resolveChannelFormDescription(mode: 'create' | 'edit') {
  return mode === 'create'
    ? '维护通知渠道配置，流程中心会在自动化动作中直接使用这些渠道。'
    : '修改通知渠道配置，保存后会影响后续自动通知发送。'
}

function ChannelFormPage({
  mode,
  channelId,
}: {
  mode: 'create' | 'edit'
  channelId?: string
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const optionsQuery = useQuery({
    queryKey: ['system', 'notification-channel-options'],
    queryFn: () => getNotificationChannelFormOptions(),
  })
  const detailQuery = useQuery({
    queryKey: ['system', 'notification-channel-detail', channelId],
    queryFn: () => getNotificationChannelDetail(channelId as string),
    enabled: mode === 'edit' && Boolean(channelId),
  })

  const form = useForm<ChannelFormValues>({
    resolver: zodResolver(channelFormSchema),
    defaultValues: {
      channelName: '',
      channelType: '',
      endpoint: '',
      secret: '',
      remark: '',
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
    mutationFn: async (payload: { values: ChannelFormValues; submitAction: SubmitAction }) => {
      const request: SaveNotificationChannelPayload = {
        channelName: payload.values.channelName.trim(),
        channelType: payload.values.channelType,
        endpoint: payload.values.endpoint.trim(),
        secret: payload.values.secret.trim() || undefined,
        remark: payload.values.remark.trim() || undefined,
        enabled: payload.values.enabled,
      }

      return mode === 'create' && !channelId
        ? createNotificationChannel(request)
        : updateNotificationChannel(channelId as string, request)
    },
    onSuccess: async (result, payload) => {
      await queryClient.invalidateQueries({ queryKey: ['system', 'notification-channels'] })
      await queryClient.invalidateQueries({ queryKey: ['system', 'notification-channel-detail'] })

      toast.success(mode === 'create' ? '通知渠道已创建' : '通知渠道已更新')

      if (payload.submitAction === 'continue') {
        if (mode === 'create') {
          startTransition(() => {
            navigate({
              to: '/system/notification-channels/$channelId/edit',
              params: { channelId: result.channelId },
            })
          })
        }
        return
      }

      startTransition(() => {
        if (mode === 'create') {
          navigate({ to: '/system/notification-channels/list' })
          return
        }

        navigate({
          to: '/system/notification-channels/$channelId',
          params: { channelId: channelId as string },
        })
      })
    },
    onError: (error) => {
      const apiError = applyChannelFieldErrors(form, error)
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

  const pageTitle = resolveChannelFormTitle(mode)
  const pageDescription = resolveChannelFormDescription(mode)
  const channelTypes = optionsQuery.data?.channelTypes ?? []

  if (optionsQuery.isError || detailQuery.isError) {
    return (
      <ChannelPageErrorState
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
    return <ChannelPageLoadingState title={pageTitle} description={pageDescription} />
  }

  return (
    <PageShell
      title={pageTitle}
      description={pageDescription}
      actions={
        <Button asChild variant='outline'>
          <Link search={{}} to='/system/notification-channels/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>渠道配置</CardTitle>
            <CardDescription>通知渠道负责承接自动化动作发出的消息，先配置好基础信息。</CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form className='grid gap-4 md:grid-cols-2' onSubmit={onSubmit}>
                <FormField
                  control={form.control}
                  name='channelName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>渠道名称</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：企业微信通知' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='channelType'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>渠道类型</FormLabel>
                      <FormControl>
                        <select
                          className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                          value={field.value}
                          onChange={field.onChange}
                        >
                          <option value=''>请选择渠道类型</option>
                          {channelTypes.map((item) => (
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
                  name='endpoint'
                  render={({ field }) => (
                    <FormItem className='md:col-span-2'>
                      <FormLabel>通知地址</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：ops@westflow.cn' {...field} />
                      </FormControl>
                      <FormDescription>邮件填邮箱地址，Webhook 填接口地址。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='secret'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>渠道密钥</FormLabel>
                      <FormControl>
                        <Input placeholder='可选，填写签名或鉴权密钥' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='remark'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>说明</FormLabel>
                      <FormControl>
                        <Textarea className='min-h-24' placeholder='请输入渠道说明' {...field} />
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
                        <FormDescription>停用后自动化动作不会继续通过该渠道发送通知。</FormDescription>
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
            <CardDescription>实时回显当前表单状态，方便管理员检查渠道名称与地址是否填写正确。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 text-sm'>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <p className='text-xs text-muted-foreground'>渠道名称</p>
              <p className='mt-2 font-medium'>{currentValues.channelName || '--'}</p>
            </div>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <p className='text-xs text-muted-foreground'>渠道类型</p>
              <p className='mt-2 font-medium'>{currentValues.channelType || '--'}</p>
            </div>
            <div className='rounded-lg border bg-muted/20 p-4'>
              <p className='text-xs text-muted-foreground'>通知地址</p>
              <p className='mt-2 break-all font-medium'>{currentValues.endpoint || '--'}</p>
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

function ChannelDetailMetric({
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

function resolveHealthLabel(status: string | null | undefined) {
  if (!status) {
    return '未诊断'
  }

  return status
}

function resolveHealthVariant(status: string | null | undefined) {
  switch (status) {
    case 'HEALTHY':
    case 'SUCCESS':
      return 'secondary' as const
    case 'DEGRADED':
    case 'WARNING':
      return 'outline' as const
    case 'UNHEALTHY':
    case 'FAILED':
      return 'destructive' as const
    default:
      return 'outline' as const
  }
}

function resolveBooleanLabel(
  value: boolean | null | undefined,
  positive = '是',
  negative = '否'
) {
  if (value == null) {
    return '--'
  }

  return value ? positive : negative
}

function NotificationChannelDiagnosticCard({
  diagnostic,
  channelId,
}: {
  diagnostic: NotificationChannelDiagnostic
  channelId?: string
}) {
  return (
    <Card>
      <CardHeader>
        <div className='flex flex-wrap items-start justify-between gap-3'>
          <div className='space-y-1'>
            <CardTitle>渠道诊断</CardTitle>
            <CardDescription>
              查看当前渠道配置完整性、最近发送状态和 provider 回执。
            </CardDescription>
          </div>
          {channelId ? (
            <Button asChild variant='outline' size='sm'>
              <Link
                to='/system/monitor/notification-channels/health/$channelId'
                params={{ channelId }}
              >
                查看健康监控详情
              </Link>
            </Button>
          ) : null}
        </div>
      </CardHeader>
      <CardContent className='grid gap-4'>
        <div className='flex flex-wrap items-center gap-2'>
          <Badge variant={resolveHealthVariant(diagnostic.healthStatus)}>
            {resolveHealthLabel(diagnostic.healthStatus)}
          </Badge>
          <Badge
            variant={diagnostic.configurationComplete ? 'secondary' : 'outline'}
          >
            {diagnostic.configurationComplete ? '配置完整' : '配置缺失'}
          </Badge>
        </div>
        <div className='grid gap-3 text-sm'>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>渠道编码</span>
            <span>{diagnostic.channelCode || '--'}</span>
          </div>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>最近发送结果</span>
            <span>{diagnostic.lastDispatchStatus || '--'}</span>
          </div>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>最近发送成功</span>
            <span>
              {resolveBooleanLabel(diagnostic.lastDispatchSuccess, '成功', '失败')}
            </span>
          </div>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>Provider</span>
            <span>{diagnostic.lastProviderName || '--'}</span>
          </div>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>最近发送时间</span>
            <span>{formatDateTime(diagnostic.lastDispatchAt)}</span>
          </div>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>最近成功时间</span>
            <span>{formatDateTime(diagnostic.lastSentAt)}</span>
          </div>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>最近失败时间</span>
            <span>{formatDateTime(diagnostic.lastFailureAt)}</span>
          </div>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4 text-sm leading-6'>
          <p className='text-xs text-muted-foreground'>最近回执</p>
          <p className='mt-2 break-words'>
            {diagnostic.lastResponseMessage || '--'}
          </p>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4 text-sm leading-6'>
          <p className='text-xs text-muted-foreground'>最近失败原因</p>
          <p className='mt-2 break-words'>
            {diagnostic.lastFailureMessage || '--'}
          </p>
        </div>
        <div className='rounded-lg border bg-muted/20 p-4 text-sm leading-6'>
          <p className='text-xs text-muted-foreground'>缺失配置</p>
          {diagnostic.missingConfigFields.length > 0 ? (
            <ul className='mt-2 list-disc pl-5'>
              {diagnostic.missingConfigFields.map((field) => (
                <li key={field}>{field}</li>
              ))}
            </ul>
          ) : (
            <p className='mt-2'>无</p>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

export function NotificationChannelsListPage() {
  const search = notificationChannelsRoute.useSearch()
  const navigate = notificationChannelsRoute.useNavigate()
  const channelsQuery = useQuery({
    queryKey: ['system', 'notification-channels', search],
    queryFn: () => listNotificationChannels(search),
  })

  const pageData = channelsQuery.data ?? buildEmptyChannelPage(search)

  return (
    <>
      {channelsQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>通知渠道列表加载失败</AlertTitle>
          <AlertDescription>
            {channelsQuery.error instanceof Error ? channelsQuery.error.message : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <ResourceListPage
        title='通知渠道管理'
        description='独立维护通知渠道配置，支持分页、模糊搜索和状态回查。'
        endpoint='/api/v1/system/notification-channels/page'
        searchPlaceholder='搜索渠道名称、类型或通知地址'
        search={search}
        navigate={navigate}
        columns={channelColumns}
        data={pageData.records}
        total={pageData.total}
        summaries={[
          {
            label: '通知渠道总量',
            value: String(pageData.total),
            hint: '后端分页接口返回的真实通知渠道数量。',
          },
          {
            label: '当前页启用',
            value: String(pageData.records.filter((item) => item.status === 'ENABLED').length),
            hint: '当前页里正在生效的通知渠道数量。',
          },
          {
            label: '当前页停用',
            value: String(pageData.records.filter((item) => item.status === 'DISABLED').length),
            hint: '当前页里已停用的通知渠道数量。',
          },
        ]}
        createAction={{
          label: '新建通知渠道',
          href: '/system/notification-channels/create',
        }}
      />
    </>
  )
}

export function NotificationChannelCreatePage() {
  return <ChannelFormPage mode='create' />
}

export function NotificationChannelEditPage({ channelId }: { channelId: string }) {
  return <ChannelFormPage mode='edit' channelId={channelId} />
}

export function NotificationChannelDetailPage({ channelId }: { channelId: string }) {
  const detailQuery = useQuery({
    queryKey: ['system', 'notification-channel-detail', channelId],
    queryFn: () => getNotificationChannelDetail(channelId),
  })
  const diagnosticQuery = useQuery({
    queryKey: ['system', 'notification-channel-diagnostic', channelId],
    queryFn: () => getNotificationChannelDiagnostic(channelId),
  })

  if (detailQuery.isLoading) {
    return (
      <ChannelPageLoadingState
        title='通知渠道详情'
        description='查看通知渠道的基础配置、启用状态和最近更新时间。'
      />
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <ChannelPageErrorState
        title='通知渠道详情'
        description='查看通知渠道的基础配置、启用状态和最近更新时间。'
        retry={() => detailQuery.refetch()}
      />
    )
  }

  const detail = detailQuery.data

  return (
    <PageShell
      title='通知渠道详情'
      description='查看通知渠道的基础配置、启用状态和最近更新时间。'
      actions={
        <div className='flex flex-wrap gap-2'>
          <Button asChild variant='outline'>
            <Link search={{}} to='/system/notification-channels/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
          <Button asChild>
            <Link
              to='/system/notification-channels/$channelId/edit'
              params={{ channelId }}
              search={{}}
            >
              编辑
            </Link>
          </Button>
        </div>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <div className='grid gap-4'>
          <Card>
            <CardHeader>
              <CardTitle>基础信息</CardTitle>
              <CardDescription>
                通知渠道用于承接系统自动化动作发送的消息。
              </CardDescription>
            </CardHeader>
            <CardContent className='grid gap-4 md:grid-cols-2'>
              <ChannelDetailMetric icon={Zap} label='渠道名称' value={detail.channelName} />
              <ChannelDetailMetric icon={Zap} label='渠道类型' value={detail.channelType} />
              <ChannelDetailMetric icon={Zap} label='通知地址' value={detail.endpoint} />
              <ChannelDetailMetric icon={Zap} label='状态' value={resolveStatusLabel(detail.status)} />
            </CardContent>
          </Card>
          {diagnosticQuery.isLoading ? (
            <Card>
              <CardHeader>
                <CardTitle>渠道诊断</CardTitle>
                <CardDescription>正在加载最近一次诊断和发送状态。</CardDescription>
              </CardHeader>
              <CardContent className='grid gap-3'>
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className='h-14 w-full' />
                ))}
              </CardContent>
            </Card>
          ) : diagnosticQuery.isError || !diagnosticQuery.data ? (
            <Alert variant='destructive'>
              <AlertCircle />
              <AlertTitle>诊断结果加载失败</AlertTitle>
              <AlertDescription>
                无法读取该通知渠道的诊断信息，请稍后重试。
              </AlertDescription>
            </Alert>
          ) : (
            <NotificationChannelDiagnosticCard
              diagnostic={diagnosticQuery.data}
              channelId={channelId}
            />
          )}
        </div>

        <Card>
          <CardHeader>
            <CardTitle>附加说明</CardTitle>
            <CardDescription>管理员可以在这里快速核对渠道密钥和备注。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <div className='rounded-lg border bg-muted/20 p-4 text-sm leading-6 text-muted-foreground'>
              {detail.remark ?? '暂无说明'}
            </div>
            <div className='grid gap-2 text-sm'>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>渠道密钥</span>
                <span className='break-all'>{detail.secret ?? '--'}</span>
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
            <Badge variant={resolveStatusVariant(detail.status)}>{resolveStatusLabel(detail.status)}</Badge>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}
