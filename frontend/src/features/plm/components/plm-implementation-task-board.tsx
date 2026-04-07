import {
  type PLMImplementationTask,
  type PLMImplementationTaskActionCode,
} from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { formatApprovalSheetDateTime } from '@/features/workbench/approval-sheet-list'

const TASK_STATUS_ORDER: Array<PLMImplementationTask['status']> = [
  'PENDING',
  'RUNNING',
  'BLOCKED',
  'COMPLETED',
  'CANCELLED',
]

const TASK_STATUS_LABELS: Record<string, string> = {
  PENDING: '待开始',
  RUNNING: '进行中',
  BLOCKED: '已阻塞',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

function formatTaskStatus(value: string) {
  return TASK_STATUS_LABELS[value] ?? value
}

function resolveTaskBadgeVariant(status: string) {
  switch (status) {
    case 'RUNNING':
      return 'default' as const
    case 'BLOCKED':
      return 'secondary' as const
    case 'COMPLETED':
      return 'outline' as const
    case 'CANCELLED':
      return 'destructive' as const
    default:
      return 'outline' as const
  }
}

function resolveTaskActionButtons(task: PLMImplementationTask): Array<{
  label: string
  action: PLMImplementationTaskActionCode
  variant: 'default' | 'outline' | 'secondary' | 'destructive'
}> {
  switch (task.status) {
    case 'PENDING':
      return [
        { label: '开始', action: 'START', variant: 'default' },
        { label: '阻塞', action: 'BLOCK', variant: 'outline' },
        { label: '取消', action: 'CANCEL', variant: 'destructive' },
      ]
    case 'RUNNING':
      return [
        { label: '完成', action: 'COMPLETE', variant: 'default' },
        { label: '阻塞', action: 'BLOCK', variant: 'outline' },
        { label: '取消', action: 'CANCEL', variant: 'destructive' },
      ]
    case 'BLOCKED':
      return [
        { label: '开始', action: 'START', variant: 'default' },
        { label: '取消', action: 'CANCEL', variant: 'destructive' },
      ]
    default:
      return []
  }
}

function TaskCard({
  task,
  onTaskAction,
  pendingTaskId,
}: {
  task: PLMImplementationTask
  onTaskAction?: (
    task: PLMImplementationTask,
    action: PLMImplementationTaskActionCode
  ) => void
  pendingTaskId?: string | null
}) {
  const buttons = resolveTaskActionButtons(task)
  const isPending = pendingTaskId != null

  return (
    <div className='rounded-lg border bg-background p-4 shadow-sm'>
      <div className='flex flex-wrap items-start justify-between gap-3'>
        <div className='space-y-1'>
          <div className='flex flex-wrap items-center gap-2'>
            <span className='font-medium'>{task.taskTitle}</span>
            <Badge variant={resolveTaskBadgeVariant(String(task.status))}>
              {formatTaskStatus(String(task.status))}
            </Badge>
            {task.verificationRequired ? (
              <Badge variant='secondary'>需验证</Badge>
            ) : null}
          </div>
          <p className='text-xs text-muted-foreground'>
            {task.taskNo} · {task.taskType}
          </p>
        </div>
        <div className='text-right text-xs text-muted-foreground'>
          <div>
            {task.ownerDisplayName ?? task.ownerUserId ?? '未指定负责人'}
          </div>
          <div>
            {task.sortOrder != null ? `顺序 ${task.sortOrder}` : '未排序'}
          </div>
        </div>
      </div>

      <Separator className='my-4' />

      <dl className='grid gap-3 text-sm md:grid-cols-2'>
        <div className='space-y-1'>
          <dt className='text-xs text-muted-foreground'>计划开始</dt>
          <dd>
            {task.plannedStartAt
              ? formatApprovalSheetDateTime(task.plannedStartAt)
              : '--'}
          </dd>
        </div>
        <div className='space-y-1'>
          <dt className='text-xs text-muted-foreground'>计划完成</dt>
          <dd>
            {task.plannedEndAt
              ? formatApprovalSheetDateTime(task.plannedEndAt)
              : '--'}
          </dd>
        </div>
        <div className='space-y-1'>
          <dt className='text-xs text-muted-foreground'>实际开始</dt>
          <dd>
            {task.startedAt
              ? formatApprovalSheetDateTime(task.startedAt)
              : '--'}
          </dd>
        </div>
        <div className='space-y-1'>
          <dt className='text-xs text-muted-foreground'>实际完成</dt>
          <dd>
            {task.completedAt
              ? formatApprovalSheetDateTime(task.completedAt)
              : '--'}
          </dd>
        </div>
      </dl>

      {task.resultSummary ? (
        <>
          <Separator className='my-4' />
          <div className='space-y-1 text-sm'>
            <div className='text-xs text-muted-foreground'>结果摘要</div>
            <div className='leading-6'>{task.resultSummary}</div>
          </div>
        </>
      ) : null}

      {buttons.length > 0 ? (
        <>
          <Separator className='my-4' />
          <div className='flex flex-wrap gap-2'>
            {buttons.map((button) => (
              <Button
                key={`${task.id}-${button.action}`}
                type='button'
                size='sm'
                variant={button.variant}
                disabled={isPending}
                onClick={() => onTaskAction?.(task, button.action)}
              >
                {button.label}
              </Button>
            ))}
          </div>
        </>
      ) : null}
    </div>
  )
}

function TaskLane({
  status,
  tasks,
  onTaskAction,
  pendingTaskId,
}: {
  status: PLMImplementationTask['status']
  tasks: PLMImplementationTask[]
  onTaskAction?: (
    task: PLMImplementationTask,
    action: PLMImplementationTaskActionCode
  ) => void
  pendingTaskId?: string | null
}) {
  return (
    <div className='flex min-w-72 flex-1 flex-col rounded-xl border bg-muted/20 p-4'>
      <div className='mb-3 flex items-center justify-between gap-3'>
        <div>
          <div className='text-sm font-medium'>
            {formatTaskStatus(String(status))}
          </div>
          <div className='text-xs text-muted-foreground'>
            {tasks.length} 条任务
          </div>
        </div>
        <Badge variant={resolveTaskBadgeVariant(String(status))}>
          {String(status)}
        </Badge>
      </div>

      <ScrollArea className='max-h-[420px]'>
        <div className='space-y-3 pr-3'>
          {tasks.length === 0 ? (
            <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
              该状态下没有任务。
            </div>
          ) : (
            tasks.map((task) => (
              <TaskCard
                key={task.id}
                task={task}
                onTaskAction={onTaskAction}
                pendingTaskId={pendingTaskId}
              />
            ))
          )}
        </div>
      </ScrollArea>
    </div>
  )
}

export function PLMImplementationTaskBoard({
  tasks,
  onTaskAction,
  pendingTaskId,
  emptyDescription = '当前没有实施任务。',
}: {
  tasks: PLMImplementationTask[]
  onTaskAction?: (
    task: PLMImplementationTask,
    action: PLMImplementationTaskActionCode
  ) => void
  pendingTaskId?: string | null
  emptyDescription?: string
}) {
  const groupedTasks = TASK_STATUS_ORDER.map((status) => ({
    status,
    tasks: tasks.filter((task) => task.status === status),
  }))

  return (
    <Card>
      <CardHeader>
        <CardTitle>实施任务</CardTitle>
        <CardDescription>
          把实施阶段拆成可执行、可阻塞、可验证的任务板。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {tasks.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            {emptyDescription}
          </div>
        ) : (
          <div className='flex gap-4 overflow-x-auto pb-2'>
            {groupedTasks.map((lane) => (
              <TaskLane
                key={lane.status}
                status={lane.status}
                tasks={lane.tasks}
                onTaskAction={onTaskAction}
                pendingTaskId={pendingTaskId}
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
