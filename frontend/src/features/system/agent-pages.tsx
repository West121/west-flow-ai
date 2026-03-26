import { startTransition, useEffect, useState } from 'react'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch, type UseFormReturn } from 'react-hook-form'
import {
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  Loader2,
  Save,
  ShieldCheck,
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
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { getApiErrorResponse } from '@/lib/api/client'
import {
  createSystemAgent,
  executeSystemHandover,
  getSystemAgentDetail,
  getSystemAgentFormOptions,
  listSystemAgents,
  previewSystemHandover,
  updateSystemAgent,
  type HandoverExecuteResponse,
  type HandoverPreviewResponse,
  type SaveSystemAgentPayload,
  type SystemAgentDetail,
  type SystemAgentRecord,
  type SystemAgentStatus,
  type SystemAgentUserOption,
} from '@/lib/api/system-agents'
import { handleServerError } from '@/lib/handle-server-error'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import {
  normalizeListQuerySearch,
  type ListQuerySearch,
} from '@/features/shared/table/query-contract'

const agentListRoute = getRouteApi('/_authenticated/system/agents/list')

const agentFormSchema = z
  .object({
    sourceUserId: z.string().trim().min(1, '请选择委托人'),
    targetUserId: z.string().trim().min(1, '请选择代理人'),
    description: z.string().max(500, '说明最多 500 个字符'),
    enabled: z.boolean(),
  })
  .superRefine((value, ctx) => {
    // 委托人和代理人不能相同，否则系统侧闭环没有意义。
    if (value.sourceUserId === value.targetUserId) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['targetUserId'],
        message: '代理人不能与委托人相同',
      })
    }
  })

const handoverFormSchema = z
  .object({
    sourceUserId: z.string().trim().min(1, '请选择来源用户'),
    targetUserId: z.string().trim().min(1, '请选择目标用户'),
    comment: z.string().max(500, '说明最多 500 个字符'),
  })
  .superRefine((value, ctx) => {
    // 离职转办必须确保来源和目标不同，避免把任务原地转回。
    if (value.sourceUserId === value.targetUserId) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['targetUserId'],
        message: '目标用户不能与来源用户相同',
      })
    }
  })

type AgentFormValues = z.infer<typeof agentFormSchema>
type HandoverFormValues = z.infer<typeof handoverFormSchema>
type SubmitAction = 'list' | 'continue'

// 代理关系管理页统一用这个方法格式化时间。
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

// 代理关系状态只展示启用/停用。
function resolveAgentStatusLabel(status: SystemAgentStatus) {
  return status === 'ACTIVE' ? '启用' : '停用'
}

// 状态 badge 使用统一的视觉语义。
function resolveAgentStatusVariant(status: SystemAgentStatus) {
  return status === 'ACTIVE' ? 'secondary' : 'outline'
}

// 用户选择项在列表里展开成姓名、账号和部门岗位。
function resolveUserLabel(user?: SystemAgentUserOption | null) {
  if (!user) {
    return '--'
  }

  const suffix = [user.departmentName, user.postName].filter(Boolean).join(' · ')
  return suffix
    ? `${user.displayName} / @${user.username} · ${suffix}`
    : `${user.displayName} / @${user.username}`
}

// 编辑页回填时把详情转换成表单默认值。
function toAgentFormValues(detail?: SystemAgentDetail): AgentFormValues {
  return {
    sourceUserId: detail?.sourceUserId ?? '',
    targetUserId: detail?.targetUserId ?? '',
    description: detail?.description ?? '',
    enabled: detail?.status ? detail.status === 'ACTIVE' : true,
  }
}

// 请求失败时用空分页兜底，避免列表结构抖动。
function buildEmptyAgentPage(search: ListQuerySearch) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

// 从 URL 筛选条件里读取当前状态值。
function getAgentStatusFilterValue(search: ListQuerySearch) {
  const filter = (search.filters ?? []).find((item) => item.field === 'status')
  return typeof filter?.value === 'string' ? filter.value : undefined
}

