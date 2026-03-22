import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  formatApprovalSheetDateTime,
  resolveApprovalSheetAutomationStatusLabel,
} from '@/features/workbench/approval-sheet-list'
import {
  type WorkbenchAutomationActionTraceItem,
  type WorkbenchNotificationSendRecord,
} from '@/lib/api/workbench'

// 自动动作轨迹状态只展示简短中文标签。
function resolveTraceStatusLabel(status: string) {
  switch (status) {
    case 'SUCCESS':
      return '成功'
    case 'FAILED':
      return '失败'
    case 'RUNNING':
      return '执行中'
    case 'SKIPPED':
      return '已跳过'
    default:
      return status
  }
}

// 自动动作轨迹的 badge 颜色保持统一。
function resolveTraceStatusVariant(status: string) {
  switch (status) {
    case 'SUCCESS':
      return 'secondary'
    case 'FAILED':
      return 'destructive'
    case 'RUNNING':
      return 'default'
    default:
      return 'outline'
  }
}

// 通知发送记录状态统一转成中文文案。
function resolveRecordStatusLabel(status: string) {
  switch (status) {
    case 'SUCCESS':
      return '发送成功'
    case 'FAILED':
      return '发送失败'
    case 'PENDING':
      return '待发送'
    case 'RETRYING':
      return '重试中'
    default:
      return status
  }
}

// 通知发送记录状态也复用同一套 badge 语义。
function resolveRecordStatusVariant(status: string) {
  switch (status) {
    case 'SUCCESS':
      return 'secondary'
    case 'FAILED':
      return 'destructive'
    case 'PENDING':
    case 'RETRYING':
      return 'default'
    default:
      return 'outline'
  }
}

// 轨迹标题把名称和类型拼在一起，方便快速识别。
function resolveAutomationTraceLabel(item: WorkbenchAutomationActionTraceItem) {
  return `${item.traceName} · ${item.traceType}`
}

export function ApprovalSheetAutomationStatusCard({
  automationStatus,
  actionTraceCount,
  notificationCount,
}: {
  automationStatus: string | null | undefined
  actionTraceCount: number
  notificationCount: number
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>自动化状态</CardTitle>
        <CardDescription>展示当前审批单的自动化执行态、动作轨迹数量和通知记录数量。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-3'>
        <Badge variant='secondary'>
          {resolveApprovalSheetAutomationStatusLabel(automationStatus)}
        </Badge>
        <div className='grid grid-cols-2 gap-3 text-sm'>
          <div className='rounded-lg border bg-muted/20 p-3'>
            <p className='text-xs text-muted-foreground'>自动动作轨迹</p>
            <p className='mt-2 font-medium'>{actionTraceCount}</p>
          </div>
          <div className='rounded-lg border bg-muted/20 p-3'>
            <p className='text-xs text-muted-foreground'>通知发送记录</p>
            <p className='mt-2 font-medium'>{notificationCount}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

export function ApprovalSheetAutomationActionTimeline({
  automationActionTrace,
}: {
  automationActionTrace: WorkbenchAutomationActionTraceItem[] | null | undefined
}) {
  const items = automationActionTrace ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>自动动作轨迹</CardTitle>
        <CardDescription>记录自动规则命中、条件判断和自动动作执行结果，帮助定位自动化链路。</CardDescription>
      </CardHeader>
      <CardContent>
        {items.length ? (
          <ol className='space-y-3 border-l border-dashed pl-4'>
            {items.map((item, index) => (
              <li key={`${item.traceId}:${index}`} className='space-y-2'>
                <div className='flex flex-wrap items-center gap-2'>
                  <Badge variant={resolveTraceStatusVariant(item.status)}>
                    {resolveTraceStatusLabel(item.status)}
                  </Badge>
                  <span className='font-medium'>{resolveAutomationTraceLabel(item)}</span>
                </div>
                <div className='grid gap-1 text-sm text-muted-foreground'>
                  <div className='flex flex-wrap gap-x-3 gap-y-1'>
                    <span>操作人：{item.operatorUserId ?? '--'}</span>
                    <span>发生时间：{formatApprovalSheetDateTime(item.occurredAt)}</span>
                  </div>
                  <p>说明：{item.detail?.trim() || '--'}</p>
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <div className='rounded-lg border border-dashed p-6 text-sm text-muted-foreground'>
            暂无自动动作轨迹。
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export function NotificationSendRecordSection({
  notificationSendRecords,
}: {
  notificationSendRecords: WorkbenchNotificationSendRecord[] | null | undefined
}) {
  const items = notificationSendRecords ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>通知发送记录</CardTitle>
        <CardDescription>记录每次通知发送的渠道、目标、重试次数和失败原因，方便排查消息链路。</CardDescription>
      </CardHeader>
      <CardContent>
        {items.length ? (
          <div className='grid gap-3'>
            {items.map((item, index) => (
              <div key={`${item.recordId}:${index}`} className='rounded-lg border bg-muted/20 p-4'>
                <div className='flex flex-wrap items-center justify-between gap-3'>
                  <div className='space-y-1'>
                    <p className='text-sm font-medium'>{item.channelName}</p>
                    <p className='text-xs text-muted-foreground'>
                      {item.channelType} · {item.target}
                    </p>
                  </div>
                  <Badge variant={resolveRecordStatusVariant(item.status)}>
                    {resolveRecordStatusLabel(item.status)}
                  </Badge>
                </div>
                <div className='mt-3 grid gap-2 text-sm text-muted-foreground sm:grid-cols-2'>
                  <div>发送时间：{formatApprovalSheetDateTime(item.sentAt)}</div>
                  <div>重试次数：{item.attemptCount}</div>
                  <div className='sm:col-span-2'>失败原因：{item.errorMessage?.trim() || '--'}</div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className='rounded-lg border border-dashed p-6 text-sm text-muted-foreground'>
            暂无通知发送记录。
          </div>
        )}
      </CardContent>
    </Card>
  )
}
