import { startTransition, useEffect, type ReactNode } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryResult,
} from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { useForm, type UseFormReturn } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  Loader2,
  PencilLine,
  Save,
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
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { getApiErrorResponse } from '@/lib/api/client'
import {
  createNotificationTemplate,
  getNotificationRecordDetail,
  getNotificationTemplateDetail,
  getNotificationTemplateFormOptions,
  listNotificationRecords,
  listNotificationTemplates,
  updateNotificationTemplate,
  type NotificationRecordRecord,
  type NotificationRecordStatus,
  type NotificationTemplateDetail,
  type NotificationTemplateFormOptions,
  type NotificationTemplateRecord,
  type NotificationTemplateStatus,
  type SaveNotificationTemplatePayload,
} from '@/lib/api/system-notifications'
import { handleServerError } from '@/lib/handle-server-error'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'

const templateListRoute = getRouteApi('/_authenticated/system/notifications/templates/list')
const templateDetailRoute = getRouteApi('/_authenticated/system/notifications/templates/$templateId/')
const templateEditRoute = getRouteApi('/_authenticated/system/notifications/templates/$templateId/edit')
const recordListRoute = getRouteApi('/_authenticated/system/notifications/records/list')
const recordDetailRoute = getRouteApi('/_authenticated/system/notifications/records/$recordId/')

const templateFormSchema = z.object({
  templateCode: z.string().trim().min(2, '模板编码至少需要 2 个字符'),
  templateName: z.string().trim().min(2, '模板名称至少需要 2 个字符'),
  channelType: z.string().trim().min(1, '请选择渠道类型'),
  titleTemplate: z.string().trim().min(2, '标题模板至少需要 2 个字符'),
  contentTemplate: z.string().trim().min(2, '内容模板至少需要 2 个字符'),
  remark: z.string().max(500, '备注最多 500 个字符').optional(),
  enabled: z.boolean(),
})

type TemplateFormValues = z.infer<typeof templateFormSchema>

function formatDateTime(value: string | null | undefined) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function resolveTemplateStatusLabel(status: NotificationTemplateStatus) {
  return status === 'ENABLED' ? '启用' : '停用'
}

function resolveTemplateStatusVariant(status: NotificationTemplateStatus) {
  return status === 'ENABLED' ? 'secondary' : 'outline'
}

function resolveRecordStatusLabel(status: NotificationRecordStatus) {
  return status === 'SUCCESS' ? '成功' : '失败'
}

function resolveRecordStatusVariant(status: NotificationRecordStatus) {
  return status === 'SUCCESS' ? 'secondary' : 'destructive'
}

function toTemplateFormValues(detail?: NotificationTemplateDetail): TemplateFormValues {
  return {
    templateCode: detail?.templateCode ?? '',
    templateName: detail?.templateName ?? '',
    channelType: detail?.channelType ?? '',
    titleTemplate: detail?.titleTemplate ?? '',
    contentTemplate: detail?.contentTemplate ?? '',
    remark: detail?.remark ?? '',
    enabled: detail?.status ? detail.status === 'ENABLED' : true,
  }
}

