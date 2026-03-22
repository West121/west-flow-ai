import {
  useEffect,
  useMemo,
  startTransition,
  useState,
  type ReactNode,
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
import { useForm, useWatch } from 'react-hook-form'
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
import {
  formatApprovalSheetText,
  resolveApprovalSheetResultLabel,
} from '@/features/workbench/approval-sheet-helpers'
import {
  createApprovalSheetColumns,
  summarizeApprovalSheets,
} from '@/features/workbench/approval-sheet-list'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import { type NavigateFn } from '@/hooks/use-table-url-state'
import { handleServerError } from '@/lib/handle-server-error'
import { NodeFormRenderer } from '@/features/forms/runtime/node-form-renderer'
import { ProcessFormRenderer } from '@/features/forms/runtime/process-form-renderer'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import {
  addSignWorkbenchTask,
  getApprovalSheetDetailByBusiness,
  claimWorkbenchTask,
  completeWorkbenchTask,
  getWorkbenchTaskActions,
  getWorkbenchTaskDetail,
  jumpWorkbenchTask,
  listApprovalSheets,
  listWorkbenchTasks,
  readWorkbenchTask,
  removeSignWorkbenchTask,
  revokeWorkbenchTask,
  rejectWorkbenchTask,
  returnWorkbenchTask,
  takeBackWorkbenchTask,
  transferWorkbenchTask,
  wakeUpWorkbenchInstance,
  urgeWorkbenchTask,
  type ApprovalSheetListItem,
  type CompleteWorkbenchTaskPayload,
  type WorkbenchTaskDetail,
  type WorkbenchTaskListItem,
} from '@/lib/api/workbench'

const workbenchTodoListRoute = getRouteApi('/_authenticated/workbench/todos/list')
const workbenchDoneListRoute = getRouteApi('/_authenticated/workbench/done/list')
const workbenchInitiatedListRoute = getRouteApi('/_authenticated/workbench/initiated/list')
const workbenchCopiedListRoute = getRouteApi('/_authenticated/workbench/copied/list')

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

function buildEmptyApprovalSheetPage(search: { page: number; pageSize: number }) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [],
    groups: [],
  }
}

const approvalSheetBusinessTypeOptions = [
  { label: '请假申请', value: 'OA_LEAVE' },
  { label: '报销申请', value: 'OA_EXPENSE' },
  { label: '通用申请', value: 'OA_COMMON' },
] as const

function getApprovalSheetFilterValue(
  search: ListQuerySearch,
  field: string
) {
  const filter = (search.filters ?? []).find((item) => item.field === field)
  return typeof filter?.value === 'string' ? filter.value : undefined
}

function updateApprovalSheetFilter(
  search: ListQuerySearch,
  navigate: NavigateFn,
  field: string,
  value?: string
) {
  const nextFilters = (search.filters ?? []).filter((item) => item.field !== field)
  if (value) {
    nextFilters.push({
      field,
      operator: 'eq',
      value,
    })
  }

  navigate({
    search: (prev) => ({
      ...prev,
      page: undefined,
      filters: nextFilters.length > 0 ? nextFilters : undefined,
    }),
  })
}

function isCcApprovalSheetRead(record: ApprovalSheetListItem) {
  const latestAction = record.latestAction?.toUpperCase()
  const currentTaskStatus = record.currentTaskStatus?.toUpperCase()

  return (
    latestAction === 'READ' ||
    latestAction === 'CC_READ' ||
    latestAction === 'READ_DONE' ||
    currentTaskStatus === 'READ'
  )
}

function isCcApprovalSheetCompleted(record: ApprovalSheetListItem) {
  return (
    record.instanceStatus === 'COMPLETED' ||
    record.currentTaskStatus?.toUpperCase() === 'COMPLETED'
  )
}

function summarizeCcApprovalSheets(records: ApprovalSheetListItem[]) {
  return {
    total: records.length,
    pending: records.filter(
      (record) => !isCcApprovalSheetRead(record) && !isCcApprovalSheetCompleted(record)
    ).length,
    read: records.filter((record) => isCcApprovalSheetRead(record)).length,
    completed: records.filter((record) => isCcApprovalSheetCompleted(record)).length,
  }
}

