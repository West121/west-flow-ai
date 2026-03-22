import { startTransition, useEffect, useMemo, useState, type ReactNode } from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, type UseFormReturn } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  Calendar,
  Loader2,
  Mail,
  MessageCircle,
  MessageSquareQuote,
  Save,
  Send,
  SendHorizonal,
  UserRound,
  Users,
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { PageShell } from '@/features/shared/page-shell'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import { getApiErrorResponse } from '@/lib/api/client'
import {
  createSystemMessage,
  getSystemMessageDetail,
  getSystemMessageFormOptions,
  listSystemMessages,
  updateSystemMessage,
  type MessageReadStatus,
  type MessageSelectOption,
  type MessageStatus,
  type MessageTargetType,
  type SystemMessageDetail,
  type SystemMessageFormOptions,
  type SystemMessagePageResponse,
  type SystemMessageRecord,
  type SaveSystemMessagePayload,
} from '@/lib/api/system-messages'
import { handleServerError } from '@/lib/handle-server-error'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'

function MessageFieldRow({
  label,
  value,
  icon,
}: {
  label: string
  value: ReactNode
  icon?: ReactNode
}) {
  return (
    <div className='rounded-lg border bg-muted/20 p-4'>
      <div className='flex items-center gap-2 text-sm text-muted-foreground'>
        {icon}
        <span>{label}</span>
      </div>
      <p className='mt-2 text-sm'>{value}</p>
    </div>
  )
}

const messageTargetTypeSchema = z.union([
  z.literal('ALL'),
  z.literal('USER'),
  z.literal('DEPARTMENT'),
])

const messageFormSchema = z
  .object({
    title: z.string().trim().min(2, '标题至少 2 个字符'),
    content: z.string().trim().min(2, '消息内容至少 2 个字符'),
    status: z.string().trim().min(1, '请选择消息状态'),
    targetType: messageTargetTypeSchema,
    targetUserIdsText: z.string().trim().max(500, '目标用户ID不能为空').optional(),
    targetDepartmentIdsText: z
      .string()
      .trim()
      .max(500, '目标部门ID不能为空')
      .optional(),
    sentAt: z.string().trim().optional(),
  })
  .superRefine((value, ctx) => {
    if (value.targetType === 'USER' && !value.targetUserIdsText?.trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['targetUserIdsText'],
        message: '目标类型为用户时，请填写至少一个用户 ID',
      })
    }

    if (value.targetType === 'DEPARTMENT' && !value.targetDepartmentIdsText?.trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['targetDepartmentIdsText'],
        message: '目标类型为部门时，请填写至少一个部门 ID',
      })
    }
  })

type MessageFormValues = z.infer<typeof messageFormSchema>
type SubmitAction = 'list' | 'continue'

type MessagePageErrorProps = {
  title: string
  description: string
  retry?: () => void
  listHref?: string
}

type MessagePageSearch = {
  search: ListQuerySearch
  navigate: NavigateFn
}

// 格式化时间时统一为中文可读格式，降低字段差异引入的排查成本。
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

function splitIds(value: string | null | undefined): string[] {
  return (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

function toDatetimeLocal(value: string | undefined | null) {
  if (!value) {
    return ''
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return ''
  }

  return new Date(parsed.getTime() - parsed.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 16)
}

function joinIds(ids: string[]) {
  return ids.length > 0 ? ids.join(', ') : ''
}

// 以中文标签显示消息状态和目标类型。
function resolveMessageStatusLabel(status: MessageStatus) {
  return status === 'DRAFT'
    ? '草稿'
    : status === 'SENT'
      ? '已发送'
      : '已取消'
}

function resolveMessageStatusVariant(status: MessageStatus) {
  return status === 'SENT'
    ? 'secondary'
    : status === 'CANCELLED'
      ? 'destructive'
      : 'outline'
}

function resolveTargetTypeLabel(targetType: MessageTargetType) {
  return targetType === 'ALL' ? '全部用户' : targetType === 'USER' ? '指定用户' : '指定部门'
}

function resolveReadStatusLabel(readStatus: MessageReadStatus) {
  return readStatus === 'UNREAD' ? '未读' : '已读'
}

function resolveReadStatusVariant(readStatus: MessageReadStatus) {
  return readStatus === 'READ' ? 'secondary' : 'outline'
}

// 字段级错误映射保证表单体验一致，集中到具体输入框。
function applyMessageFieldErrors(
  form: UseFormReturn<MessageFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    if (
      fieldError.field === 'title' ||
      fieldError.field === 'content' ||
      fieldError.field === 'status' ||
      fieldError.field === 'targetType' ||
      fieldError.field === 'targetUserIdsText' ||
      fieldError.field === 'targetDepartmentIdsText' ||
      fieldError.field === 'sentAt'
    ) {
      form.setError(fieldError.field as keyof MessageFormValues, {
        type: 'server',
        message: fieldError.message,
      })
    }
  })

  return apiError
}

