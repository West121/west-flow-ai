import {
  useEffect,
  useMemo,
  startTransition,
  useState,
} from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  Loader2,
  Play,
  Sparkles,
  Undo2,
  UserCheck2,
  UserRoundPlus,
} from 'lucide-react'
import { useForm } from 'react-hook-form'
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
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { PageShell } from '@/features/shared/page-shell'
import { ApprovalSheetBusinessSection } from '@/features/oa/detail-sections'
import { ApprovalSheetGraph } from '@/features/workbench/approval-sheet-graph'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { handleServerError } from '@/lib/handle-server-error'
import { NodeFormRenderer } from '@/features/forms/runtime/node-form-renderer'
import { ProcessFormRenderer } from '@/features/forms/runtime/process-form-renderer'
import {
  getApprovalSheetDetailByBusiness,
  claimWorkbenchTask,
  completeWorkbenchTask,
  getWorkbenchTaskActions,
  getWorkbenchTaskDetail,
  listWorkbenchTasks,
  returnWorkbenchTask,
  transferWorkbenchTask,
  type CompleteWorkbenchTaskPayload,
  type WorkbenchTaskDetail,
  type WorkbenchTaskListItem,
} from '@/lib/api/workbench'

const workbenchTodoListRoute = getRouteApi('/_authenticated/workbench/todos/list')

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '--'
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

function resolveTaskStatusLabel(status: WorkbenchTaskListItem['status']) {
  switch (status) {
    case 'PENDING_CLAIM':
      return '待认领'
    case 'PENDING':
      return '待处理'
    case 'TRANSFERRED':
      return '已转办'
    case 'RETURNED':
      return '已退回'
    case 'COMPLETED':
    default:
      return '已完成'
  }
}

function resolveTaskStatusVariant(status: WorkbenchTaskListItem['status']) {
  return status === 'PENDING' || status === 'PENDING_CLAIM'
    ? 'destructive'
    : 'secondary'
}

function summarizeTasks(records: WorkbenchTaskListItem[]) {
  return {
    total: records.length,
    pending: records.filter(
      (record) =>
        record.status === 'PENDING' || record.status === 'PENDING_CLAIM'
    ).length,
    completed: records.filter((record) => record.status === 'COMPLETED').length,
  }
}

