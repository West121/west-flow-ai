import { type PLMExternalSyncEventEnvelope } from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'

function resolveStatusVariant(status: string) {
  if (status === 'SUCCESS') {
    return 'secondary'
  }
  if (status === 'FAILED') {
    return 'destructive'
  }
  return 'outline'
}

function formatStatus(status: string) {
  switch (status) {
    case 'SUCCESS':
      return '成功'
    case 'FAILED':
      return '失败'
    case 'PENDING':
      return '待处理'
    default:
      return status
  }
}

function summarizePayload(payload?: string | null) {
  if (!payload) {
    return null
  }
  try {
    const parsed = JSON.parse(payload) as Record<string, unknown>
    if (typeof parsed.message === 'string') {
      return parsed.message
    }
    if (
      typeof parsed.jobId === 'string' &&
      typeof parsed.ackStatus === 'string'
    ) {
      return `任务 ${parsed.jobId} · ${parsed.ackStatus}`
    }
    if (
      typeof parsed.jobId === 'string' &&
      typeof parsed.operatorUserId === 'string'
    ) {
      return `任务 ${parsed.jobId} · 操作人 ${parsed.operatorUserId}`
    }
  } catch {
    return payload
  }
  return payload
}

export function PLMExternalSyncEventPanel({
  events,
}: {
  events: PLMExternalSyncEventEnvelope[]
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>外部同步事件</CardTitle>
        <CardDescription>
          查看 ERP、MES、PDM、CAD 等外部边界的最近同步时间线，用于定位失败与待处理事件。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {events.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前没有外部同步事件。
          </div>
        ) : (
          <ScrollArea className='max-h-[24rem]'>
            <div className='space-y-3'>
              {events.map((event) => (
                <div
                  key={event.id}
                  className='rounded-xl border bg-muted/15 p-4'
                >
                  <div className='flex flex-wrap items-center gap-2'>
                    <div className='font-medium'>{event.systemName}</div>
                    <Badge variant='outline'>{event.directionCode}</Badge>
                    <Badge variant={resolveStatusVariant(event.status)}>
                      {formatStatus(event.status)}
                    </Badge>
                    <Badge variant='outline'>{event.eventType}</Badge>
                  </div>
                  <div className='mt-2 text-xs text-muted-foreground'>
                    {event.happenedAt ?? '--'}
                  </div>
                  {summarizePayload(event.payloadJson) ? (
                    <div className='mt-2 text-xs text-muted-foreground'>
                      {summarizePayload(event.payloadJson)}
                    </div>
                  ) : null}
                  {event.errorMessage ? (
                    <div className='mt-2 text-xs text-destructive'>
                      错误: {event.errorMessage}
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          </ScrollArea>
        )}
      </CardContent>
    </Card>
  )
}