function applyTemplateFieldErrors(
  form: UseFormReturn<TemplateFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)
  apiError?.fieldErrors?.forEach((fieldError) => {
    if (
      fieldError.field === 'templateCode' ||
      fieldError.field === 'templateName' ||
      fieldError.field === 'channelType' ||
      fieldError.field === 'titleTemplate' ||
      fieldError.field === 'contentTemplate' ||
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

function buildEmptyTemplatePage(search: ListQuerySearch) {
  return { page: search.page, pageSize: search.pageSize, total: 0, pages: 0, records: [], groups: [] }
}

function buildEmptyRecordPage(search: ListQuerySearch) {
  return { page: search.page, pageSize: search.pageSize, total: 0, pages: 0, records: [], groups: [] }
}

const templateColumns: ColumnDef<NotificationTemplateRecord>[] = [
  {
    accessorKey: 'templateCode',
    header: '模板编码',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.templateCode}</span>
        <span className='text-xs text-muted-foreground'>{row.original.templateId}</span>
      </div>
    ),
  },
  { accessorKey: 'templateName', header: '模板名称' },
  { accessorKey: 'channelType', header: '渠道类型' },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveTemplateStatusVariant(row.original.status)}>
        {resolveTemplateStatusLabel(row.original.status)}
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
            to='/system/notifications/templates/$templateId'
            params={{ templateId: row.original.templateId }}
          >
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/notifications/templates/$templateId/edit'
            params={{ templateId: row.original.templateId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

const recordColumns: ColumnDef<NotificationRecordRecord>[] = [
  {
    accessorKey: 'channelName',
    header: '渠道',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.channelName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.channelCode}
        </span>
      </div>
    ),
  },
  { accessorKey: 'recipient', header: '接收人' },
  { accessorKey: 'title', header: '标题' },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveRecordStatusVariant(row.original.status)}>
        {resolveRecordStatusLabel(row.original.status)}
      </Badge>
    ),
  },
  {
    accessorKey: 'sentAt',
    header: '发送时间',
    cell: ({ row }) => formatDateTime(row.original.sentAt),
  },
  {
    id: 'action',
    header: '操作',
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/notifications/records/$recordId'
            params={{ recordId: row.original.recordId }}
          >
            详情
          </Link>
        </Button>
      </div>
    ),
  },
]

function PageErrorState({
  title,
  description,
  retry,
  listHref,
}: {
  title: string
  description: string
  retry?: () => void
  listHref: string
}) {
  return (
    <PageShell title={title} description={description}>
      <Alert variant='destructive'>
        <AlertCircle />
        <AlertTitle>页面加载失败</AlertTitle>
        <AlertDescription>通知数据请求未成功，请重试或返回列表页。</AlertDescription>
      </Alert>
      <div className='flex flex-wrap gap-2'>
        {retry ? <Button onClick={retry}>重新加载</Button> : null}
        <Button asChild variant='outline'>
          <Link to={listHref}>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
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
              <div key={index} className='space-y-2 rounded-lg border p-4'>
                <Skeleton className='h-4 w-24' />
                <Skeleton className='h-5 w-full' />
              </div>
            ))}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <Skeleton className='h-6 w-32' />
          </CardHeader>
          <CardContent className='space-y-3'>
            <Skeleton className='h-10 w-full' />
            <Skeleton className='h-10 w-full' />
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

export function NotificationTemplatesListPage() {
  const search = templateListRoute.useSearch()
  const navigate = templateListRoute.useNavigate()
  const query = useQuery({
    queryKey: ['notification-templates', search],
    queryFn: () => listNotificationTemplates(search),
  })
  const data = query.data ?? buildEmptyTemplatePage(search)

  return (
    <ResourceListPage
      title='通知模板管理'
      description='管理通知模板的编码、渠道类型、标题模板和内容模板。'
      endpoint='/api/v1/system/notification-templates/page'
      searchPlaceholder='搜索模板编码、名称或标题模板'
      search={search}
      navigate={navigate}
      columns={templateColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '模板总数', value: String(data.total), hint: '模板是通知内容配置入口。' },
        { label: '当前分页', value: String(search.page), hint: '分页参数遵循统一查询契约。' },
        { label: '通知记录', value: '可追踪', hint: '发送记录在记录页单独查询。' },
      ]}
      createAction={{
        label: '新建模板',
        href: '/system/notifications/templates/create',
      }}
    />
  )
}

