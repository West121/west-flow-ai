import {
  type PLMConnectorDispatchLog,
  type PLMConnectorTask,
  type PLMConnectorTaskReceipt,
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
import { Separator } from '@/components/ui/separator'

function formatTaskStatus(status: string) {
  switch (status) {
    case 'ACKED':
      return '已回执'
    case 'SYNCED':
      return '已同步'
    case 'DISPATCHED':
      return '已派发'
    case 'RETRY_PENDING':
      return '待重试'
    case 'RUNNING':
      return '处理中'
    case 'FAILED':
      return '失败'
    case 'PENDING':
      return '待执行'
    default:
      return status
  }
}

function formatReceiptStatus(status: string) {
  switch (status) {
    case 'ACKED':
      return '已回执'
    case 'FAILED':
      return '失败'
    case 'PENDING':
      return '待回执'
    default:
      return status
  }
}

function resolveTaskVariant(status: string) {
  if (status === 'FAILED') {
    return 'destructive' as const
  }
  if (status === 'SYNCED' || status === 'ACKED') {
    return 'secondary' as const
  }
  return 'outline' as const
}

function resolveReceiptVariant(status: string) {
  if (status === 'FAILED') {
    return 'destructive' as const
  }
  if (status === 'ACKED') {
    return 'secondary' as const
  }
  return 'outline' as const
}

function ReceiptItem({
  receipt,
}: {
  receipt: PLMConnectorTaskReceipt
}) {
  return (
    <div className='rounded-lg border bg-background/70 p-3 text-sm'>
      <div className='flex flex-wrap items-center gap-2'>
        <Badge variant='outline'>{receipt.receiptType}</Badge>
        <Badge variant={resolveReceiptVariant(receipt.receiptStatus)}>
          {formatReceiptStatus(receipt.receiptStatus)}
        </Badge>
        <span className='text-xs text-muted-foreground'>
          {receipt.receiptNo ?? '--'}
        </span>
      </div>
      <div className='mt-2 space-y-1 text-xs text-muted-foreground'>
        <div>回执时间 {receipt.acknowledgedAt ?? '--'}</div>
        {receipt.payloadSummary ? <div>{receipt.payloadSummary}</div> : null}
        {receipt.payloadDetails?.map((detail) => (
          <div key={detail}>{detail}</div>
        ))}
        {receipt.errorMessage ? (
          <div className='text-destructive'>{receipt.errorMessage}</div>
        ) : null}
      </div>
    </div>
  )
}

function DispatchLogItem({
  log,
}: {
  log: PLMConnectorDispatchLog
}) {
  return (
    <div className='rounded-lg border bg-background/70 p-3 text-sm'>
      <div className='flex flex-wrap items-center gap-2'>
        <Badge variant='outline'>{log.actionType}</Badge>
        <Badge variant={resolveTaskVariant(log.status)}>{log.status}</Badge>
        <span className='text-xs text-muted-foreground'>
          {log.happenedAt ?? '--'}
        </span>
      </div>
      <div className='mt-2 space-y-1 text-xs text-muted-foreground'>
        {log.requestSummary ? <div>请求：{log.requestSummary}</div> : null}
        {log.requestDetails?.map((detail) => (
          <div key={`request-${detail}`}>{detail}</div>
        ))}
        {log.responseSummary ? <div>响应：{log.responseSummary}</div> : null}
        {log.responseDetails?.map((detail) => (
          <div key={`response-${detail}`}>{detail}</div>
        ))}
        {log.errorMessage ? (
          <div className='text-destructive'>{log.errorMessage}</div>
        ) : null}
      </div>
    </div>
  )
}

function StatusRail({
  label,
  value,
  detail,
  tone,
}: {
  label: string
  value: number
  detail: string
  tone: 'sky' | 'amber' | 'emerald'
}) {
  const toneClass =
    tone === 'sky'
      ? 'bg-sky-500'
      : tone === 'emerald'
        ? 'bg-emerald-500'
        : 'bg-amber-500'

  return (
    <div className='space-y-2 rounded-xl border bg-muted/10 p-4'>
      <div className='flex items-center justify-between gap-3'>
        <div className='text-sm font-medium'>{label}</div>
        <div className='text-sm font-semibold'>{value}%</div>
      </div>
      <div className='h-2 rounded-full bg-muted/40'>
        <div
          className={`h-full rounded-full ${toneClass}`}
          style={{ width: `${Math.max(0, Math.min(100, value))}%` }}
        />
      </div>
      <div className='text-xs text-muted-foreground'>{detail}</div>
    </div>
  )
}

export function PLMConnectorTaskPanel({
  tasks,
  onDispatchTask,
  onRetryTask,
  pendingTaskAction,
}: {
  tasks: PLMConnectorTask[]
  onDispatchTask?: (task: PLMConnectorTask) => void
  onRetryTask?: (task: PLMConnectorTask) => void
  pendingTaskAction?: {
    taskId: string
    action: 'dispatch' | 'retry'
  } | null
}) {
  const normalizedTasks = tasks.map((task) => ({
    ...task,
    dispatchLogs: task.dispatchLogs ?? [],
    receipts: task.receipts ?? [],
  }))
  const receiptCount = tasks.reduce(
    (count, task) => count + (task.receipts?.length ?? 0),
    0
  )
  const dispatchLogCount = normalizedTasks.reduce(
    (count, task) => count + task.dispatchLogs.length,
    0
  )
  const failedTaskCount = normalizedTasks.filter(
    (task) => task.status === 'FAILED'
  ).length
  const pendingReceiptCount = normalizedTasks.reduce(
    (count, task) =>
      count +
      task.receipts.filter((receipt) => receipt.receiptStatus === 'PENDING').length,
    0
  )
  const syncedTaskCount = normalizedTasks.filter(
    (task) => task.status === 'SYNCED'
  ).length
  const runningTaskCount = normalizedTasks.filter((task) =>
    ['RUNNING', 'DISPATCHED', 'QUEUED', 'PENDING'].includes(task.status)
  ).length
  const ackedReceiptCount = normalizedTasks.reduce(
    (count, task) =>
      count +
      task.receipts.filter((receipt) => receipt.receiptStatus === 'ACKED').length,
    0
  )
  const connectorHealthRate =
    normalizedTasks.length > 0
      ? Math.round((syncedTaskCount / normalizedTasks.length) * 100)
      : 100
  const dispatchProgressRate =
    normalizedTasks.length > 0
      ? Math.round(
          ((syncedTaskCount + runningTaskCount) / normalizedTasks.length) * 100
        )
      : 100
  const receiptClosureRate =
    receiptCount > 0
      ? Math.round((ackedReceiptCount / receiptCount) * 100)
      : 100

  return (
    <Card>
      <CardHeader>
        <CardTitle>连接器任务 / 回执</CardTitle>
        <CardDescription>
          跟踪 ERP、MES、PDM、CAD 等边界任务的下发、执行和回执状态。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='grid gap-3 sm:grid-cols-3'>
          <div className='rounded-xl border bg-muted/15 p-4'>
            <div className='text-sm text-muted-foreground'>连接器任务</div>
            <div className='mt-1 text-2xl font-semibold'>{tasks.length}</div>
            <div className='mt-1 text-xs text-muted-foreground'>
              跨系统推进中的边界任务
            </div>
          </div>
          <div className='rounded-xl border bg-muted/15 p-4'>
            <div className='text-sm text-muted-foreground'>失败任务</div>
            <div className='mt-1 text-2xl font-semibold'>{failedTaskCount}</div>
            <div className='mt-1 text-xs text-muted-foreground'>
              需要人工重试或人工补偿
            </div>
          </div>
          <div className='rounded-xl border bg-muted/15 p-4'>
            <div className='text-sm text-muted-foreground'>待回执</div>
            <div className='mt-1 text-2xl font-semibold'>{pendingReceiptCount}</div>
            <div className='mt-1 text-xs text-muted-foreground'>
              回执总数 {receiptCount}
            </div>
          </div>
        </div>
        <div className='rounded-xl border bg-muted/10 p-4 text-sm text-muted-foreground'>
          当前累计派发日志 {dispatchLogCount} 条，可回看入队、派发、重试与 ACK 收到的完整轨迹。
        </div>

        <div className='grid gap-3 xl:grid-cols-3'>
          <StatusRail
            label='连接器健康'
            value={connectorHealthRate}
            detail={`已同步 ${syncedTaskCount} / ${normalizedTasks.length} · 失败 ${failedTaskCount}`}
            tone='sky'
          />
          <StatusRail
            label='任务推进'
            value={dispatchProgressRate}
            detail={`运行中 ${runningTaskCount} · 已派发日志 ${dispatchLogCount}`}
            tone='amber'
          />
          <StatusRail
            label='回执闭环'
            value={receiptClosureRate}
            detail={`已回执 ${ackedReceiptCount} / ${receiptCount} · 待回执 ${pendingReceiptCount}`}
            tone='emerald'
          />
        </div>

        {normalizedTasks.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前没有连接器任务或回执记录。
          </div>
        ) : (
          <div className='space-y-4'>
            {normalizedTasks.map((task, index) => (
              <div key={task.id} className='space-y-3'>
                <div className='rounded-xl border bg-muted/15 p-4'>
                  <div className='flex flex-wrap items-center gap-2'>
                    <div className='font-medium'>{task.connectorName}</div>
                    <Badge variant='outline'>{task.targetSystem}</Badge>
                    {task.directionCode ? (
                      <Badge variant='outline'>{task.directionCode}</Badge>
                    ) : null}
                    <Badge variant={resolveTaskVariant(task.status)}>
                      {formatTaskStatus(task.status)}
                    </Badge>
                  </div>
                  <div className='mt-2 text-sm text-muted-foreground'>
                    {task.taskType} · 请求 {task.requestedAt ?? '--'} · 完成{' '}
                    {task.completedAt ?? '--'}
                  </div>
                  <div className='mt-2 text-sm text-muted-foreground'>
                    负责人 {task.ownerDisplayName ?? task.ownerUserId ?? '--'} · 外部引用{' '}
                    {task.externalRef ?? '--'}
                  </div>
                  {task.dispatchProfile ? (
                    <div className='mt-2 text-sm text-muted-foreground'>
                      {(task.dispatchProfile.transport ?? 'stub').toUpperCase()} ·{' '}
                      {task.dispatchProfile.endpointUrl ?? '--'}
                      {task.dispatchProfile.endpointPath ?? ''}
                    </div>
                  ) : null}
                  <div className='mt-3 flex flex-wrap gap-2'>
                    <Button
                      type='button'
                      size='sm'
                      variant='outline'
                      disabled={
                        pendingTaskAction != null ||
                        task.status === 'SYNCED' ||
                        task.status === 'CANCELLED'
                      }
                      onClick={() => onDispatchTask?.(task)}
                    >
                      {pendingTaskAction?.taskId === task.id &&
                      pendingTaskAction.action === 'dispatch'
                        ? '派发中...'
                        : '重新派发'}
                    </Button>
                    <Button
                      type='button'
                      size='sm'
                      variant='secondary'
                      disabled={
                        pendingTaskAction != null ||
                        !['FAILED', 'PENDING', 'DISPATCHED'].includes(task.status)
                      }
                      onClick={() => onRetryTask?.(task)}
                    >
                      {pendingTaskAction?.taskId === task.id &&
                      pendingTaskAction.action === 'retry'
                        ? '重试中...'
                        : '重试'}
                    </Button>
                  </div>
                  {task.payloadSummary ? (
                    <div className='mt-3 rounded-lg border bg-background/70 p-3 text-sm text-muted-foreground'>
                      <div>{task.payloadSummary}</div>
                      {task.payloadDetails?.map((detail) => (
                        <div key={detail} className='mt-1 text-xs text-muted-foreground'>
                          {detail}
                        </div>
                      ))}
                    </div>
                  ) : null}
                  <div className='mt-3 space-y-2'>
                    {task.dispatchLogs.length === 0 ? (
                      <div className='rounded-lg border border-dashed p-3 text-sm text-muted-foreground'>
                        当前还没有派发日志。
                      </div>
                    ) : (
                      task.dispatchLogs.map((log) => (
                        <DispatchLogItem key={log.id} log={log} />
                      ))
                    )}
                  </div>
                  <div className='mt-3 space-y-2'>
                    {task.receipts.length === 0 ? (
                      <div className='rounded-lg border border-dashed p-3 text-sm text-muted-foreground'>
                        当前还没有回执。
                      </div>
                    ) : (
                      task.receipts.map((receipt) => (
                        <ReceiptItem key={receipt.id} receipt={receipt} />
                      ))
                    )}
                  </div>
                </div>
                {index < normalizedTasks.length - 1 ? <Separator /> : null}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