function ApprovalSheetListToolbar({
  view,
  search,
  navigate,
  records,
}: {
  view: 'DONE' | 'INITIATED' | 'CC'
  search: ListQuerySearch
  navigate: NavigateFn
  records: ApprovalSheetListItem[]
}) {
  const statusValue = getApprovalSheetFilterValue(search, 'instanceStatus')
  const businessTypeValue = getApprovalSheetFilterValue(search, 'businessType')
  const ccSummary = view === 'CC' ? summarizeCcApprovalSheets(records) : null

  return (
    <div className='space-y-4 rounded-lg border bg-muted/20 p-4'>
      <div className='flex flex-wrap items-start justify-between gap-3'>
        <div className='space-y-1'>
          <p className='text-sm font-medium'>高级筛选</p>
          <p className='text-sm text-muted-foreground'>
            按实例状态和业务类型快速收窄流程中心列表。
          </p>
        </div>
        {ccSummary ? (
          <div className='flex flex-wrap gap-2'>
            <Badge variant='secondary'>
              <span>抄送中</span>
              <span className='ml-2 text-xs tabular-nums text-muted-foreground'>
                {ccSummary.pending}
              </span>
            </Badge>
            <Badge variant='secondary'>
              <span>已阅</span>
              <span className='ml-2 text-xs tabular-nums text-muted-foreground'>
                {ccSummary.read}
              </span>
            </Badge>
            <Badge variant='secondary'>
              <span>已完成</span>
              <span className='ml-2 text-xs tabular-nums text-muted-foreground'>
                {ccSummary.completed}
              </span>
            </Badge>
          </div>
        ) : null}
      </div>

      <div className='space-y-3'>
        <div className='flex flex-wrap items-center gap-2'>
          <span className='text-xs font-medium uppercase tracking-wide text-muted-foreground'>
            状态
          </span>
          {[
            { label: '全部', value: undefined },
            { label: '进行中', value: 'RUNNING' },
            { label: '已完成', value: 'COMPLETED' },
          ].map((item) => (
            <Button
              key={item.label}
              type='button'
              variant={statusValue === item.value ? 'secondary' : 'outline'}
              size='sm'
              onClick={() =>
                updateApprovalSheetFilter(search, navigate, 'instanceStatus', item.value)
              }
            >
              {item.label}
            </Button>
          ))}
        </div>

        <div className='flex flex-wrap items-center gap-2'>
          <span className='text-xs font-medium uppercase tracking-wide text-muted-foreground'>
            业务类型
          </span>
          <Button
            type='button'
            variant={businessTypeValue ? 'outline' : 'secondary'}
            size='sm'
            onClick={() =>
              updateApprovalSheetFilter(search, navigate, 'businessType', undefined)
            }
          >
            全部
          </Button>
          {approvalSheetBusinessTypeOptions.map((item) => (
            <Button
              key={item.value}
              type='button'
              variant={businessTypeValue === item.value ? 'secondary' : 'outline'}
              size='sm'
              onClick={() =>
                updateApprovalSheetFilter(search, navigate, 'businessType', item.value)
              }
            >
              {item.label}
            </Button>
          ))}
        </div>
      </div>
    </div>
  )
}