export function NotificationRecordsListPage() {
  const search = recordListRoute.useSearch()
  const navigate = recordListRoute.useNavigate()
  const query = useQuery({
    queryKey: ['notification-records', search],
    queryFn: () => listNotificationRecords(search),
  })
  const data = query.data ?? buildEmptyRecordPage(search)

  return (
    <ResourceListPage
      title='通知记录管理'
      description='查看通知发送结果、目标用户、渠道和回执信息。'
      endpoint='/api/v1/system/notification-records/page'
      searchPlaceholder='搜索接收人、标题或渠道名称'
      search={search}
      navigate={navigate}
      columns={recordColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '发送记录', value: String(data.total), hint: '这里只读，不支持删改。' },
        { label: '成功状态', value: 'SUCCESS', hint: '失败记录可通过详情查看回执。' },
        { label: '失败状态', value: 'FAILED', hint: '便于排查渠道和模板问题。' },
      ]}
    />
  )
}

export function NotificationTemplateCreatePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const optionsQuery = useQuery<NotificationTemplateFormOptions>({
    queryKey: ['notification-template-options'],
    queryFn: () => getNotificationTemplateFormOptions(),
  })
  const form = useForm<TemplateFormValues>({
    resolver: zodResolver(templateFormSchema),
    defaultValues: {
      templateCode: '',
      templateName: '',
      channelType: '',
      titleTemplate: '',
      contentTemplate: '',
      remark: '',
      enabled: true,
    },
  })

  const createMutation = useMutation({
    mutationFn: createNotificationTemplate,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notification-templates'] })
      toast.success('通知模板已创建')
      startTransition(() => {
        navigate({ to: '/system/notifications/templates/list' })
      })
    },
    onError: (error) => {
      handleServerError(error)
      applyTemplateFieldErrors(form, error)
    },
  })

  return (
    <NotificationTemplateFormShell
      title='新建通知模板'
      description='模板用于统一配置不同渠道的通知文案。'
      backHref='/system/notifications/templates/list'
      form={form}
      optionsQuery={optionsQuery}
      onSubmit={form.handleSubmit((values) =>
        createMutation.mutate({
          templateCode: values.templateCode,
          templateName: values.templateName,
          channelType: values.channelType,
          titleTemplate: values.titleTemplate,
          contentTemplate: values.contentTemplate,
          remark: values.remark || null,
          enabled: values.enabled,
        })
      )}
      submitLabel='创建模板'
      busy={createMutation.isPending}
    />
  )
}

export function NotificationTemplateEditPage() {
  const { templateId } = templateEditRoute.useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const optionsQuery = useQuery({
    queryKey: ['notification-template-options'],
    queryFn: () => getNotificationTemplateFormOptions(),
  })
  const detailQuery = useQuery({
    queryKey: ['notification-templates', templateId],
    queryFn: () => getNotificationTemplateDetail(templateId),
  })
  const form = useForm<TemplateFormValues>({
    resolver: zodResolver(templateFormSchema),
    defaultValues: {
      templateCode: '',
      templateName: '',
      channelType: '',
      titleTemplate: '',
      contentTemplate: '',
      remark: '',
      enabled: true,
    },
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset(toTemplateFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form])

  const updateMutation = useMutation({
    mutationFn: (values: SaveNotificationTemplatePayload) =>
      updateNotificationTemplate(templateId, values),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notification-templates'] })
      await queryClient.invalidateQueries({ queryKey: ['notification-templates', templateId] })
      toast.success('通知模板已更新')
      startTransition(() => {
        navigate({ to: '/system/notifications/templates/$templateId', params: { templateId } })
      })
    },
    onError: (error) => {
      handleServerError(error)
      applyTemplateFieldErrors(form, error)
    },
  })

  if (detailQuery.isLoading || optionsQuery.isLoading) {
    return (
      <PageLoadingState
        title='编辑通知模板'
        description='修改模板编码、渠道类型和内容模板。'
      />
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <PageErrorState
        title='编辑通知模板'
        description='修改模板编码、渠道类型和内容模板。'
        retry={detailQuery.refetch}
        listHref='/system/notifications/templates/list'
      />
    )
  }

  return (
    <NotificationTemplateFormShell
      title='编辑通知模板'
      description='编辑模板时保持模板编码唯一。'
      backHref='/system/notifications/templates/list'
      form={form}
      optionsQuery={optionsQuery}
      onSubmit={form.handleSubmit((values) =>
        updateMutation.mutate({
          templateCode: values.templateCode,
          templateName: values.templateName,
          channelType: values.channelType,
          titleTemplate: values.titleTemplate,
          contentTemplate: values.contentTemplate,
          remark: values.remark || null,
          enabled: values.enabled,
        })
      )}
      submitLabel='保存修改'
      busy={updateMutation.isPending}
    />
  )
}