function TaskRuntimeFormCard({
  detail,
  hasNodeForm,
  showCompletionForm,
  onSubmit,
  isPending,
}: {
  detail: WorkbenchTaskDetail
  hasNodeForm: boolean
  showCompletionForm: boolean
  onSubmit: (payload: CompleteWorkbenchTaskPayload) => void
  isPending: boolean
}) {
  const form = useForm<TaskActionFormValues>({
    resolver: zodResolver(taskActionSchema),
    defaultValues: {
      action: 'APPROVE',
      comment: '',
    },
  })
  const [taskFormData, setTaskFormData] = useState<Record<string, unknown>>(
    detail.taskFormData ?? detail.formData ?? {}
  )
  const onSubmitForm = form.handleSubmit((payload) => {
    onSubmit({
      action:
        hasNodeForm && taskFormData.approved === false ? 'REJECT' : payload.action,
      comment:
        hasNodeForm && typeof taskFormData.comment === 'string'
          ? taskFormData.comment.trim() || undefined
          : payload.comment?.trim() || undefined,
      taskFormData,
    })
  })

  return (
    <div className='space-y-3 rounded-lg border bg-muted/30 p-4 md:col-span-2'>
      <div className='space-y-1'>
        <p className='text-xs text-muted-foreground'>运行表单</p>
        <p className='text-sm font-medium'>
          {hasNodeForm ? '节点表单优先' : '流程默认表单回退'}
        </p>
      </div>
      {hasNodeForm ? (
        <NodeFormRenderer
          nodeFormKey={detail.nodeFormKey ?? detail.effectiveFormKey}
          nodeFormVersion={detail.nodeFormVersion ?? detail.effectiveFormVersion}
          value={taskFormData}
          onChange={(nextValue) => {
            setTaskFormData(nextValue)
          }}
          fieldBindings={detail.fieldBindings}
          taskFormData={detail.taskFormData ?? undefined}
          disabled={detail.status === 'COMPLETED' || !showCompletionForm}
        />
      ) : (
        <ProcessFormRenderer
          processFormKey={detail.processFormKey}
          processFormVersion={detail.processFormVersion}
          value={taskFormData}
          onChange={(nextValue) => {
            setTaskFormData(nextValue)
          }}
          disabled={detail.status === 'COMPLETED' || !showCompletionForm}
        />
      )}
      <div className='grid gap-2 rounded-md border bg-background p-3 text-xs text-muted-foreground'>
        <div className='flex flex-wrap gap-3'>
          <span>
            流程默认：{detail.processFormKey} · {detail.processFormVersion}
          </span>
          <span>生效表单：{detail.effectiveFormKey} · {detail.effectiveFormVersion}</span>
        </div>
        <p>提交时会同步带上 `taskFormData`。</p>
      </div>
      {showCompletionForm ? (
        <Form {...form}>
          <form className='space-y-6 rounded-lg border p-4' onSubmit={onSubmitForm}>
            <div className='space-y-1'>
              <p className='text-sm font-medium'>审批处理</p>
              <p className='text-sm text-muted-foreground'>
                选择审批动作并填写意见，完成后会自动跳转到下一个待办或返回列表。
              </p>
            </div>
            {!hasNodeForm ? (
              <>
                <FormField
                  control={form.control}
                  name='action'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>审批动作</FormLabel>
                      <FormControl>
                        <select
                          className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                          {...field}
                        >
                          <option value='APPROVE'>同意通过</option>
                          <option value='REJECT'>驳回</option>
                        </select>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='comment'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>审批意见</FormLabel>
                      <FormControl>
                        <Textarea
                          className='min-h-36'
                          placeholder='请输入审批意见'
                          {...field}
                        />
                      </FormControl>
                      <FormDescription>审批意见会随任务完成状态一起保存。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            ) : (
              <div className='rounded-lg border border-dashed p-3 text-sm text-muted-foreground'>
                节点表单已接管审批结果与意见输入，提交时会自动根据表单值生成通过/驳回动作。
              </div>
            )}

            <div className='flex items-center gap-3'>
              <Button
                type='submit'
                disabled={isPending || detail.status === 'COMPLETED'}
              >
                {isPending ? (
                  <>
                    <Loader2 className='animate-spin' />
                    提交中
                  </>
                ) : (
                  '完成任务'
                )}
              </Button>
              <Button asChild type='button' variant='outline'>
                <Link to='/workbench/todos/list'>返回列表</Link>
              </Button>
            </div>
          </form>
        </Form>
      ) : null}
    </div>
  )
}

const todoColumns: ColumnDef<WorkbenchTaskListItem>[] = [
  {
    accessorKey: 'processName',
    header: '流程标题',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.processName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.processKey}
        </span>
      </div>
    ),
  },
  {
    accessorKey: 'applicantUserId',
    header: '发起人',
  },
  {
    accessorKey: 'nodeName',
    header: '当前节点',
  },
  {
    accessorKey: 'businessKey',
    header: '业务单号',
    cell: ({ row }) => row.original.businessKey ?? '--',
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveTaskStatusVariant(row.original.status)}>
        {resolveTaskStatusLabel(row.original.status)}
      </Badge>
    ),
  },
  {
    id: 'createdAt',
    accessorKey: 'createdAt',
    header: '创建时间',
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
  {
    id: 'actions',
    header: '操作',
    cell: ({ row }) => (
      <Button asChild size='sm' variant='outline'>
        <Link to='/workbench/todos/$taskId' params={{ taskId: row.original.taskId }}>
          处理
        </Link>
      </Button>
    ),
  },
]