// 列表和详情通用错误态，列表跳回 /system/messages/list。
function MessagePageErrorState({
  title,
  description,
  retry,
  listHref,
}: MessagePageErrorProps) {
  return (
    <PageShell title={title} description={description}>
      <Alert variant='destructive'>
        <AlertCircle />
        <AlertTitle>页面加载失败</AlertTitle>
        <AlertDescription>消息管理请求失败，请重试或返回列表页。</AlertDescription>
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

// 加载骨架和消息表单骨架复用。
function MessagePageLoadingState({
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
          <CardContent className='grid gap-4'>
            {Array.from({ length: 4 }).map((_, index) => (
              <div key={index} className='grid gap-2'>
                <Skeleton className='h-4 w-20' />
                <Skeleton className='h-10 w-full' />
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

const messageColumns: ColumnDef<SystemMessageRecord>[] = [
  {
    accessorKey: 'title',
    header: '标题',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.title}</span>
        <span className='text-xs text-muted-foreground'>ID: {row.original.messageId}</span>
      </div>
    ),
  },
  {
    accessorKey: 'status',
    header: '发送状态',
    cell: ({ row }) => (
      <Badge variant={resolveMessageStatusVariant(row.original.status)}>
        {resolveMessageStatusLabel(row.original.status)}
      </Badge>
    ),
  },
  {
    accessorKey: 'targetType',
    header: '目标类型',
    cell: ({ row }) => resolveTargetTypeLabel(row.original.targetType),
  },
  {
    accessorKey: 'readStatus',
    header: '阅读态',
    cell: ({ row }) => (
      <Badge variant={resolveReadStatusVariant(row.original.readStatus)}>
        {resolveReadStatusLabel(row.original.readStatus)}
      </Badge>
    ),
  },
  {
    accessorKey: 'sentAt',
    header: '发送时间',
    cell: ({ row }) => formatDateTime(row.original.sentAt),
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
          <Link to='/system/messages/$messageId' params={{ messageId: row.original.messageId }}>
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/messages/$messageId/edit'
            params={{ messageId: row.original.messageId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

function buildEmptyMessagePage(search: ListQuerySearch): SystemMessagePageResponse {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

export function MessagesListPage({ search, navigate }: MessagePageSearch) {
  const query = useQuery({
    queryKey: ['system-messages', search],
    queryFn: () => listSystemMessages(search),
  })

  const data = query.data ?? buildEmptyMessagePage(search)

  if (query.isLoading) {
    return (
      <PageShell title='消息管理' description='消息发送列表以独立页管理，可独立分页和查询。'>
        <Skeleton className='h-64 w-full' />
      </PageShell>
    )
  }

  if (query.isError) {
    return (
      <MessagePageErrorState
        title='消息管理'
        description='消息列表未能加载成功。'
        retry={() => void query.refetch()}
      />
    )
  }

  const sentCount = data.records.filter((item) => item.status === 'SENT').length
  const unreadCount = data.records.filter((item) => item.readStatus === 'UNREAD').length
  const draftCount = data.records.filter((item) => item.status === 'DRAFT').length

  return (
    <ResourceListPage
      title='消息管理'
      description='支持按标题检索、分页管理站内消息及发送状态。'
      endpoint='/system/messages/page'
      searchPlaceholder='搜索消息标题'
      search={search}
      navigate={navigate}
      columns={messageColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '消息总量', value: String(data.total), hint: '包含草稿与已发送消息。' },
        { label: '已发送', value: String(sentCount), hint: '当前查询下已确认发送。' },
        { label: '未读', value: String(unreadCount), hint: '待读状态用于运营跟进。' },
        { label: '草稿', value: String(draftCount), hint: '草稿支持保存后继续编辑。' },
      ]}
      createAction={{
        label: '新建消息',
        href: '/system/messages/create',
      }}
    />
  )
}

// 新建与编辑共享逻辑，支持目标类型切换后自动更新输入提示。
function MessageFormPage({ mode, messageId }: { mode: 'create' | 'edit'; messageId?: string }) {
  const isEdit = mode === 'edit'
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [submitAction, setSubmitAction] = useState<SubmitAction>('list')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const form = useForm<MessageFormValues>({
    resolver: zodResolver(messageFormSchema),
    defaultValues: {
      title: '',
      content: '',
      status: 'DRAFT',
      targetType: 'ALL',
      targetUserIdsText: '',
      targetDepartmentIdsText: '',
      sentAt: '',
    },
  })

  const optionsQuery = useQuery<SystemMessageFormOptions>({
    queryKey: ['system-message-form-options'],
    queryFn: getSystemMessageFormOptions,
  })

  const detailQuery = useQuery<SystemMessageDetail>({
    queryKey: ['system-message', messageId],
    enabled: isEdit && Boolean(messageId),
    queryFn: () => getSystemMessageDetail(messageId!),
  })

  useEffect(() => {
    if (detailQuery.data) {
      form.reset({
        title: detailQuery.data.title,
        content: detailQuery.data.content,
        status: detailQuery.data.status as MessageStatus,
        targetType: detailQuery.data.targetType,
        targetUserIdsText: joinIds(detailQuery.data.targetUserIds),
        targetDepartmentIdsText: joinIds(detailQuery.data.targetDepartmentIds),
        sentAt: toDatetimeLocal(detailQuery.data.sentAt),
      })
    }
  }, [detailQuery.data, form])

  const createMutation = useMutation({
    mutationFn: createSystemMessage,
    onError: (error) => {
      const apiError = applyMessageFieldErrors(form, error)
      if (!apiError || !apiError.fieldErrors?.length) {
        setErrorMessage(apiError?.message ?? null)
        handleServerError(error)
      }
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ messageId, payload }: { messageId: string; payload: SaveSystemMessagePayload }) =>
      updateSystemMessage(messageId, payload),
    onError: (error) => {
      const apiError = applyMessageFieldErrors(form, error)
      if (!apiError || !apiError.fieldErrors?.length) {
        setErrorMessage(apiError?.message ?? null)
        handleServerError(error)
      }
    },
  })

  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const isInitialLoading = optionsQuery.isLoading || (isEdit && detailQuery.isLoading)

  function toPayload(values: MessageFormValues): SaveSystemMessagePayload {
    return {
      title: values.title.trim(),
      content: values.content.trim(),
      status: values.status,
      targetType: values.targetType,
      targetUserIds: values.targetType === 'USER' ? splitIds(values.targetUserIdsText) : [],
      targetDepartmentIds:
        values.targetType === 'DEPARTMENT' ? splitIds(values.targetDepartmentIdsText) : [],
      sentAt: values.sentAt ? new Date(values.sentAt).toISOString() : null,
    }
  }

  async function onSubmit(values: MessageFormValues) {
    form.clearErrors()
    setErrorMessage(null)

    try {
      const payload = toPayload(values)
      const result = isEdit
        ? await updateMutation.mutateAsync({ messageId: messageId!, payload })
        : await createMutation.mutateAsync(payload)

      await queryClient.invalidateQueries({ queryKey: ['system-messages'] })
      await queryClient.invalidateQueries({ queryKey: ['system-message', result.messageId] })

      toast.success(isEdit ? '消息已更新' : '消息已创建')

      if (submitAction === 'continue' && isEdit) {
        startTransition(() => {
          navigate({
            to: '/system/messages/$messageId/edit',
            params: { messageId: result.messageId },
          })
        })
        return
      }

      startTransition(() => {
        navigate({ to: '/system/messages/list' })
      })
    } catch (error) {
      if (error instanceof Error) {
        setErrorMessage(error.message)
      }
    }
  }

  const targetType = form.watch('targetType')
  const statusOptions = useMemo(() => optionsQuery.data?.statusOptions ?? [], [optionsQuery.data?.statusOptions])
  const targetTypeOptions = useMemo(() => optionsQuery.data?.targetTypeOptions ?? [], [optionsQuery.data?.targetTypeOptions])

  if (isInitialLoading) {
    return (
      <MessagePageLoadingState
        title={isEdit ? '编辑消息' : '新建消息'}
        description='正在加载消息表单与下拉选项。'
      />
    )
  }

  if (optionsQuery.isError || (isEdit && detailQuery.isError)) {
    return (
      <MessagePageErrorState
        title={isEdit ? '编辑消息' : '新建消息'}
        description='消息表单依赖数据未加载成功。'
        listHref='/system/messages/list'
        retry={() => {
          void optionsQuery.refetch()
          if (isEdit) {
            void detailQuery.refetch()
          }
        }}
      />
    )
  }

  const statusLabelMap = new Map<string, string>(
    statusOptions.map((option: MessageSelectOption) => [option.code, option.name])
  )
  const targetTypeLabelMap = new Map<string, string>(
    targetTypeOptions.map((option: MessageSelectOption) => [option.code, option.name])
  )
  const pageTitle = isEdit ? '编辑消息' : '新建消息'

  return (
    <PageShell
      title={pageTitle}
      description={`${pageTitle}，当前目标类型：${targetTypeLabelMap.get(targetType) || resolveTargetTypeLabel(targetType)}。`}
      actions={
        <>
          <Button
            type='submit'
            form='system-message-form'
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
            form='system-message-form'
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
            <Link to='/system/messages/list'>
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
          <CardDescription>
            当前选中状态：{statusLabelMap.get(form.watch('status')) ?? resolveMessageStatusLabel(form.watch('status') as MessageStatus)}
          </CardDescription>
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
              id='system-message-form'
              className='grid gap-6'
              onSubmit={form.handleSubmit(onSubmit)}
            >
              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='title'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>消息标题</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：任务审批提醒' {...field} />
                      </FormControl>
                      <FormDescription>标题参与列表检索，建议聚焦任务对象。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='status'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>消息状态</FormLabel>
                      <Select
                        value={field.value}
                        onValueChange={(value) => field.onChange(value)}
                      >
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder='请选择消息状态' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {statusOptions.map((option) => (
                            <SelectItem key={option.code} value={option.code}>
                              {option.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormDescription>草稿可反复编辑，发送后请确认状态切换。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <FormField
                control={form.control}
                name='content'
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>消息内容</FormLabel>
                    <FormControl>
                      <Textarea placeholder='填写通知内容，支持自然语言。' rows={5} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <div className='grid gap-4 md:grid-cols-2'>
                <FormField
                  control={form.control}
                  name='targetType'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>目标类型</FormLabel>
                      <Select
                        value={field.value}
                        onValueChange={(value) =>
                          field.onChange(value as MessageTargetType)
                        }
                      >
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder='请选择目标类型' />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {targetTypeOptions.map((option) => (
                            <SelectItem key={option.code} value={option.code}>
                              {option.name}
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
                  name='sentAt'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>计划发送时间</FormLabel>
                      <FormControl>
                        <Input
                          type='datetime-local'
                          value={field.value ?? ''}
                          onChange={(event) => {
                            field.onChange(event.target.value || '')
                          }}
                        />
                      </FormControl>
                      <FormDescription>
                        留空表示立即发送；历史记录按实际生效时间回显。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              {targetType === 'USER' ? (
                <FormField
                  control={form.control}
                  name='targetUserIdsText'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>目标用户 ID（逗号分隔）</FormLabel>
                      <FormControl>
                        <Input
                          placeholder='例如：user-id-1,user-id-2'
                          {...field}
                        />
                      </FormControl>
                      <FormDescription>仅当目标类型为“指定用户”时必填。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              ) : null}

              {targetType === 'DEPARTMENT' ? (
                <FormField
                  control={form.control}
                  name='targetDepartmentIdsText'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>目标部门 ID（逗号分隔）</FormLabel>
                      <FormControl>
                        <Input
                          placeholder='例如：dept-id-1,dept-id-2'
                          {...field}
                        />
                      </FormControl>
                      <FormDescription>仅当目标类型为“指定部门”时必填。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              ) : null}

              <FormField
                control={form.control}
                name='targetUserIdsText'
                render={({ field }) => (
                  <FormItem className={targetType === 'USER' ? '' : 'hidden'}>
                    <FormLabel>目标用户 ID（备份字段）</FormLabel>
                    <FormControl>
                      <Input {...field} readOnly />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name='targetDepartmentIdsText'
                render={({ field }) => (
                  <FormItem className={targetType === 'DEPARTMENT' ? '' : 'hidden'}>
                    <FormLabel>目标部门 ID（备份字段）</FormLabel>
                    <FormControl>
                      <Input {...field} readOnly />
                    </FormControl>
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

export function MessageCreatePage() {
  return <MessageFormPage mode='create' />
}

export function MessageEditPage({ messageId }: { messageId: string }) {
  return <MessageFormPage mode='edit' messageId={messageId} />
}

// 详情页面展示接收范围、发送状态和正文内容。
export function MessageDetailPage({ messageId }: { messageId: string }) {
  const query = useQuery<SystemMessageDetail>({
    queryKey: ['system-message', messageId],
    queryFn: () => getSystemMessageDetail(messageId),
  })

  if (query.isLoading) {
    return (
      <MessagePageLoadingState
        title='消息详情'
        description='正在加载消息详细信息。'
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <MessagePageErrorState
        title='消息详情'
        description='消息详情未能加载成功。'
        listHref='/system/messages/list'
        retry={() => void query.refetch()}
      />
    )
  }

  const detail = query.data
  const targetIcon =
    detail.targetType === 'USER' ? (
      <UserRound className='size-4' />
    ) : detail.targetType === 'DEPARTMENT' ? (
      <Users className='size-4' />
    ) : (
      <SendHorizonal className='size-4' />
    )

  return (
    <PageShell
      title='消息详情'
      description='查看发送标题、内容、目标范围与发送/创建时间。'
      actions={
        <>
          <Button asChild variant='outline'>
            <Link to='/system/messages/$messageId/edit' params={{ messageId }}>
              编辑
            </Link>
          </Button>
          <Button asChild variant='ghost'>
            <Link to='/system/messages/list'>
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
            <CardTitle>消息正文</CardTitle>
            <CardDescription>发送内容与标题采用独立页查看，避免弹窗截断信息。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <MessageFieldRow
              label='标题'
              value={detail.title}
              icon={<MessageCircle className='size-4' />}
            />
            <MessageFieldRow
              label='内容'
              value={detail.content}
              icon={<MessageSquareQuote className='size-4' />}
            />
            <MessageFieldRow
              label='发送时间'
              value={formatDateTime(detail.sentAt)}
              icon={<Calendar className='size-4' />}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>状态与分发范围</CardTitle>
            <CardDescription>消息发送后，目标范围与状态会影响用户侧回执。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <MessageFieldRow
              label='消息状态'
              value={
                <Badge variant={resolveMessageStatusVariant(detail.status)}>
                  {resolveMessageStatusLabel(detail.status)}
                </Badge>
              }
              icon={<Send className='size-4' />}
            />
            <MessageFieldRow
              label='目标类型'
              value={
                <>
                  {targetIcon}
                  <span>{resolveTargetTypeLabel(detail.targetType)}</span>
                </>
              }
              icon={<Mail className='size-4' />}
            />
            <MessageFieldRow
              label='阅读状态'
              value={
                <Badge variant={resolveReadStatusVariant(detail.readStatus)}>
                  {resolveReadStatusLabel(detail.readStatus)}
                </Badge>
              }
              icon={<MessageCircle className='size-4' />}
            />
            <MessageFieldRow
              label='目标用户IDs'
              value={joinIds(detail.targetUserIds) || '-'}
              icon={<UserRound className='size-4' />}
            />
            <MessageFieldRow
              label='目标部门IDs'
              value={joinIds(detail.targetDepartmentIds) || '-'}
              icon={<Users className='size-4' />}
            />
            <MessageFieldRow
              label='创建/更新时间'
              value={`${formatDateTime(detail.createdAt)} / ${formatDateTime(detail.updatedAt)}`}
              icon={<Calendar className='size-4' />}
            />
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}
