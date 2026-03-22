import { useEffect, useMemo, startTransition } from 'react'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { type ColumnDef } from '@tanstack/react-table'
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  Loader2,
  Play,
  Sparkles,
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
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { handleServerError } from '@/lib/handle-server-error'
import {
  completeWorkbenchTask,
  getWorkbenchTaskDetail,
  listWorkbenchTasks,
  startWorkbenchProcess,
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
  return status === 'PENDING' ? '待处理' : '已完成'
}

function resolveTaskStatusVariant(status: WorkbenchTaskListItem['status']) {
  return status === 'PENDING' ? 'destructive' : 'secondary'
}

function summarizeTasks(records: WorkbenchTaskListItem[]) {
  return {
    total: records.length,
    pending: records.filter((record) => record.status === 'PENDING').length,
    completed: records.filter((record) => record.status === 'COMPLETED').length,
  }
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

const startProcessSchema = z.object({
  processKey: z.string().min(1, '请输入流程标识'),
  businessKey: z.string().optional(),
  formDataJson: z.string().min(2, '请输入 JSON 格式的流程表单数据'),
})

type StartProcessFormValues = z.infer<typeof startProcessSchema>

const taskActionSchema = z.object({
  action: z.enum(['APPROVE', 'REJECT']),
  comment: z.string().max(500, '审批意见最多 500 个字符').default(''),
})

type TaskActionFormValues = z.input<typeof taskActionSchema>

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
  const navigate = useNavigate()
  const form = useForm<StartProcessFormValues>({
    resolver: zodResolver(startProcessSchema),
    defaultValues: {
      processKey: 'oa_leave',
      businessKey: `biz_${new Date().getTime()}`,
      formDataJson: JSON.stringify(
        {
          days: 3,
          reason: '请假',
        },
        null,
        2
      ),
    },
  })

  const startMutation = useMutation({
    mutationFn: startWorkbenchProcess,
    onSuccess: (response) => {
      const firstTask = response.activeTasks[0]
      if (firstTask) {
        startTransition(() => {
          navigate({
            to: '/workbench/todos/$taskId',
            params: { taskId: firstTask.taskId },
          })
        })
        return
      }

      startTransition(() => {
        navigate({ to: '/workbench/todos/list' })
      })
    },
    onError: handleServerError,
  })

  const onSubmit = form.handleSubmit((values) => {
    let formData: Record<string, unknown> = {}

    try {
      formData = values.formDataJson ? JSON.parse(values.formDataJson) : {}
    } catch {
      form.setError('formDataJson', {
        type: 'manual',
        message: '流程表单数据必须是合法 JSON',
      })
      return
    }

    startMutation.mutate({
      processKey: values.processKey.trim(),
      businessKey: values.businessKey?.trim() || undefined,
      formData,
    })
  })

  return (
    <PageShell
      title='发起流程'
      description='独立的流程发起页，提交后会返回首个待处理任务，形成最小运行闭环。'
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
            <CardTitle>流程发起表单</CardTitle>
            <CardDescription>
              流程标识需先在流程设计器里发布对应版本，发起后自动进入待办处理页。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form className='space-y-6' onSubmit={onSubmit}>
                <FormField
                  control={form.control}
                  name='processKey'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>流程标识</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：oa_leave' {...field} />
                      </FormControl>
                      <FormDescription>输入已经发布的流程标识。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='businessKey'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>业务单号</FormLabel>
                      <FormControl>
                        <Input placeholder='例如：biz_20260322_001' {...field} />
                      </FormControl>
                      <FormDescription>可选，不填也能发起流程。</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name='formDataJson'
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>流程表单 JSON</FormLabel>
                      <FormControl>
                        <Textarea
                          className='min-h-40 font-mono text-sm'
                          placeholder='{"days": 3, "reason": "请假"}'
                          {...field}
                        />
                      </FormControl>
                      <FormDescription>
                        当前版本直接提交 JSON，后续会接入代码表单组件和 AI 填报。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <div className='flex items-center gap-3'>
                  <Button type='submit' disabled={startMutation.isPending}>
                    {startMutation.isPending ? (
                      <>
                        <Loader2 className='animate-spin' />
                        发起中
                      </>
                    ) : (
                      <>
                        <Play />
                        发起并进入待办
                      </>
                    )}
                  </Button>
                  <Button asChild type='button' variant='outline'>
                    <Link to='/workbench/todos/list'>先看待办列表</Link>
                  </Button>
                </div>
              </form>
            </Form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>闭环说明</CardTitle>
            <CardDescription>这是当前最小可运行运行态闭环的使用方式。</CardDescription>
          </CardHeader>
          <CardContent className='flex flex-col gap-3 text-sm text-muted-foreground'>
            <p>1. 先在流程设计器发布一个流程版本，例如 `oa_leave`。</p>
            <p>2. 在这里填写流程标识和 JSON 表单数据，点击发起。</p>
            <p>3. 系统会自动跳转到首个待办任务处理页，完成后返回工作台待办。</p>
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

export function WorkbenchTodoDetailPage({ taskId }: { taskId: string }) {
  const navigate = useNavigate()
  const detailQuery = useQuery({
    queryKey: ['workbench', 'todo-detail', taskId],
    queryFn: () => getWorkbenchTaskDetail(taskId),
  })
  const detail = detailQuery.data

  const form = useForm<TaskActionFormValues>({
    resolver: zodResolver(taskActionSchema),
    defaultValues: {
      action: 'APPROVE',
      comment: '',
    },
  })

  useEffect(() => {
    if (!detail) {
      return
    }

    form.reset({
      action: 'APPROVE',
      comment: detail.comment ?? '',
    })
  }, [detail, form])

  const completeMutation = useMutation({
    mutationFn: (payload: TaskActionFormValues) =>
      completeWorkbenchTask(taskId, {
        action: payload.action,
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: (response) => {
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
    },
    onError: handleServerError,
  })

  const actionLabel = useMemo(() => {
    if (!detail) {
      return ''
    }

    return detail.status === 'COMPLETED' ? '已完成' : '待处理'
  }, [detail])

  const onSubmit = form.handleSubmit((values) => {
    completeMutation.mutate(values)
  })

  return (
    <PageShell
      title='任务处理'
      description='独立的任务处理页，支持查看详情、填写审批意见并完成任务。'
      actions={
        <Button asChild variant='outline'>
          <Link to='/workbench/todos/list'>
            <ArrowLeft />
            返回待办列表
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
                    <Badge variant={detail.status === 'PENDING' ? 'destructive' : 'secondary'}>
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
                </dl>
              </div>

              <div className='space-y-2 rounded-lg border bg-muted/30 p-4 md:col-span-2'>
                <p className='text-xs text-muted-foreground'>流程表单数据</p>
                <pre className='overflow-auto rounded-md bg-background p-3 text-xs text-muted-foreground'>
                  {JSON.stringify(detail.formData, null, 2)}
                </pre>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>完成任务</CardTitle>
              <CardDescription>
                选择审批动作并填写意见，完成后会自动跳转到下一个待办或返回列表。
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Form {...form}>
                <form className='space-y-6' onSubmit={onSubmit}>
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
                        <FormDescription>
                          审批意见会随任务完成状态一起保存。
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <div className='flex items-center gap-3'>
                    <Button
                      type='submit'
                      disabled={completeMutation.isPending || detail.status === 'COMPLETED'}
                    >
                      {completeMutation.isPending ? (
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
            </CardContent>
          </Card>
        </div>
      ) : null}
    </PageShell>
  )
}