function ApprovalSheetActionTimeline({
  taskTrace,
}: {
  taskTrace: WorkbenchTaskDetail['taskTrace']
}) {
  const items = taskTrace ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>动作轨迹</CardTitle>
        <CardDescription>
          按任务节点回放审批动作、办理人、意见和时间点，便于快速定位流程走向。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {items.length ? (
          <ol className='space-y-3 border-l border-dashed pl-4'>
            {items.map((item, index) => (
              <li key={`${item.taskId}:${item.nodeId}:${index}`} className='space-y-2'>
                <div className='flex flex-wrap items-center gap-2'>
                  <Badge variant='secondary'>{resolveApprovalSheetResultLabel(item)}</Badge>
                  <span className='font-medium'>{item.nodeName}</span>
                </div>
                <div className='grid gap-1 text-sm text-muted-foreground'>
                  <div className='flex flex-wrap gap-x-3 gap-y-1'>
                    <span>办理人：{formatApprovalSheetText(item.operatorUserId ?? item.assigneeUserId)}</span>
                    <span>接收时间：{formatDateTime(item.receiveTime)}</span>
                    <span>读取时间：{formatDateTime(item.readTime)}</span>
                  </div>
                  <div className='flex flex-wrap gap-x-3 gap-y-1'>
                    <span>开始时间：{formatDateTime(item.handleStartTime)}</span>
                    <span>完成时间：{formatDateTime(item.handleEndTime)}</span>
                    <span>
                      时长：
                      {item.handleDurationSeconds === null ||
                      item.handleDurationSeconds === undefined
                        ? '--'
                        : `${item.handleDurationSeconds} 秒`}
                    </span>
                  </div>
                  <p>意见：{formatApprovalSheetText(item.comment)}</p>
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <div className='rounded-lg border border-dashed p-6 text-sm text-muted-foreground'>
            暂无动作轨迹。
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function ApprovalSheetListPageSection({
  title,
  description,
  view,
  search,
  navigate,
  renderTopContent,
}: {
  title: string
  description: string
  view: 'DONE' | 'INITIATED' | 'CC'
  search: ListQuerySearch
  navigate: NavigateFn
  renderTopContent?: (records: ApprovalSheetListItem[]) => ReactNode
}) {
  const approvalSheetsQuery = useQuery({
    queryKey: ['workbench', 'approval-sheet-page', view, search],
    queryFn: () =>
      listApprovalSheets({
        ...search,
        view,
      }),
  })

  const pageData = approvalSheetsQuery.data ?? buildEmptyApprovalSheetPage(search)
  const summary =
    view === 'CC'
      ? summarizeCcApprovalSheets(pageData.records)
      : summarizeApprovalSheets(pageData.records)
  const pendingSummary = 'pending' in summary ? summary.pending : summary.running
  const completedSummary = 'read' in summary ? summary.read : summary.completed

  return (
    <>
      {renderTopContent ? renderTopContent(pageData.records) : null}
      {approvalSheetsQuery.isError ? (
        <Alert variant='destructive' className='mb-4'>
          <AlertTitle>{title}加载失败</AlertTitle>
          <AlertDescription>
            {approvalSheetsQuery.error instanceof Error
              ? approvalSheetsQuery.error.message
              : '请稍后重试'}
          </AlertDescription>
        </Alert>
      ) : null}
      <ResourceListPage
        title={title}
        description={description}
        endpoint='/api/v1/process-runtime/demo/approval-sheets/page'
        searchPlaceholder='搜索流程标题、业务标题、单号或当前节点'
        search={search}
        navigate={navigate}
        columns={createApprovalSheetColumns('workbench')}
        data={pageData.records}
        total={pageData.total}
        summaries={[
          {
            label: view === 'CC' ? '抄送总量' : '审批单总量',
            value: String(pageData.total),
            hint:
              view === 'CC'
                ? '当前页抄送记录的真实总量。'
                : '实例维度聚合后的审批单数量。',
          },
          {
            label: view === 'CC' ? '待阅' : '进行中',
            value: String(pendingSummary),
            hint:
              view === 'CC'
                ? '尚未确认已阅的抄送记录。'
                : '当前页里仍在流转中的审批单。',
          },
          {
            label: view === 'CC' ? '已阅' : '已完成',
            value: String(completedSummary),
            hint:
              view === 'CC'
                ? '已确认已阅的抄送记录。'
                : '当前页已完成的审批单数量。',
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

const rejectTaskSchema = z.object({
  targetStrategy: z.enum([
    'PREVIOUS_USER_TASK',
    'INITIATOR',
    'ANY_USER_TASK',
  ]),
  targetTaskId: z.string().max(120, '驳回目标任务编号最多 120 个字符').default(''),
  targetNodeId: z.string().max(120, '驳回目标节点编号最多 120 个字符').default(''),
  reapproveStrategy: z.enum([
    'CONTINUE',
    'RETURN_TO_REJECTED_NODE',
  ]),
  comment: z.string().max(500, '驳回说明最多 500 个字符').default(''),
})

type RejectTaskFormValues = z.input<typeof rejectTaskSchema>

const jumpTaskSchema = z.object({
  targetNodeId: z.string().trim().min(1, '请输入目标节点编号'),
  comment: z.string().max(500, '跳转说明最多 500 个字符').default(''),
})

type JumpTaskFormValues = z.input<typeof jumpTaskSchema>

const takeBackTaskSchema = z.object({
  comment: z.string().max(500, '拿回说明最多 500 个字符').default(''),
})

type TakeBackTaskFormValues = z.input<typeof takeBackTaskSchema>

const wakeUpTaskSchema = z.object({
  sourceTaskId: z.string().trim().min(1, '请输入历史任务编号'),
  comment: z.string().max(500, '唤醒说明最多 500 个字符').default(''),
})

type WakeUpTaskFormValues = z.input<typeof wakeUpTaskSchema>

const addSignTaskSchema = z.object({
  targetUserId: z.string().trim().min(1, '请输入加签用户编码'),
  comment: z.string().max(500, '加签说明最多 500 个字符').default(''),
})

type AddSignTaskFormValues = z.input<typeof addSignTaskSchema>

const removeSignTaskSchema = z.object({
  targetTaskId: z.string().trim().min(1, '请输入减签任务编号'),
  comment: z.string().max(500, '减签说明最多 500 个字符').default(''),
})

type RemoveSignTaskFormValues = z.input<typeof removeSignTaskSchema>

const simpleActionCommentSchema = z.object({
  comment: z.string().max(500, '说明最多 500 个字符').default(''),
})

type SimpleActionCommentFormValues = z.input<typeof simpleActionCommentSchema>

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

export function WorkbenchDoneListPage() {
  const search = workbenchDoneListRoute.useSearch()
  const navigate = workbenchDoneListRoute.useNavigate()

  return (
    <ApprovalSheetListPageSection
      title='流程中心已办'
      description='统一按审批单维度查看我已处理的流程，进入详情后仍然展示业务正文和流程轨迹。'
      view='DONE'
      search={search}
      navigate={navigate}
      renderTopContent={(records) => (
        <ApprovalSheetListToolbar
          view='DONE'
          search={search}
          navigate={navigate}
          records={records}
        />
      )}
    />
  )
}

export function WorkbenchInitiatedListPage() {
  const search = workbenchInitiatedListRoute.useSearch()
  const navigate = workbenchInitiatedListRoute.useNavigate()

  return (
    <ApprovalSheetListPageSection
      title='流程中心我发起'
      description='查看当前登录人发起的审批单，支持模糊搜索、分页和回查审批单详情。'
      view='INITIATED'
      search={search}
      navigate={navigate}
      renderTopContent={(records) => (
        <ApprovalSheetListToolbar
          view='INITIATED'
          search={search}
          navigate={navigate}
          records={records}
        />
      )}
    />
  )
}

export function WorkbenchCopiedListPage() {
  const search = workbenchCopiedListRoute.useSearch()
  const navigate = workbenchCopiedListRoute.useNavigate()

  return (
    <ApprovalSheetListPageSection
      title='流程中心抄送我'
      description='查看当前登录人的真实抄送记录，支持状态筛选、业务类型筛选和已阅轨迹回查。'
      view='CC'
      search={search}
      navigate={navigate}
      renderTopContent={(records) => (
        <ApprovalSheetListToolbar
          view='CC'
          search={search}
          navigate={navigate}
          records={records}
        />
      )}
    />
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
  const [addSignDialogOpen, setAddSignDialogOpen] = useState(false)
  const [removeSignDialogOpen, setRemoveSignDialogOpen] = useState(false)
  const [revokeDialogOpen, setRevokeDialogOpen] = useState(false)
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false)
  const [jumpDialogOpen, setJumpDialogOpen] = useState(false)
  const [takeBackDialogOpen, setTakeBackDialogOpen] = useState(false)
  const [wakeUpDialogOpen, setWakeUpDialogOpen] = useState(false)
  const [transferDialogOpen, setTransferDialogOpen] = useState(false)
  const [urgeDialogOpen, setUrgeDialogOpen] = useState(false)
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

  function requireActionInstanceId() {
    if (!detail?.instanceId) {
      throw new Error('当前审批单没有实例编号')
    }

    return detail.instanceId
  }

  async function navigateAfterTaskMutation(response: {
    nextTasks: Array<{ taskId: string }>
  }) {
    const nextTask = response.nextTasks[0]
    if (locator?.mode === 'task') {
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
  const addSignForm = useForm<AddSignTaskFormValues>({
    resolver: zodResolver(addSignTaskSchema),
    defaultValues: {
      targetUserId: '',
      comment: '',
    },
  })
  const removeSignForm = useForm<RemoveSignTaskFormValues>({
    resolver: zodResolver(removeSignTaskSchema),
    defaultValues: {
      targetTaskId: '',
      comment: '',
    },
  })
  const revokeForm = useForm<SimpleActionCommentFormValues>({
    resolver: zodResolver(simpleActionCommentSchema),
    defaultValues: {
      comment: '',
    },
  })
  const rejectForm = useForm<RejectTaskFormValues>({
    resolver: zodResolver(rejectTaskSchema),
    defaultValues: {
      targetStrategy: 'PREVIOUS_USER_TASK',
      targetTaskId: '',
      targetNodeId: '',
      reapproveStrategy: 'CONTINUE',
      comment: '',
    },
  })
  const jumpForm = useForm<JumpTaskFormValues>({
    resolver: zodResolver(jumpTaskSchema),
    defaultValues: {
      targetNodeId: '',
      comment: '',
    },
  })
  const takeBackForm = useForm<TakeBackTaskFormValues>({
    resolver: zodResolver(takeBackTaskSchema),
    defaultValues: {
      comment: '',
    },
  })
  const wakeUpForm = useForm<WakeUpTaskFormValues>({
    resolver: zodResolver(wakeUpTaskSchema),
    defaultValues: {
      sourceTaskId: '',
      comment: '',
    },
  })
  const urgeForm = useForm<SimpleActionCommentFormValues>({
    resolver: zodResolver(simpleActionCommentSchema),
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
    addSignForm.reset({
      targetUserId: '',
      comment: '',
    })
    removeSignForm.reset({
      targetTaskId: '',
      comment: '',
    })
    revokeForm.reset({
      comment: '',
    })
    rejectForm.reset({
      targetStrategy: 'PREVIOUS_USER_TASK',
      targetTaskId: '',
      targetNodeId: '',
      reapproveStrategy: 'CONTINUE',
      comment: '',
    })
    jumpForm.reset({
      targetNodeId: '',
      comment: '',
    })
    takeBackForm.reset({
      comment: '',
    })
    wakeUpForm.reset({
      sourceTaskId: '',
      comment: '',
    })
    urgeForm.reset({
      comment: '',
    })
  }, [
    addSignForm,
    claimForm,
    detail,
    jumpForm,
    removeSignForm,
    rejectForm,
    returnForm,
    revokeForm,
    takeBackForm,
    transferForm,
    urgeForm,
    wakeUpForm,
  ])

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
  const addSignMutation = useMutation({
    mutationFn: (payload: AddSignTaskFormValues) =>
      addSignWorkbenchTask(requireActionTaskId(), {
        targetUserId: payload.targetUserId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setAddSignDialogOpen(false)
      addSignForm.reset({
        targetUserId: '',
        comment: '',
      })
    },
    onError: handleServerError,
  })
  const removeSignMutation = useMutation({
    mutationFn: (payload: RemoveSignTaskFormValues) =>
      removeSignWorkbenchTask(requireActionTaskId(), {
        targetTaskId: payload.targetTaskId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setRemoveSignDialogOpen(false)
      removeSignForm.reset({
        targetTaskId: '',
        comment: '',
      })
    },
    onError: handleServerError,
  })
  const revokeMutation = useMutation({
    mutationFn: (payload: SimpleActionCommentFormValues) =>
      revokeWorkbenchTask(requireActionTaskId(), {
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setRevokeDialogOpen(false)
      revokeForm.reset({
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
  const rejectMutation = useMutation({
    mutationFn: (payload: RejectTaskFormValues) => {
      const targetTaskId = payload.targetTaskId?.trim()
      const targetNodeId = payload.targetNodeId?.trim()

      return rejectWorkbenchTask(requireActionTaskId(), {
        targetStrategy: payload.targetStrategy,
        targetTaskId: targetTaskId || undefined,
        targetNodeId: targetNodeId || undefined,
        reapproveStrategy: payload.reapproveStrategy,
        comment: payload.comment?.trim() || undefined,
      })
    },
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      setRejectDialogOpen(false)
      rejectForm.reset({
        targetStrategy: 'PREVIOUS_USER_TASK',
        targetTaskId: '',
        targetNodeId: '',
        reapproveStrategy: 'CONTINUE',
        comment: '',
      })
      await navigateAfterTaskMutation(response)
    },
    onError: handleServerError,
  })
  const jumpMutation = useMutation({
    mutationFn: (payload: JumpTaskFormValues) =>
      jumpWorkbenchTask(requireActionTaskId(), {
        targetNodeId: payload.targetNodeId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      setJumpDialogOpen(false)
      jumpForm.reset({
        targetNodeId: '',
        comment: '',
      })
      await navigateAfterTaskMutation(response)
    },
    onError: handleServerError,
  })
  const takeBackMutation = useMutation({
    mutationFn: (payload: TakeBackTaskFormValues) =>
      takeBackWorkbenchTask(requireActionTaskId(), {
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      setTakeBackDialogOpen(false)
      takeBackForm.reset({
        comment: '',
      })
      await navigateAfterTaskMutation(response)
    },
    onError: handleServerError,
  })
  const wakeUpMutation = useMutation({
    mutationFn: (payload: WakeUpTaskFormValues) =>
      wakeUpWorkbenchInstance(requireActionInstanceId(), {
        sourceTaskId: payload.sourceTaskId.trim(),
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async (response) => {
      await refreshWorkbenchQueries()
      setWakeUpDialogOpen(false)
      wakeUpForm.reset({
        sourceTaskId: '',
        comment: '',
      })
      await navigateAfterTaskMutation(response)
    },
    onError: handleServerError,
  })
  const urgeMutation = useMutation({
    mutationFn: (payload: SimpleActionCommentFormValues) =>
      urgeWorkbenchTask(requireActionTaskId(), {
        comment: payload.comment?.trim() || undefined,
      }),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
      setUrgeDialogOpen(false)
      urgeForm.reset({
        comment: '',
      })
    },
    onError: handleServerError,
  })
  const readMutation = useMutation({
    mutationFn: () => readWorkbenchTask(requireActionTaskId()),
    onSuccess: async () => {
      await refreshWorkbenchQueries()
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
  const onAddSignSubmit = addSignForm.handleSubmit((values) => {
    addSignMutation.mutate(values)
  })
  const onRemoveSignSubmit = removeSignForm.handleSubmit((values) => {
    removeSignMutation.mutate(values)
  })
  const onRevokeSubmit = revokeForm.handleSubmit((values) => {
    revokeMutation.mutate(values)
  })
  const onUrgeSubmit = urgeForm.handleSubmit((values) => {
    urgeMutation.mutate(values)
  })
  const showCompletionForm = Boolean(
    actionsQuery.data?.canApprove || actionsQuery.data?.canReject
  )
  const hasNodeForm = Boolean(detail?.nodeFormKey && detail?.nodeFormVersion)
  const hasMoreActions = Boolean(
    actionsQuery.data?.canAddSign ||
      actionsQuery.data?.canRemoveSign ||
      actionsQuery.data?.canRevoke ||
      actionsQuery.data?.canUrge ||
      actionsQuery.data?.canRead ||
      actionsQuery.data?.canRejectRoute ||
      actionsQuery.data?.canJump ||
      actionsQuery.data?.canTakeBack ||
      actionsQuery.data?.canWakeUp
  )
  const rejectTargetStrategy =
    useWatch({
      control: rejectForm.control,
      name: 'targetStrategy',
    }) ?? 'PREVIOUS_USER_TASK'

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
                      {detail.handleDurationSeconds === null ||
                      detail.handleDurationSeconds === undefined
                        ? '--'
                        : `${detail.handleDurationSeconds} 秒`}
                    </dd>
                  </div>
                </dl>
              </div>

              <div className='md:col-span-2 grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
                <ApprovalSheetBusinessSection detail={detail} />
                <div className='space-y-4'>
                  <ApprovalSheetGraph
                    flowNodes={detail.flowNodes ?? []}
                    flowEdges={detail.flowEdges ?? []}
                    taskTrace={detail.taskTrace ?? []}
                    instanceEvents={detail.instanceEvents ?? []}
                  />
                  <ApprovalSheetActionTimeline taskTrace={detail.taskTrace ?? []} />
                </div>
              </div>

              <TaskRuntimeFormCard
                key={`${detail.taskId}:${detail.updatedAt}:${detail.effectiveFormKey}:${detail.effectiveFormVersion}`}
                detail={detail}
                hasNodeForm={hasNodeForm}
                showCompletionForm={showCompletionForm}
                onSubmit={(payload) => {
                  if (payload.action === 'REJECT') {
                    rejectMutation.mutate({
                      targetStrategy: 'PREVIOUS_USER_TASK',
                      targetTaskId: '',
                      targetNodeId: '',
                      reapproveStrategy: 'CONTINUE',
                      comment: payload.comment ?? undefined,
                    })
                    return
                  }

                  completeMutation.mutate(payload)
                }}
                isPending={completeMutation.isPending || rejectMutation.isPending}
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

              {hasMoreActions ? (
                <div className='rounded-lg border p-4'>
                  <div className='mb-4 space-y-1'>
                    <p className='text-sm font-medium'>更多动作</p>
                    <p className='text-sm text-muted-foreground'>
                      高级运行态动作会写入审批轨迹，执行后自动刷新当前审批单详情。
                    </p>
                  </div>
                  <div className='grid gap-3'>
                    {actionsQuery.data?.canAddSign ? (
                      <Dialog open={addSignDialogOpen} onOpenChange={setAddSignDialogOpen}>
                        <DialogTrigger asChild>
                          <Button type='button' variant='outline'>加签</Button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>加签</DialogTitle>
                            <DialogDescription>
                              为当前任务追加一位串行复核办理人。
                            </DialogDescription>
                          </DialogHeader>
                          <Form {...addSignForm}>
                            <form className='space-y-4' onSubmit={onAddSignSubmit}>
                              <FormField
                                control={addSignForm.control}
                                name='targetUserId'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>加签用户编码</FormLabel>
                                    <FormControl>
                                      <Input placeholder='例如：usr_003' {...field} />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <FormField
                                control={addSignForm.control}
                                name='comment'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>加签说明</FormLabel>
                                    <FormControl>
                                      <Textarea
                                        className='min-h-24'
                                        placeholder='请输入加签说明'
                                        {...field}
                                      />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <DialogFooter>
                                <Button type='button' variant='outline' onClick={() => setAddSignDialogOpen(false)}>
                                  取消
                                </Button>
                                <Button type='submit' disabled={addSignMutation.isPending}>
                                  {addSignMutation.isPending ? '加签中' : '确认加签'}
                                </Button>
                              </DialogFooter>
                            </form>
                          </Form>
                        </DialogContent>
                      </Dialog>
                    ) : null}

                    {actionsQuery.data?.canRemoveSign ? (
                      <Dialog open={removeSignDialogOpen} onOpenChange={setRemoveSignDialogOpen}>
                        <DialogTrigger asChild>
                          <Button type='button' variant='outline'>减签</Button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>减签</DialogTitle>
                            <DialogDescription>
                              输入待减签的加签任务编号，系统会撤销该串行加签任务。
                            </DialogDescription>
                          </DialogHeader>
                          <Form {...removeSignForm}>
                            <form className='space-y-4' onSubmit={onRemoveSignSubmit}>
                              <FormField
                                control={removeSignForm.control}
                                name='targetTaskId'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>加签任务编号</FormLabel>
                                    <FormControl>
                                      <Input placeholder='例如：task_xxx' {...field} />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <FormField
                                control={removeSignForm.control}
                                name='comment'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>减签说明</FormLabel>
                                    <FormControl>
                                      <Textarea
                                        className='min-h-24'
                                        placeholder='请输入减签说明'
                                        {...field}
                                      />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <DialogFooter>
                                <Button type='button' variant='outline' onClick={() => setRemoveSignDialogOpen(false)}>
                                  取消
                                </Button>
                                <Button type='submit' disabled={removeSignMutation.isPending}>
                                  {removeSignMutation.isPending ? '减签中' : '确认减签'}
                                </Button>
                              </DialogFooter>
                            </form>
                          </Form>
                        </DialogContent>
                      </Dialog>
                    ) : null}

                    {actionsQuery.data?.canRevoke ? (
                      <Dialog open={revokeDialogOpen} onOpenChange={setRevokeDialogOpen}>
                        <DialogTrigger asChild>
                          <Button type='button' variant='outline'>撤销</Button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>撤销流程</DialogTitle>
                            <DialogDescription>
                              仅发起人可撤销，撤销后当前运行中的任务会一并终止。
                            </DialogDescription>
                          </DialogHeader>
                          <Form {...revokeForm}>
                            <form className='space-y-4' onSubmit={onRevokeSubmit}>
                              <FormField
                                control={revokeForm.control}
                                name='comment'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>撤销说明</FormLabel>
                                    <FormControl>
                                      <Textarea
                                        className='min-h-24'
                                        placeholder='请输入撤销说明'
                                        {...field}
                                      />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <DialogFooter>
                                <Button type='button' variant='outline' onClick={() => setRevokeDialogOpen(false)}>
                                  取消
                                </Button>
                                <Button type='submit' disabled={revokeMutation.isPending}>
                                  {revokeMutation.isPending ? '撤销中' : '确认撤销'}
                                </Button>
                              </DialogFooter>
                            </form>
                          </Form>
                        </DialogContent>
                      </Dialog>
                    ) : null}

                    {actionsQuery.data?.canUrge ? (
                      <Dialog open={urgeDialogOpen} onOpenChange={setUrgeDialogOpen}>
                        <DialogTrigger asChild>
                          <Button type='button' variant='outline'>催办</Button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>催办</DialogTitle>
                            <DialogDescription>
                              催办不会改变任务状态，但会写入实例动作轨迹。
                            </DialogDescription>
                          </DialogHeader>
                          <Form {...urgeForm}>
                            <form className='space-y-4' onSubmit={onUrgeSubmit}>
                              <FormField
                                control={urgeForm.control}
                                name='comment'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>催办说明</FormLabel>
                                    <FormControl>
                                      <Textarea
                                        className='min-h-24'
                                        placeholder='请输入催办说明'
                                        {...field}
                                      />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <DialogFooter>
                                <Button type='button' variant='outline' onClick={() => setUrgeDialogOpen(false)}>
                                  取消
                                </Button>
                                <Button type='submit' disabled={urgeMutation.isPending}>
                                  {urgeMutation.isPending ? '催办中' : '确认催办'}
                                </Button>
                              </DialogFooter>
                            </form>
                          </Form>
                        </DialogContent>
                      </Dialog>
                    ) : null}

                    {actionsQuery.data?.canRead ? (
                      <Button
                        type='button'
                        variant='outline'
                        disabled={readMutation.isPending}
                        onClick={() => {
                          readMutation.mutate()
                        }}
                      >
                        {readMutation.isPending ? '处理中' : '已阅'}
                      </Button>
                    ) : null}

                    {actionsQuery.data?.canRejectRoute ? (
                      <Dialog open={rejectDialogOpen} onOpenChange={setRejectDialogOpen}>
                        <DialogTrigger asChild>
                          <Button type='button' variant='outline'>
                            驳回
                          </Button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>驳回处理</DialogTitle>
                            <DialogDescription>
                              选择驳回目标和重审策略，系统会通过专用驳回接口处理。
                            </DialogDescription>
                          </DialogHeader>
                          <Form {...rejectForm}>
                            <form
                              className='space-y-4'
                              onSubmit={rejectForm.handleSubmit((values) => {
                                rejectMutation.mutate(values)
                              })}
                            >
                              <FormField
                                control={rejectForm.control}
                                name='targetStrategy'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>驳回目标</FormLabel>
                                    <FormControl>
                                      <select
                                        className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                                        {...field}
                                      >
                                        <option value='PREVIOUS_USER_TASK'>上一步人工节点</option>
                                        <option value='INITIATOR'>发起人</option>
                                        <option value='ANY_USER_TASK'>任意节点</option>
                                      </select>
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              {rejectTargetStrategy === 'ANY_USER_TASK' ? (
                                <FormField
                                  control={rejectForm.control}
                                  name='targetNodeId'
                                  render={({ field }) => (
                                    <FormItem>
                                      <FormLabel>目标节点编号</FormLabel>
                                      <FormControl>
                                        <Input placeholder='例如：approve_finance' {...field} />
                                      </FormControl>
                                      <FormDescription>当前版本通过节点编号指定任意驳回目标。</FormDescription>
                                      <FormMessage />
                                    </FormItem>
                                  )}
                                />
                              ) : (
                                <FormField
                                  control={rejectForm.control}
                                  name='targetTaskId'
                                  render={({ field }) => (
                                    <FormItem>
                                      <FormLabel>目标任务编号</FormLabel>
                                      <FormControl>
                                        <Input placeholder='可选，系统会自动解析默认目标' {...field} />
                                      </FormControl>
                                      <FormDescription>上一步或发起人场景可留空，让系统自动回退。</FormDescription>
                                      <FormMessage />
                                    </FormItem>
                                  )}
                                />
                              )}
                              <FormField
                                control={rejectForm.control}
                                name='reapproveStrategy'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>重审策略</FormLabel>
                                    <FormControl>
                                      <select
                                        className='flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm'
                                        {...field}
                                      >
                                        <option value='CONTINUE'>继续执行</option>
                                        <option value='RETURN_TO_REJECTED_NODE'>退回驳回节点</option>
                                      </select>
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <FormField
                                control={rejectForm.control}
                                name='comment'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>驳回说明</FormLabel>
                                    <FormControl>
                                      <Textarea
                                        className='min-h-24'
                                        placeholder='请输入驳回说明'
                                        {...field}
                                      />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <DialogFooter>
                                <Button type='button' variant='outline' onClick={() => setRejectDialogOpen(false)}>
                                  取消
                                </Button>
                                <Button type='submit' disabled={rejectMutation.isPending}>
                                  {rejectMutation.isPending ? '驳回中' : '确认驳回'}
                                </Button>
                              </DialogFooter>
                            </form>
                          </Form>
                        </DialogContent>
                      </Dialog>
                    ) : null}

                    {actionsQuery.data?.canJump ? (
                      <Dialog open={jumpDialogOpen} onOpenChange={setJumpDialogOpen}>
                        <DialogTrigger asChild>
                          <Button type='button' variant='outline'>
                            跳转
                          </Button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>跳转</DialogTitle>
                            <DialogDescription>
                              将当前任务强制路由到指定节点或结束节点。
                            </DialogDescription>
                          </DialogHeader>
                          <Form {...jumpForm}>
                            <form
                              className='space-y-4'
                              onSubmit={jumpForm.handleSubmit((values) => {
                                jumpMutation.mutate(values)
                              })}
                            >
                              <FormField
                                control={jumpForm.control}
                                name='targetNodeId'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>目标节点编号</FormLabel>
                                    <FormControl>
                                      <Input placeholder='例如：approve_finance' {...field} />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <FormField
                                control={jumpForm.control}
                                name='comment'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>跳转说明</FormLabel>
                                    <FormControl>
                                      <Textarea
                                        className='min-h-24'
                                        placeholder='请输入跳转说明'
                                        {...field}
                                      />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <DialogFooter>
                                <Button type='button' variant='outline' onClick={() => setJumpDialogOpen(false)}>
                                  取消
                                </Button>
                                <Button type='submit' disabled={jumpMutation.isPending}>
                                  {jumpMutation.isPending ? '跳转中' : '确认跳转'}
                                </Button>
                              </DialogFooter>
                            </form>
                          </Form>
                        </DialogContent>
                      </Dialog>
                    ) : null}

                    {actionsQuery.data?.canTakeBack ? (
                      <Dialog open={takeBackDialogOpen} onOpenChange={setTakeBackDialogOpen}>
                        <DialogTrigger asChild>
                          <Button type='button' variant='outline'>
                            拿回
                          </Button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>拿回任务</DialogTitle>
                            <DialogDescription>
                              在当前办理人未处理前，上一节点提交人可把任务拿回。
                            </DialogDescription>
                          </DialogHeader>
                          <Form {...takeBackForm}>
                            <form
                              className='space-y-4'
                              onSubmit={takeBackForm.handleSubmit((values) => {
                                takeBackMutation.mutate(values)
                              })}
                            >
                              <FormField
                                control={takeBackForm.control}
                                name='comment'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>拿回说明</FormLabel>
                                    <FormControl>
                                      <Textarea
                                        className='min-h-24'
                                        placeholder='请输入拿回说明'
                                        {...field}
                                      />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <DialogFooter>
                                <Button type='button' variant='outline' onClick={() => setTakeBackDialogOpen(false)}>
                                  取消
                                </Button>
                                <Button type='submit' disabled={takeBackMutation.isPending}>
                                  {takeBackMutation.isPending ? '拿回中' : '确认拿回'}
                                </Button>
                              </DialogFooter>
                            </form>
                          </Form>
                        </DialogContent>
                      </Dialog>
                    ) : null}

                    {actionsQuery.data?.canWakeUp ? (
                      <Dialog open={wakeUpDialogOpen} onOpenChange={setWakeUpDialogOpen}>
                        <DialogTrigger asChild>
                          <Button type='button' variant='outline'>
                            唤醒
                          </Button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>唤醒流程</DialogTitle>
                            <DialogDescription>
                              从历史任务重新拉起终态实例，继续后续审批流程。
                            </DialogDescription>
                          </DialogHeader>
                          <Form {...wakeUpForm}>
                            <form
                              className='space-y-4'
                              onSubmit={wakeUpForm.handleSubmit((values) => {
                                wakeUpMutation.mutate(values)
                              })}
                            >
                              <FormField
                                control={wakeUpForm.control}
                                name='sourceTaskId'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>历史任务编号</FormLabel>
                                    <FormControl>
                                      <Input placeholder='例如：task_001' {...field} />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <FormField
                                control={wakeUpForm.control}
                                name='comment'
                                render={({ field }) => (
                                  <FormItem>
                                    <FormLabel>唤醒说明</FormLabel>
                                    <FormControl>
                                      <Textarea
                                        className='min-h-24'
                                        placeholder='请输入唤醒说明'
                                        {...field}
                                      />
                                    </FormControl>
                                    <FormMessage />
                                  </FormItem>
                                )}
                              />
                              <DialogFooter>
                                <Button type='button' variant='outline' onClick={() => setWakeUpDialogOpen(false)}>
                                  取消
                                </Button>
                                <Button type='submit' disabled={wakeUpMutation.isPending}>
                                  {wakeUpMutation.isPending ? '唤醒中' : '确认唤醒'}
                                </Button>
                              </DialogFooter>
                            </form>
                          </Form>
                        </DialogContent>
                      </Dialog>
                    ) : null}
                  </div>
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
              !actionsQuery.data?.canAddSign &&
              !actionsQuery.data?.canRemoveSign &&
              !actionsQuery.data?.canRevoke &&
              !actionsQuery.data?.canUrge &&
              !actionsQuery.data?.canRead &&
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