// 通过 URL 切换状态筛选，便于分享和返回定位。
function updateAgentStatusFilter(
  search: ListQuerySearch,
  navigate: NavigateFn,
  value?: SystemAgentStatus
) {
  // 中文注释：状态筛选直接写回 URL，列表页才能支持分享和返回定位。
  const nextFilters = (search.filters ?? []).filter((item) => item.field !== 'status')
  if (value) {
    nextFilters.push({
      field: 'status',
      operator: 'eq',
      value,
    })
  }

  navigate({
    search: (prev) => {
      const current = prev as ListQuerySearch
      return {
        ...current,
        page: undefined,
        filters: nextFilters.length > 0 ? nextFilters : undefined,
      }
    },
  })
}

// 把代理关系表单的字段错误映射回对应输入框。
function applyAgentFieldErrors(
  form: UseFormReturn<AgentFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    if (
      fieldError.field === 'sourceUserId' ||
      fieldError.field === 'targetUserId' ||
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

// 离职转办表单的字段错误也统一回写到控件上。
function applyHandoverFieldErrors(
  form: UseFormReturn<HandoverFormValues>,
  error: unknown
) {
  const apiError = getApiErrorResponse(error)

  apiError?.fieldErrors?.forEach((fieldError) => {
    if (
      fieldError.field === 'sourceUserId' ||
      fieldError.field === 'targetUserId' ||
      fieldError.field === 'comment'
    ) {
      form.setError(fieldError.field, {
        type: 'server',
        message: fieldError.message,
      })
    }
  })

  return apiError
}

// 页面加载失败时统一给出错误态和返回入口。
function AgentPageErrorState({
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
          代理关系与离职转办数据请求未成功，请重试或先返回列表页。
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

function AgentPageLoadingState({
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

function resolveAgentFormTitle(mode: 'create' | 'edit') {
  return mode === 'create' ? '新建代理关系' : '编辑代理关系'
}

function resolveAgentFormDescription(mode: 'create' | 'edit') {
  return mode === 'create'
    ? '通过系统管理维护委托人与代理人关系，流程中心会据此决定可代理办理范围。'
    : '修改代理关系的委托人、代理人和启用状态，保存后立刻影响流程运行态。'
}

const agentColumns: ColumnDef<SystemAgentRecord>[] = [
  {
    accessorKey: 'sourceUserName',
    header: '委托人',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.sourceUserName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.sourceUserId}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'targetUserName',
    header: '代理人',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.targetUserName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.targetUserId}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveAgentStatusVariant(row.original.status)}>
        {resolveAgentStatusLabel(row.original.status)}
      </Badge>
    ),
  },
  {
    accessorKey: 'description',
    header: '生效说明',
    cell: ({ row }) => row.original.description ?? '--',
  },
  {
    accessorKey: 'updatedAt',
    header: '更新时间',
    cell: ({ row }) => formatDateTime(row.original.updatedAt),
  },
  {
    id: 'actions',
    header: '操作',
    cell: ({ row }) => (
      <div className='flex items-center gap-2'>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/agents/$agentId'
            params={{ agentId: row.original.agentId }}
          >
            详情
          </Link>
        </Button>
        <Button asChild variant='ghost' className='h-8 px-2'>
          <Link
            to='/system/agents/$agentId/edit'
            params={{ agentId: row.original.agentId }}
          >
            编辑
          </Link>
        </Button>
      </div>
    ),
  },
]

function AgentStatusFilterCard({
  search,
  navigate,
  records,
}: {
  search: ListQuerySearch
  navigate: ReturnType<typeof agentListRoute.useNavigate>
  records: SystemAgentRecord[]
}) {
  const statusValue = getAgentStatusFilterValue(search)
  const activeCount = records.filter((item) => item.status === 'ACTIVE').length
  const disabledCount = records.filter((item) => item.status === 'DISABLED').length

  return (
    <Card className='border-dashed'>
      <CardHeader className='gap-3'>
        <div className='flex flex-wrap items-start justify-between gap-3'>
          <div className='space-y-1'>
            <CardTitle className='text-base'>筛选代理关系</CardTitle>
            <CardDescription>
              通过状态快速定位正在生效或已停用的代理关系。
            </CardDescription>
          </div>
          <div className='flex flex-wrap gap-2'>
            <Badge variant='secondary'>
              <ShieldCheck />
              启用 {activeCount}
            </Badge>
            <Badge variant='secondary'>
              <Users />
              停用 {disabledCount}
            </Badge>
          </div>
        </div>
      </CardHeader>
      <CardContent className='flex flex-wrap gap-2'>
        {[
          { label: '全部', value: undefined },
          { label: '启用', value: 'ACTIVE' as const },
          { label: '停用', value: 'DISABLED' as const },
        ].map((item) => (
          <Button
            key={item.label}
            type='button'
            variant={statusValue === item.value ? 'secondary' : 'outline'}
            size='sm'
            onClick={() => updateAgentStatusFilter(search, navigate, item.value)}
          >
            {item.label}
          </Button>
        ))}
      </CardContent>
    </Card>
  )
}

export function AgentsListPage() {
  const search = normalizeListQuerySearch(agentListRoute.useSearch())
  const navigate = agentListRoute.useNavigate()
  const agentsQuery = useQuery({
    queryKey: ['system', 'agent-page', search],
    queryFn: () => listSystemAgents(search),
  })

  const pageData = agentsQuery.data ?? buildEmptyAgentPage(search)
  const activeCount = pageData.records.filter((item) => item.status === 'ACTIVE').length
  const disabledCount = pageData.records.filter((item) => item.status === 'DISABLED').length

  return (
    <>
      <AgentStatusFilterCard
        search={search}
        navigate={navigate}
        records={pageData.records}
      />

      <ResourceListPage
        title='代理关系管理'
        description='独立维护委托人与代理人关系，支持分页、模糊搜索、状态筛选和排序。'
        endpoint='/api/v1/system/agents/page'
        searchPlaceholder='搜索委托人、代理人或生效说明'
        search={search}
        navigate={navigate}
        columns={agentColumns}
        data={pageData.records}
        total={pageData.total}
        summaries={[
          {
            label: '代理关系总量',
            value: String(pageData.total),
            hint: '后端分页接口返回的真实代理关系数量。',
          },
          {
            label: '当前页启用',
            value: String(activeCount),
            hint: '当前页里仍在生效的代理关系数量。',
          },
          {
            label: '当前页停用',
            value: String(disabledCount),
            hint: '当前页里已停用的代理关系数量。',
          },
        ]}
        createAction={{
          label: '新建代理关系',
          href: '/system/agents/create',
        }}
      />
    </>
  )
}

function AgentFormPreviewCard({
  users,
  values,
}: {
  users: SystemAgentUserOption[]
  values: AgentFormValues
}) {
  const sourceUser = users.find((item) => item.id === values.sourceUserId)
  const targetUser = users.find((item) => item.id === values.targetUserId)

  return (
    <Card>
      <CardHeader>
        <CardTitle>当前配置预览</CardTitle>
        <CardDescription>
          这里实时展示表单选择结果，方便管理员确认代理关系是否正确。
        </CardDescription>
      </CardHeader>
      <CardContent className='grid gap-4'>
        <div className='grid gap-3 rounded-lg border bg-muted/20 p-4 text-sm'>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>委托人</span>
            <span className='font-medium'>{resolveUserLabel(sourceUser)}</span>
          </div>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>代理人</span>
            <span className='font-medium'>{resolveUserLabel(targetUser)}</span>
          </div>
          <div className='flex items-center justify-between gap-3'>
            <span className='text-muted-foreground'>状态</span>
            <Badge variant={values.enabled ? 'secondary' : 'outline'}>
              {values.enabled ? '启用' : '停用'}
            </Badge>
          </div>
        </div>
        <p className='text-sm text-muted-foreground'>
          代理关系启用后，流程中心会在任务详情里开放代理代办的可见权限。
        </p>
      </CardContent>
    </Card>
  )
}

function AgentFormPage({
  mode,
  agentId,
}: {
  mode: 'create' | 'edit'
  agentId?: string
}) {
  const navigate = useNavigate() as (opts: {
    to: string
    params?: Record<string, string>
    search?: unknown
    replace?: boolean
  }) => void
  const queryClient = useQueryClient()
  const optionsQuery = useQuery({
    queryKey: ['system', 'agent-form-options'],
    queryFn: () => getSystemAgentFormOptions(),
  })
  const detailQuery = useQuery({
    queryKey: ['system', 'agent-detail', agentId],
    queryFn: () => getSystemAgentDetail(agentId as string),
    enabled: mode === 'edit' && Boolean(agentId),
  })

  const form = useForm<AgentFormValues>({
    resolver: zodResolver(agentFormSchema),
    defaultValues: {
      sourceUserId: '',
      targetUserId: '',
      description: '',
      enabled: true,
    },
  })

  useEffect(() => {
    if (mode === 'edit' && detailQuery.data) {
      form.reset(toAgentFormValues(detailQuery.data))
    }
  }, [detailQuery.data, form, mode])

  const mutation = useMutation({
    mutationFn: async (payload: { values: AgentFormValues; submitAction: SubmitAction }) => {
      const request: SaveSystemAgentPayload = {
        sourceUserId: payload.values.sourceUserId.trim(),
        targetUserId: payload.values.targetUserId.trim(),
        description: payload.values.description?.trim() || undefined,
        enabled: payload.values.enabled,
      }

      return mode === 'create' && !agentId
        ? createSystemAgent(request)
        : updateSystemAgent(agentId as string, request)
    },
    onSuccess: async (result, payload) => {
      await queryClient.invalidateQueries({ queryKey: ['system', 'agent-page'] })
      await queryClient.invalidateQueries({ queryKey: ['system', 'agent-detail'] })

      toast.success(mode === 'create' ? '代理关系已创建' : '代理关系已更新')

      if (payload.submitAction === 'continue') {
        if (mode === 'create') {
          startTransition(() => {
            navigate({
              to: '/system/agents/$agentId/edit',
              params: { agentId: result.agentId },
            })
          })
        }
        return
      }

      startTransition(() => {
        if (mode === 'create') {
          navigate({ to: '/system/agents/list' })
          return
        }

        navigate({
          to: '/system/agents/$agentId',
          params: { agentId: agentId as string },
        })
      })
    },
    onError: (error) => {
      const apiError = applyAgentFieldErrors(form, error)
      if (!apiError) {
        handleServerError(error)
      }
    },
  })

  const onSubmit = form.handleSubmit((values, event) => {
    const submitter = (event?.nativeEvent as { submitter?: HTMLButtonElement } | undefined)
      ?.submitter
    const submitAction: SubmitAction =
      submitter?.dataset.submitAction === 'continue' ? 'continue' : 'list'

    mutation.mutate({ values, submitAction })
  })

  const pageTitle = resolveAgentFormTitle(mode)
  const pageDescription = resolveAgentFormDescription(mode)
  const users = optionsQuery.data?.users ?? []
  const currentValues = useWatch<AgentFormValues>({ control: form.control }) as AgentFormValues

  if (optionsQuery.isError || detailQuery.isError) {
    return (
      <AgentPageErrorState
        title={pageTitle}
        description={pageDescription}
        retry={() => {
          optionsQuery.refetch()
          detailQuery.refetch()
        }}
        listHref='/system/agents/list'
      />
    )
  }

  if (optionsQuery.isLoading || (mode === 'edit' && detailQuery.isLoading)) {
    return <AgentPageLoadingState title={pageTitle} description={pageDescription} />
  }

  return (
    <PageShell
      title={pageTitle}
      description={pageDescription}
      actions={
        <Button asChild variant='outline'>
          <Link search={{}} to='/system/agents/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>代理关系信息</CardTitle>
            <CardDescription>
              代理关系是运行态协作动作的基础配置，这里维护的是系统管理中的真实数据。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form className='grid gap-4 md:grid-cols-2' onSubmit={onSubmit}>
                <FormField
                  control={form.control}
                  name='sourceUserId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>委托人用户</FormLabel>
                      <FormControl>
                        <select
                          className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                          value={field.value}
                          onChange={field.onChange}
                        >
                          <option value=''>请选择委托人用户</option>
                          {users.map((user) => (
                            <option key={user.id} value={user.id}>
                              {resolveUserLabel(user)}
                            </option>
                          ))}
                        </select>
                      </FormControl>
                      <FormDescription>
                        代理关系的原始责任人，流程中心会从这里判断代理范围。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='targetUserId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>代理人用户</FormLabel>
                      <FormControl>
                        <select
                          className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                          value={field.value}
                          onChange={field.onChange}
                        >
                          <option value=''>请选择代理人用户</option>
                          {users.map((user) => (
                            <option key={user.id} value={user.id}>
                              {resolveUserLabel(user)}
                            </option>
                          ))}
                        </select>
                      </FormControl>
                      <FormDescription>
                        代理人启用后，可以代替委托人处理待办任务。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='description'
                  render={({ field }) => (
                    <FormItem className='md:col-span-2'>
                      <FormLabel>生效说明</FormLabel>
                      <FormControl>
                        <Textarea
                          className='min-h-28'
                          placeholder='例如：请假审批和报销审批的代理关系'
                          {...field}
                        />
                      </FormControl>
                      <FormDescription>
                        这里主要记录这条代理关系的业务背景和使用范围。
                      </FormDescription>
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
                        <FormDescription>
                          关闭后该代理关系不会继续参与流程运行态判断。
                        </FormDescription>
                      </div>
                      <FormControl>
                        <Switch checked={field.value} onCheckedChange={field.onChange} />
                      </FormControl>
                    </FormItem>
                  )}
                />

                <div className='flex flex-wrap gap-2 md:col-span-2'>
                  <Button
                    type='submit'
                    variant='outline'
                    disabled={mutation.isPending}
                    data-submit-action='list'
                  >
                    <Save data-icon='inline-start' />
                    保存并返回列表
                  </Button>
                  <Button
                    type='submit'
                    disabled={mutation.isPending}
                    data-submit-action='continue'
                  >
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

        <AgentFormPreviewCard users={users} values={currentValues} />
      </div>
    </PageShell>
  )
}

function AgentDetailMetric({
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
        <Icon />
        <span>{label}</span>
      </div>
      <p className='mt-3 text-sm font-medium'>{value}</p>
    </div>
  )
}

export function AgentCreatePage() {
  return <AgentFormPage mode='create' />
}

export function AgentEditPage({ agentId }: { agentId: string }) {
  return <AgentFormPage mode='edit' agentId={agentId} />
}

export function AgentDetailPage({ agentId }: { agentId: string }) {
  const detailQuery = useQuery({
    queryKey: ['system', 'agent-detail', agentId],
    queryFn: () => getSystemAgentDetail(agentId),
  })

  if (detailQuery.isLoading) {
    return <AgentPageLoadingState title='代理关系详情' description='查看代理关系的委托人与代理人配置。' />
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <AgentPageErrorState
        title='代理关系详情'
        description='查看代理关系的委托人与代理人配置。'
        retry={() => detailQuery.refetch()}
        listHref='/system/agents/list'
      />
    )
  }

  const detail = detailQuery.data

  return (
    <PageShell
      title='代理关系详情'
      description='详情页用于核对委托人与代理人关系是否符合当前流程运行态要求。'
      actions={
        <div className='flex flex-wrap gap-2'>
          <Button asChild variant='outline'>
            <Link search={{}} to='/system/agents/list'>
              <ArrowLeft data-icon='inline-start' />
              返回列表
            </Link>
          </Button>
          <Button asChild>
            <Link to='/system/agents/$agentId/edit' params={{ agentId }} search={{}}>
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
            <CardDescription>
              代理关系保存后会被流程中心、任务详情和离职转办执行页共同使用。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4 md:grid-cols-2'>
            <AgentDetailMetric
              icon={UserRound}
              label='委托人'
              value={`${detail.sourceUserName}（${detail.sourceUserId}）`}
            />
            <AgentDetailMetric
              icon={Users}
              label='代理人'
              value={`${detail.targetUserName}（${detail.targetUserId}）`}
            />
            <AgentDetailMetric
              icon={ShieldCheck}
              label='状态'
              value={resolveAgentStatusLabel(detail.status)}
            />
            <AgentDetailMetric
              icon={BadgeCheck}
              label='更新时间'
              value={formatDateTime(detail.updatedAt)}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>生效说明</CardTitle>
            <CardDescription>管理员在这里快速确认这条代理关系的业务背景。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <div className='rounded-lg border bg-muted/20 p-4 text-sm leading-6 text-muted-foreground'>
              {detail.description ?? '暂无说明'}
            </div>
            <div className='grid gap-2 text-sm'>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>创建时间</span>
                <span>{formatDateTime(detail.createdAt)}</span>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>更新时间</span>
                <span>{formatDateTime(detail.updatedAt)}</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

function renderHandoverTaskPreviewRows(
  preview: HandoverPreviewResponse | HandoverExecuteResponse | undefined
) {
  const tasks = preview?.tasks ?? []

  if (!tasks.length) {
    return (
      <tr>
        <td colSpan={6} className='h-24 text-center text-sm text-muted-foreground'>
          暂无可转办任务
        </td>
      </tr>
    )
  }

  return tasks.map((item) => (
    <TableRow key={item.taskId}>
      <TableCell>{item.processName}</TableCell>
      <TableCell>{item.businessTitle ?? '--'}</TableCell>
      <TableCell>{item.billNo ?? '--'}</TableCell>
      <TableCell>{item.currentNodeName ?? '--'}</TableCell>
      <TableCell>
        <Badge variant={item.canTransfer ? 'secondary' : 'outline'}>
          {item.canTransfer ? '可转办' : '不可转办'}
        </Badge>
      </TableCell>
      <TableCell>{item.reason ?? '--'}</TableCell>
    </TableRow>
  ))
}

export function SystemHandoverExecutePage() {
  const optionsQuery = useQuery({
    queryKey: ['system', 'agent-form-options'],
    queryFn: () => getSystemAgentFormOptions(),
  })
  const [previewResult, setPreviewResult] = useState<HandoverPreviewResponse | null>(null)
  const [previewValueKey, setPreviewValueKey] = useState('')
  const [executeResult, setExecuteResult] = useState<HandoverExecuteResponse | null>(null)
  const [executeValueKey, setExecuteValueKey] = useState('')

  const form = useForm<HandoverFormValues>({
    resolver: zodResolver(handoverFormSchema),
    defaultValues: {
      sourceUserId: '',
      targetUserId: '',
      comment: '',
    },
  })

  const currentValues = useWatch<HandoverFormValues>({ control: form.control }) as HandoverFormValues
  const currentValueKey = [
    currentValues.sourceUserId,
    currentValues.targetUserId,
    currentValues.comment,
  ].join('|')

  const previewMutation = useMutation({
    mutationFn: (payload: { values: HandoverFormValues; valueKey: string }) =>
      previewSystemHandover({
        sourceUserId: payload.values.sourceUserId.trim(),
        targetUserId: payload.values.targetUserId.trim(),
        comment: payload.values.comment?.trim() || undefined,
      }),
    onSuccess: (response, payload) => {
      setPreviewResult(response)
      setPreviewValueKey(payload.valueKey)
      setExecuteResult(null)
      setExecuteValueKey('')
      toast.success('离职转办预览已生成')
    },
    onError: (error) => {
      const apiError = applyHandoverFieldErrors(form, error)
      if (!apiError) {
        handleServerError(error)
      }
    },
  })

  const executeMutation = useMutation({
    mutationFn: (payload: { values: HandoverFormValues; valueKey: string }) =>
      executeSystemHandover({
        sourceUserId: payload.values.sourceUserId.trim(),
        targetUserId: payload.values.targetUserId.trim(),
        comment: payload.values.comment?.trim() || undefined,
      }),
    onSuccess: (response, payload) => {
      setExecuteResult(response)
      setExecuteValueKey(payload.valueKey)
      setPreviewResult({
        sourceUserId: response.sourceUserId,
        targetUserId: response.targetUserId,
        transferableCount: response.transferredCount,
        tasks: response.tasks,
      })
      setPreviewValueKey(payload.valueKey)
      toast.success('离职转办已执行')
    },
    onError: (error) => {
      const apiError = applyHandoverFieldErrors(form, error)
      if (!apiError) {
        handleServerError(error)
      }
    },
  })

  const onPreview = form.handleSubmit((values) => {
    previewMutation.mutate({
      values,
      valueKey: currentValueKey,
    })
  })

  const onExecute = form.handleSubmit((values) => {
    executeMutation.mutate({
      values,
      valueKey: currentValueKey,
    })
  })

  if (optionsQuery.isError) {
    return (
      <AgentPageErrorState
        title='离职转办执行'
        description='输入来源用户与目标用户，预览并执行离职转办。'
        retry={() => optionsQuery.refetch()}
        listHref='/system/agents/list'
      />
    )
  }

  if (optionsQuery.isLoading) {
    return <AgentPageLoadingState title='离职转办执行' description='输入来源用户与目标用户，预览并执行离职转办。' />
  }

  const users = optionsQuery.data?.users ?? []
  const hasFreshPreview = Boolean(previewResult && previewValueKey === currentValueKey)
  const hasFreshExecution = Boolean(
    executeResult && executeValueKey === currentValueKey
  )
  const previewTaskCount =
    hasFreshPreview && previewResult ? previewResult.transferableCount : 0
  const executeTaskCount =
    hasFreshExecution && executeResult ? executeResult.transferredCount : 0
  const visiblePreviewResult =
    hasFreshExecution && executeResult
      ? executeResult
      : hasFreshPreview && previewResult
        ? previewResult
        : undefined

  return (
    <PageShell
      title='离职转办执行'
      description='流程管理员在这里先预览可迁移任务，再确认执行离职转办。'
      actions={
        <Button asChild variant='outline'>
          <Link search={{}} to='/system/agents/list'>
            <ArrowLeft data-icon='inline-start' />
            返回代理关系列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]'>
        <Card>
          <CardHeader>
            <CardTitle>离职转办配置</CardTitle>
            <CardDescription>
              先选择来源用户和目标用户，再预览将被迁移的待办任务。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form className='grid gap-4 md:grid-cols-2' onSubmit={(event) => event.preventDefault()}>
                <FormField
                  control={form.control}
                  name='sourceUserId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>来源用户</FormLabel>
                      <FormControl>
                        <select
                          className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                          value={field.value}
                          onChange={field.onChange}
                        >
                          <option value=''>请选择来源用户</option>
                          {users.map((user) => (
                            <option key={user.id} value={user.id}>
                              {resolveUserLabel(user)}
                            </option>
                          ))}
                        </select>
                      </FormControl>
                      <FormDescription>
                        系统会把这个用户名下的待办任务逐一迁移给目标用户。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='targetUserId'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>目标用户</FormLabel>
                      <FormControl>
                        <select
                          className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                          value={field.value}
                          onChange={field.onChange}
                        >
                          <option value=''>请选择目标用户</option>
                          {users.map((user) => (
                            <option key={user.id} value={user.id}>
                              {resolveUserLabel(user)}
                            </option>
                          ))}
                        </select>
                      </FormControl>
                      <FormDescription>
                        目标用户会接收来源用户当前尚未完成的待办任务。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='comment'
                  render={({ field }) => (
                    <FormItem className='md:col-span-2'>
                      <FormLabel>转办说明</FormLabel>
                      <FormControl>
                        <Textarea
                          className='min-h-28'
                          placeholder='例如：张三离职，统一转到李四'
                          {...field}
                        />
                      </FormControl>
                      <FormDescription>
                        说明会写入执行记录，便于后续审计与追踪。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className='flex flex-wrap gap-2 md:col-span-2'>
                  <Button
                    type='button'
                    variant='outline'
                    disabled={previewMutation.isPending || executeMutation.isPending}
                    onClick={onPreview}
                  >
                    {previewMutation.isPending ? (
                      <>
                        <Loader2 data-icon='inline-start' className='animate-spin' />
                        预览中
                      </>
                    ) : (
                      '预览结果'
                    )}
                  </Button>
                  <Button
                    type='button'
                    disabled={
                      executeMutation.isPending ||
                      previewResult === null ||
                      form.formState.isSubmitting
                    }
                    onClick={onExecute}
                  >
                    {executeMutation.isPending ? (
                      <>
                        <Loader2 data-icon='inline-start' className='animate-spin' />
                        执行中
                      </>
                    ) : (
                      '确认执行'
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
            <CardDescription>
              这里实时展示表单选择结果，以及最近一次的预览或执行结果。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-4'>
            <div className='grid gap-2 rounded-lg border bg-muted/20 p-4 text-sm'>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>来源用户</span>
                <span className='font-medium'>
                  {resolveUserLabel(users.find((item) => item.id === currentValues.sourceUserId))}
                </span>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>目标用户</span>
                <span className='font-medium'>
                  {resolveUserLabel(users.find((item) => item.id === currentValues.targetUserId))}
                </span>
              </div>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-muted-foreground'>当前说明</span>
                <span className='max-w-[220px] truncate font-medium'>
                  {currentValues.comment || '--'}
                </span>
              </div>
            </div>

            {hasFreshPreview ? (
              <Alert>
                <BadgeCheck />
                <AlertTitle>预览完成</AlertTitle>
                <AlertDescription>
                  共预览到 {previewTaskCount} 条可迁移任务。
                </AlertDescription>
              </Alert>
            ) : (
              <Alert>
                <ShieldCheck />
                <AlertTitle>执行前请先预览</AlertTitle>
                <AlertDescription>
                  先生成预览结果，再确认执行，避免把任务迁移到错误用户。
                </AlertDescription>
              </Alert>
            )}

            {hasFreshExecution ? (
              <Alert>
                <BadgeCheck />
                <AlertTitle>执行成功</AlertTitle>
                <AlertDescription>
                  已迁移 {executeTaskCount} 条任务，结果明细如下。
                </AlertDescription>
              </Alert>
            ) : null}
          </CardContent>
        </Card>
      </div>

      <Card className='mt-4'>
        <CardHeader>
          <CardTitle>{hasFreshExecution ? '执行结果明细' : '预览结果明细'}</CardTitle>
          <CardDescription>
            按任务展示离职转办的实际影响范围，便于流程管理员逐条核对。
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>流程名称</TableHead>
                <TableHead>业务标题</TableHead>
                <TableHead>业务单号</TableHead>
                <TableHead>当前节点</TableHead>
                <TableHead>可转办</TableHead>
                <TableHead>说明</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {renderHandoverTaskPreviewRows(
                visiblePreviewResult
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </PageShell>
  )
}