const taskActionSchema = z.object({
  action: z.enum(['APPROVE', 'REJECT']),
  comment: z.string().max(500, '审批意见最多 500 个字符').default(''),
})

type TaskActionFormValues = z.input<typeof taskActionSchema>

const claimTaskSchema = z.object({
  comment: z.string().max(500, '认领说明最多 500 个字符').default(''),
})

type ClaimTaskFormValues = z.input<typeof claimTaskSchema>

const transferTaskSchema = z.object({
  targetUserId: z.string().trim().min(1, '请输入目标用户编码'),
  comment: z.string().max(500, '转办说明最多 500 个字符').default(''),
})

type TransferTaskFormValues = z.input<typeof transferTaskSchema>

const returnTaskSchema = z.object({
  comment: z.string().max(500, '退回说明最多 500 个字符').default(''),
})

type ReturnTaskFormValues = z.input<typeof returnTaskSchema>

export function Dashboard() {
  return (
    <PageShell
      title='平台总览'
      description='工作台入口已经连接到真实的流程发起、待办列表和任务处理闭环。'
      actions={
        <>
          <Button asChild>
            <Link to='/workbench/start'>发起流程</Link>
          </Button>
          <Button asChild variant='outline'>
            <Link to='/workbench/todos/list'>进入待办列表</Link>
          </Button>
        </>
      }
    >
      <div className='grid gap-4 lg:grid-cols-4'>
        {[
          {
            title: '今日待办',
            value: '实时',
            description: '待办列表已经接入后端分页接口，支持关键词检索和跳转处理页。',
            icon: Clock3,
          },
          {
            title: '流程发起',
            value: '闭环',
            description: '提供独立发起页，提交后可直接进入首个待处理任务。',
            icon: Play,
          },
          {
            title: '已完成审批',
            value: '可追踪',
            description: '任务处理后可再次回查详情，保留完成状态和审批意见。',
            icon: CheckCircle2,
          },
          {
            title: 'AI 入口',
            value: '1',
            description: '后续统一从 Copilot 进入，不再拆散到多个入口。',
            icon: Sparkles,
          },
        ].map((item) => (
          <Card key={item.title}>
            <CardHeader className='gap-3'>
              <div className='flex items-center justify-between gap-3'>
                <CardDescription>{item.title}</CardDescription>
                <item.icon className='text-muted-foreground' />
              </div>
              <CardTitle className='text-3xl'>{item.value}</CardTitle>
            </CardHeader>
            <CardContent className='text-sm text-muted-foreground'>
              {item.description}
            </CardContent>
          </Card>
        ))}
      </div>

      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <Card>
          <CardHeader>
            <CardTitle>工作台入口</CardTitle>
            <CardDescription>
              左侧导航保持中文菜单，工作台只聚焦流程发起、待办处理和运行态回查。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            {[
              ['工作台待办列表', '/workbench/todos/list'],
              ['发起流程', '/workbench/start'],
              ['系统用户列表', '/system/users/list'],
              ['流程定义列表', '/workflow/definitions/list'],
            ].map(([label, href]) => (
              <Button key={href} asChild variant='outline' className='justify-start'>
                <Link to={href}>{label}</Link>
              </Button>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>运行态约束</CardTitle>
            <CardDescription>先打通最小闭环，再扩展认领、加签、回退、跳转等复杂动作。</CardDescription>
          </CardHeader>
          <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
            <p>1. 待办列表和处理页都走真实接口，不再使用静态占位数据。</p>
            <p>2. 流程发起页支持提交 JSON 表单数据，发起后直接跳到首个任务。</p>
            <p>3. 任务详情页会回显实例信息、审批意见和完成状态，便于调试闭环。</p>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

export function WorkbenchTodoListPage() {
  const search = workbenchTodoListRoute.useSearch()
  const navigate = workbenchTodoListRoute.useNavigate()
  const tasksQuery = useQuery({
    queryKey: ['workbench', 'todo-page', search],
    queryFn: () => listWorkbenchTasks(search),
  })

  const pageData = tasksQuery.data ?? {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
  const summary = summarizeTasks(pageData.records)

  return (
    <>
      {tasksQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>待办列表加载失败</AlertTitle>
          <AlertDescription>
            {tasksQuery.error instanceof Error
              ? tasksQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <ResourceListPage
        title='工作台待办列表'
        description='展示当前运行态待办任务，支持关键词搜索、分页和跳转到独立处理页。'
        endpoint='/api/v1/process-runtime/demo/tasks/page'
        searchPlaceholder='搜索流程标题、节点名称、发起人或业务单号'
        search={search}
        navigate={navigate}
        columns={todoColumns}
        data={pageData.records}
        total={pageData.total}
        summaries={[
          {
            label: '待办总量',
            value: String(pageData.total),
            hint: '后端分页接口返回的真实总量。',
          },
          {
            label: '当前页待处理',
            value: String(summary.pending),
            hint: '当前页里仍待审批的任务数量。',
          },
          {
            label: '当前页已完成',
            value: String(summary.completed),
            hint: '用于快速确认闭环任务是否已处理。',
          },
        ]}
        createAction={{
          label: '发起流程',
          href: '/workbench/start',
        }}
      />
    </>
  )
}

export function WorkbenchStartPage() {
  return (
    <PageShell
      title='发起流程'
      description='先选择业务入口，再进入对应的 OA 发起页。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/workbench/todos/list'>
            <ArrowLeft />
            返回待办列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <Card>
          <CardHeader>
            <CardTitle>业务入口选择</CardTitle>
            <CardDescription>
              流程中心发起不再直接输入流程标识，而是先选业务入口。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2'>
            {[
              ['请假申请', '/oa/leave/create'],
              ['报销申请', '/oa/expense/create'],
              ['通用申请', '/oa/common/create'],
              ['OA 流程查询', '/oa/query'],
            ].map(([label, href]) => (
              <Button key={href} asChild variant='outline' className='justify-start'>
                <Link to={href as '/oa/leave/create' | '/oa/expense/create' | '/oa/common/create' | '/oa/query'}>
                  {label}
                </Link>
              </Button>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>流程中心说明</CardTitle>
            <CardDescription>统一待办、发起和查询入口，避免拆成多套流程中心。</CardDescription>
          </CardHeader>
          <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
            <p>1. OA 业务入口先保存业务单据，再自动匹配流程绑定。</p>
            <p>2. 发起成功后优先进入业务审批单详情页；没有待办时也能查看流程轨迹。</p>
            <p>3. 流程中心待办列表仍然保留在工作台中，方便统一处理。</p>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

type WorkbenchTodoDetailPageProps = {
  taskId?: string
  businessType?: string
  businessId?: string
  backHref?: '/workbench/todos/list' | '/oa/query'
  backLabel?: string
}

export function WorkbenchTodoDetailPage({
  taskId,
  businessType,
  businessId,
  backHref = '/workbench/todos/list',
  backLabel = '返回待办列表',
}: WorkbenchTodoDetailPageProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [transferDialogOpen, setTransferDialogOpen] = useState(false)
  const [returnDialogOpen, setReturnDialogOpen] = useState(false)
  const locator = taskId
    ? { mode: 'task' as const, taskId }
    : businessType && businessId
      ? {
          mode: 'business' as const,
          businessType,
          businessId,
        }
      : null

  if (!locator) {
    throw new Error('审批单详情页需要 taskId 或 business locator')
  }

  const detailQueryKey = useMemo(
    () =>
      locator.mode === 'task'
        ? (['workbench', 'todo-detail', locator.taskId] as const)
        : ([
            'workbench',
            'approval-sheet-detail',
            locator.businessType,
            locator.businessId,
          ] as const),
    [locator.businessId, locator.businessType, locator.mode, locator.taskId]
  )

  const detailQuery = useQuery({
    queryKey: detailQueryKey,
    queryFn: () =>
      locator.mode === 'task'
        ? getWorkbenchTaskDetail(locator.taskId)
        : getApprovalSheetDetailByBusiness({
            businessType: locator.businessType,
            businessId: locator.businessId,
          }),
  })
  const detail = detailQuery.data
  const resolvedTaskId =
    detail?.activeTaskIds[0] ?? (locator.mode === 'task' ? locator.taskId : null)
  const actionsQuery = useQuery({
    queryKey: ['workbench', 'todo-actions', resolvedTaskId ?? 'none'],
    queryFn: () => getWorkbenchTaskActions(resolvedTaskId as string),
    enabled: Boolean(resolvedTaskId),
  })

  function requireActionTaskId() {
    if (!resolvedTaskId) {
      throw new Error('当前审批单没有可操作任务')
    }

    return resolvedTaskId
  }

  const claimForm = useForm<ClaimTaskFormValues>({
    resolver: zodResolver(claimTaskSchema),
    defaultValues: {
      comment: '',
    },
  })
  const transferForm = useForm<TransferTaskFormValues>({
    resolver: zodResolver(transferTaskSchema),
    defaultValues: {
      targetUserId: '',
      comment: '',
    },
  })
  const returnForm = useForm<ReturnTaskFormValues>({
    resolver: zodResolver(returnTaskSchema),
    defaultValues: {
      comment: '',
    },
  })

  useEffect(() => {
    if (!detail) {
      return
    }

    claimForm.reset({
      comment: '',
    })
    transferForm.reset({
      targetUserId: '',
      comment: '',
    })
    returnForm.reset({
      comment: '',
    })
  }, [claimForm, detail, returnForm, transferForm])

  async function refreshWorkbenchQueries() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['workbench', 'todo-page'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'todo-detail'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'approval-sheet-detail'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'todo-actions'] }),
    ])
  }

  const completeMutation = useMutation({
    mutationFn: (payload: CompleteWorkbenchTaskPayload) =>
      completeWorkbenchTask(requireActionTaskId(), payload),
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      if (locator.mode === 'task') {
        const nextTask = response.nextTasks[0]
        if (nextTask) {
          startTransition(() => {
            navigate({
              to: '/workbench/todos/$taskId',
              params: { taskId: nextTask.taskId },
            })
          })
          return
        }

        startTransition(() => {
          navigate({ to: '/workbench/todos/list' })
        })
      }
    },
    onError: handleServerError,
  })
  const claimMutation = useMutation({
    mutationFn: (payload: ClaimTaskFormValues) =>
      claimWorkbenchTask(requireActionTaskId(), {
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      claimForm.reset({
        comment: '',
      })
    },
    onError: handleServerError,
  })
  const transferMutation = useMutation({
    mutationFn: (payload: TransferTaskFormValues) =>
      transferWorkbenchTask(requireActionTaskId(), {
        targetUserId: payload.targetUserId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setTransferDialogOpen(false)
      transferForm.reset({
        targetUserId: '',
        comment: '',
      })
      if (locator.mode === 'task') {
        startTransition(() => {
          navigate({ to: '/workbench/todos/list' })
        })
      }
    },
    onError: handleServerError,
  })
  const returnMutation = useMutation({
    mutationFn: (payload: ReturnTaskFormValues) =>
      returnWorkbenchTask(requireActionTaskId(), {
        targetStrategy: 'PREVIOUS_USER_TASK',
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setReturnDialogOpen(false)
      returnForm.reset({
        comment: '',
      })
      if (locator.mode === 'task') {
        startTransition(() => {
          navigate({ to: '/workbench/todos/list' })
        })
      }
    },
    onError: handleServerError,
  })

  const actionLabel = useMemo(() => {
    if (!detail) {
      return ''
    }

    return resolveTaskStatusLabel(detail.status)
  }, [detail])

  const onClaimSubmit = claimForm.handleSubmit((values) => {
    claimMutation.mutate(values)
  })
  const onTransferSubmit = transferForm.handleSubmit((values) => {
    transferMutation.mutate(values)
  })
  const onReturnSubmit = returnForm.handleSubmit((values) => {
    returnMutation.mutate(values)
  })
  const showCompletionForm = Boolean(
    actionsQuery.data?.canApprove || actionsQuery.data?.canReject
  )
  const hasNodeForm = Boolean(detail?.nodeFormKey && detail?.nodeFormVersion)

  return (
    <PageShell
      title='审批单详情'
      description='统一审批单详情页，支持业务正文、流程回顾和运行态处理动作。'
      actions={
        <Button asChild variant='outline'>
          <Link to={backHref}>
            <ArrowLeft />
            {backLabel}
          </Link>
        </Button>
      }
    >
      {detailQuery.isError ? (
        <Alert variant='destructive'>
          <AlertTitle>任务详情加载失败</AlertTitle>
          <AlertDescription>
            {detailQuery.error instanceof Error
              ? detailQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}

      {detailQuery.isLoading ? (
        <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
          <Card>
            <CardHeader>
              <Skeleton className='h-8 w-40' />
              <Skeleton className='h-4 w-80' />
            </CardHeader>
            <CardContent className='space-y-3'>
              <Skeleton className='h-10 w-full' />
              <Skeleton className='h-10 w-full' />
              <Skeleton className='h-10 w-full' />
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <Skeleton className='h-8 w-40' />
              <Skeleton className='h-4 w-60' />
            </CardHeader>
            <CardContent className='space-y-3'>
              <Skeleton className='h-32 w-full' />
            </CardContent>
          </Card>
        </div>
      ) : null}

      {detail ? (
        <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
          <Card>
            <CardHeader>
              <div className='flex items-center justify-between gap-3'>
                <div>
                  <CardTitle className='flex items-center gap-3'>
                    {detail.processName}
                    <Badge
                      variant={
                        detail.status === 'PENDING' ||
                        detail.status === 'PENDING_CLAIM'
                          ? 'destructive'
                          : 'secondary'
                      }
                    >
                      {actionLabel}
                    </Badge>
                  </CardTitle>
                  <CardDescription className='mt-2'>
                    {detail.processKey} · {detail.nodeName}
                  </CardDescription>
                </div>
                <Badge variant='outline'>{detail.instanceStatus}</Badge>
              </div>
            </CardHeader>
            <CardContent className='grid gap-4 md:grid-cols-2'>
              <div className='space-y-2 rounded-lg border bg-muted/30 p-4'>
                <p className='text-xs text-muted-foreground'>任务信息</p>
                <dl className='space-y-2 text-sm'>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>任务编号</dt>
                    <dd className='font-mono'>{detail.taskId}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>实例编号</dt>
                    <dd className='font-mono'>{detail.instanceId}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>业务单号</dt>
                    <dd>{detail.businessKey ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>发起人</dt>
                    <dd>{detail.applicantUserId}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>创建时间</dt>
                    <dd>{formatDateTime(detail.createdAt)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>完成时间</dt>
                    <dd>{formatDateTime(detail.completedAt)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>接收时间</dt>
                    <dd>{formatDateTime(detail.receiveTime)}</dd>
                  </div>
                </dl>
              </div>

              <div className='space-y-2 rounded-lg border bg-muted/30 p-4'>
                <p className='text-xs text-muted-foreground'>节点信息</p>
                <dl className='space-y-2 text-sm'>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>当前节点</dt>
                    <dd>{detail.nodeName}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>处理方式</dt>
                    <dd>{detail.assignmentMode ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>当前办理人</dt>
                    <dd>{detail.assigneeUserId ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>候选人</dt>
                    <dd>{detail.candidateUserIds.join('、') || '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>最新动作</dt>
                    <dd>{detail.action ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>办理人</dt>
                    <dd>{detail.operatorUserId ?? '--'}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>当前待办</dt>
                    <dd>{detail.activeTaskIds.length}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>阅读时间</dt>
                    <dd>{formatDateTime(detail.readTime)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>办理开始</dt>
                    <dd>{formatDateTime(detail.handleStartTime)}</dd>
                  </div>
                  <div className='flex justify-between gap-3'>
                    <dt className='text-muted-foreground'>办理时长</dt>
                    <dd>
                      {detail.handleDurationSeconds === null || detail.handleDurationSeconds === undefined
                        ? '--'
                        : `${detail.handleDurationSeconds} 秒`}
                    </dd>
                  </div>
                </dl>
              </div>

              <div className='md:col-span-2 grid gap-4 xl:grid-cols-2'>
                <ApprovalSheetBusinessSection detail={detail} />
                <ApprovalSheetGraph
                  flowNodes={detail.flowNodes ?? []}
                  flowEdges={detail.flowEdges ?? []}
                  taskTrace={detail.taskTrace ?? []}
                  instanceEvents={detail.instanceEvents ?? []}
                />
              </div>

              <TaskRuntimeFormCard
                key={`${detail.taskId}:${detail.updatedAt}:${detail.effectiveFormKey}:${detail.effectiveFormVersion}`}
                detail={detail}
                hasNodeForm={hasNodeForm}
                showCompletionForm={showCompletionForm}
                onSubmit={(payload) => completeMutation.mutate(payload)}
                isPending={completeMutation.isPending}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>任务动作</CardTitle>
              <CardDescription>
                根据当前任务权限显示可执行动作，完成后会自动刷新详情或返回待办列表。
              </CardDescription>
            </CardHeader>
            <CardContent className='space-y-6'>
              {actionsQuery.isLoading ? (
                <div className='space-y-3'>
                  <Skeleton className='h-10 w-full' />
                  <Skeleton className='h-24 w-full' />
                  <Skeleton className='h-10 w-32' />
                </div>
              ) : null}

              {actionsQuery.data?.canClaim ? (
                <Form {...claimForm}>
                  <form className='space-y-4 rounded-lg border p-4' onSubmit={onClaimSubmit}>
                    <div className='space-y-1'>
                      <p className='text-sm font-medium'>认领任务</p>
                      <p className='text-sm text-muted-foreground'>
                        当前任务在公共认领池中，认领后会转为你的个人待办。
                      </p>
                    </div>
                    <FormField
                      control={claimForm.control}
                      name='comment'
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>认领说明</FormLabel>
                          <FormControl>
                            <Textarea
                              className='min-h-24'
                              placeholder='可选，填写认领说明'
                              {...field}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <Button type='submit' disabled={claimMutation.isPending}>
                      {claimMutation.isPending ? (
                        <>
                          <Loader2 className='animate-spin' />
                          认领中
                        </>
                      ) : (
                        <>
                          <UserCheck2 />
                          认领任务
                        </>
                      )}
                    </Button>
                  </form>
                </Form>
              ) : null}

              {actionsQuery.data?.canTransfer ? (
                <div className='rounded-lg border p-4'>
                  <div className='mb-4 space-y-1'>
                    <p className='text-sm font-medium'>转办</p>
                    <p className='text-sm text-muted-foreground'>
                      将当前任务转交给其他用户继续处理，并保留转办轨迹。
                    </p>
                  </div>
                  <Dialog open={transferDialogOpen} onOpenChange={setTransferDialogOpen}>
                    <DialogTrigger asChild>
                      <Button type='button' variant='outline'>
                        <UserRoundPlus />
                        转办任务
                      </Button>
                    </DialogTrigger>
                    <DialogContent>
                      <DialogHeader>
                        <DialogTitle>转办任务</DialogTitle>
                        <DialogDescription>
                          输入目标用户编码后，当前任务会转到对方待办中。
                        </DialogDescription>
                      </DialogHeader>
                      <Form {...transferForm}>
                        <form className='space-y-4' onSubmit={onTransferSubmit}>
                          <FormField
                            control={transferForm.control}
                            name='targetUserId'
                            render={({ field }) => (
                              <FormItem>
                                <FormLabel>目标用户编码</FormLabel>
                                <FormControl>
                                  <Input placeholder='例如：usr_003' {...field} />
                                </FormControl>
                                <FormMessage />
                              </FormItem>
                            )}
                          />
                          <FormField
                            control={transferForm.control}
                            name='comment'
                            render={({ field }) => (
                              <FormItem>
                                <FormLabel>转办说明</FormLabel>
                                <FormControl>
                                  <Textarea
                                    className='min-h-24'
                                    placeholder='请输入转办说明'
                                    {...field}
                                  />
                                </FormControl>
                                <FormMessage />
                              </FormItem>
                            )}
                          />
                          <DialogFooter>
                            <Button
                              type='button'
                              variant='outline'
                              onClick={() => setTransferDialogOpen(false)}
                            >
                              取消
                            </Button>
                            <Button type='submit' disabled={transferMutation.isPending}>
                              {transferMutation.isPending ? (
                                <>
                                  <Loader2 className='animate-spin' />
                                  转办中
                                </>
                              ) : (
                                '确认转办'
                              )}
                            </Button>
                          </DialogFooter>
                        </form>
                      </Form>
                    </DialogContent>
                  </Dialog>
                </div>
              ) : null}

              {actionsQuery.data?.canReturn ? (
                <div className='rounded-lg border p-4'>
                  <div className='mb-4 space-y-1'>
                    <p className='text-sm font-medium'>退回上一步</p>
                    <p className='text-sm text-muted-foreground'>
                      当前版本只支持退回到上一步人工节点，并生成新的待处理任务。
                    </p>
                  </div>
                  <Dialog open={returnDialogOpen} onOpenChange={setReturnDialogOpen}>
                    <DialogTrigger asChild>
                      <Button type='button' variant='outline'>
                        <Undo2 />
                        退回上一步
                      </Button>
                    </DialogTrigger>
                    <DialogContent>
                      <DialogHeader>
                        <DialogTitle>退回上一步</DialogTitle>
                        <DialogDescription>
                          请填写退回说明，系统会把任务重新投递到上一步办理人。
                        </DialogDescription>
                      </DialogHeader>
                      <Form {...returnForm}>
                        <form className='space-y-4' onSubmit={onReturnSubmit}>
                          <FormField
                            control={returnForm.control}
                            name='comment'
                            render={({ field }) => (
                              <FormItem>
                                <FormLabel>退回说明</FormLabel>
                                <FormControl>
                                  <Textarea
                                    className='min-h-24'
                                    placeholder='请输入退回说明'
                                    {...field}
                                  />
                                </FormControl>
                                <FormMessage />
                              </FormItem>
                            )}
                          />
                          <DialogFooter>
                            <Button
                              type='button'
                              variant='outline'
                              onClick={() => setReturnDialogOpen(false)}
                            >
                              取消
                            </Button>
                            <Button type='submit' disabled={returnMutation.isPending}>
                              {returnMutation.isPending ? (
                                <>
                                  <Loader2 className='animate-spin' />
                                  退回中
                                </>
                              ) : (
                                '确认退回'
                              )}
                            </Button>
                          </DialogFooter>
                        </form>
                      </Form>
                    </DialogContent>
                  </Dialog>
                </div>
              ) : null}

              {!actionsQuery.isLoading &&
              !actionsQuery.isError &&
              !actionsQuery.data?.canClaim &&
              !actionsQuery.data?.canTransfer &&
              !actionsQuery.data?.canReturn &&
              !showCompletionForm ? (
                <Alert>
                  <AlertTitle>当前没有可执行动作</AlertTitle>
                  <AlertDescription>
                    该任务可能已完成，或者当前登录用户不具备处理权限。
                  </AlertDescription>
                </Alert>
              ) : null}
            </CardContent>
          </Card>
        </div>
      ) : null}
    </PageShell>
  )
}