export function NotificationTemplateDetailPage() {
  const { templateId } = templateDetailRoute.useParams()
  const detailQuery = useQuery({
    queryKey: ['notification-templates', templateId],
    queryFn: () => getNotificationTemplateDetail(templateId),
  })

  if (detailQuery.isLoading) {
    return (
      <PageLoadingState
        title='通知模板详情'
        description='查看模板编码、渠道类型和模板内容。'
      />
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <PageErrorState
        title='通知模板详情'
        description='查看模板编码、渠道类型和模板内容。'
        retry={detailQuery.refetch}
        listHref='/system/notifications/templates/list'
      />
    )
  }

  const detail = detailQuery.data

  return (
    <PageShell
      title='通知模板详情'
      description='展示模板的基础信息、渠道绑定和模板正文。'
      actions={
        <>
          <Button asChild>
            <Link to='/system/notifications/templates/$templateId/edit' params={{ templateId }}>
              <PencilLine className='mr-2 size-4' />
              编辑
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/notifications/templates/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 lg:grid-cols-2'>
        <Card>
          <CardHeader>
            <CardTitle>基础信息</CardTitle>
            <CardDescription>模板的主键和状态。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            <InfoCell label='模板编码' value={detail.templateCode} />
            <InfoCell label='模板名称' value={detail.templateName} />
            <InfoCell label='渠道类型' value={detail.channelType} />
            <InfoCell
              label='状态'
              value={<Badge variant={resolveTemplateStatusVariant(detail.status)}>{resolveTemplateStatusLabel(detail.status)}</Badge>}
            />
            <InfoCell label='创建时间' value={formatDateTime(detail.createdAt)} />
            <InfoCell label='更新时间' value={formatDateTime(detail.updatedAt)} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>模板内容</CardTitle>
            <CardDescription>标题和正文模板。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3'>
            <InfoCell label='标题模板' value={detail.titleTemplate} />
            <InfoCell label='内容模板' value={detail.contentTemplate} />
            <InfoCell label='备注' value={detail.remark || '暂无备注'} />
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

export function NotificationRecordDetailPage() {
  const { recordId } = recordDetailRoute.useParams()
  const detailQuery = useQuery({
    queryKey: ['notification-records', recordId],
    queryFn: () => getNotificationRecordDetail(recordId),
  })

  if (detailQuery.isLoading) {
    return (
      <PageLoadingState
        title='通知记录详情'
        description='查看通知发送回执和投递上下文。'
      />
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <PageErrorState
        title='通知记录详情'
        description='查看通知发送回执和投递上下文。'
        retry={detailQuery.refetch}
        listHref='/system/notifications/records/list'
      />
    )
  }

  const detail = detailQuery.data

  return (
    <PageShell
      title='通知记录详情'
      description='查看发送结果、目标用户、渠道和原始回执。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/notifications/records/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 lg:grid-cols-2'>
        <Card>
          <CardHeader>
            <CardTitle>基础信息</CardTitle>
            <CardDescription>通知发送记录核心字段。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            <InfoCell label='渠道名称' value={detail.channelName} />
            <InfoCell label='渠道编码' value={detail.channelCode} />
            <InfoCell label='渠道类型' value={detail.channelType} />
            <InfoCell label='接收人' value={detail.recipient} />
            <InfoCell label='标题' value={detail.title} />
            <InfoCell label='发送时间' value={formatDateTime(detail.sentAt)} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>回执状态</CardTitle>
            <CardDescription>成功/失败与渠道返回信息。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            <InfoCell
              label='状态'
              value={<Badge variant={resolveRecordStatusVariant(detail.status)}>{resolveRecordStatusLabel(detail.status)}</Badge>}
            />
            <InfoCell label='成功标记' value={detail.success ? 'true' : 'false'} />
            <InfoCell label='Provider' value={detail.providerName} />
            <InfoCell label='回执信息' value={detail.responseMessage} />
            <InfoCell label='渠道地址' value={detail.channelEndpoint || '-'} />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>内容与载荷</CardTitle>
        </CardHeader>
        <CardContent className='grid gap-4'>
          <InfoCell label='内容' value={detail.content} />
          <InfoCell label='Payload' value={<pre className='whitespace-pre-wrap rounded-lg bg-muted p-4 text-xs'>{JSON.stringify(detail.payload, null, 2)}</pre>} />
        </CardContent>
      </Card>
    </PageShell>
  )
}

function NotificationTemplateFormShell({
  title,
  description,
  backHref,
  form,
  optionsQuery,
  onSubmit,
  submitLabel,
  busy,
}: {
  title: string
  description: string
  backHref: string
  form: UseFormReturn<TemplateFormValues>
  optionsQuery: UseQueryResult<NotificationTemplateFormOptions>
  onSubmit: () => void
  submitLabel: string
  busy: boolean
}) {
  const options = optionsQuery.data
  return (
    <PageShell
      title={title}
      description={description}
      actions={
        <Button asChild variant='ghost'>
          <Link to={backHref}>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>模板信息</CardTitle>
          <CardDescription>模板编码、渠道和正文会影响后续发送记录。</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form className='grid gap-4' onSubmit={onSubmit}>
              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='templateCode'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>模板编码</FormLabel>
                      <FormControl>
                        <Input placeholder='LEAVE_APPROVED' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='templateName'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>模板名称</FormLabel>
                      <FormControl>
                        <Input placeholder='请假审批通过' {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='channelType'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>渠道类型</FormLabel>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder='请选择渠道类型' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {options?.channelTypes.map((item) => (
                            <SelectItem key={item.value} value={String(item.value)}>
                              {item.label}
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
                  name='enabled'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>状态</FormLabel>
                      <Select
                        value={field.value ? 'ENABLED' : 'DISABLED'}
                        onValueChange={(value) => field.onChange(value === 'ENABLED')}
                      >
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder='请选择状态' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {options?.statusOptions.map((item) => (
                            <SelectItem key={item.value} value={item.value}>
                              {item.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <FormField
                control={form.control}
                name='titleTemplate'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>标题模板</FormLabel>
                    <FormControl>
                      <Input placeholder='你的请假申请已通过' {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name='contentTemplate'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>内容模板</FormLabel>
                    <FormControl>
                      <Textarea rows={5} placeholder='请假单 {{billNo}} 已审批通过' {...field} />
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
                    <FormLabel>备注</FormLabel>
                    <FormControl>
                      <Textarea rows={3} placeholder='模板用途说明' {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <div className='flex flex-wrap gap-2'>
                <Button type='submit' disabled={busy || optionsQuery.isLoading}>
                  {busy ? <Loader2 className='mr-2 size-4 animate-spin' /> : <Save className='mr-2 size-4' />}
                  {submitLabel}
                </Button>
                <Button asChild variant='outline'>
                  <Link to={backHref}>取消返回列表</Link>
                </Button>
              </div>
            </form>
          </Form>
        </CardContent>
      </Card>
    </PageShell>
  )
}

function InfoCell({
  label,
  value,
}: {
  label: string
  value: ReactNode
}) {
  return (
    <div className='rounded-lg border p-4'>
      <p className='text-sm text-muted-foreground'>{label}</p>
      <div className='mt-2 text-sm font-medium'>{value}</div>
    </div>
  )
}
