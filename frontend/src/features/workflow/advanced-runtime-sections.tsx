import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type {
  ProcessCollaborationEvent,
  ProcessTerminationAudit,
  ProcessTerminationSnapshot,
  ProcessTimeTravelExecution,
} from '@/lib/api/process-runtime-advanced'

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
    second: '2-digit',
    hour12: false,
  }).format(date)
}

export function ProcessCollaborationSection({
  items,
}: {
  items: ProcessCollaborationEvent[]
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>协同轨迹</CardTitle>
        <CardDescription>展示当前实例上的协同知会、批注和提醒事件。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-3'>
        {items.length === 0 ? (
          <div className='rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground'>
            当前没有协同事件。
          </div>
        ) : (
          items.map((item) => (
            <div key={item.eventId} className='rounded-md border px-4 py-3 text-sm'>
              <div className='flex flex-wrap items-center gap-2'>
                <Badge variant='outline'>{item.eventType}</Badge>
                <span className='font-medium'>{item.subject || '协同事件'}</span>
              </div>
              <div className='mt-2 text-muted-foreground'>{item.content || '--'}</div>
              <div className='mt-2 grid gap-2 text-xs text-muted-foreground md:grid-cols-3'>
                <div>操作人：{item.operatorUserId || '--'}</div>
                <div>任务：{item.taskId || '--'}</div>
                <div>时间：{formatDateTime(item.occurredAt)}</div>
              </div>
              {item.mentionedUserIds.length > 0 ? (
                <div className='mt-2 text-xs text-muted-foreground'>
                  @提醒：{item.mentionedUserIds.join('、')}
                </div>
              ) : null}
            </div>
          ))
        )}
      </CardContent>
    </Card>
  )
}

export function ProcessTimeTravelSection({
  items,
}: {
  items: ProcessTimeTravelExecution[]
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>穿越时空轨迹</CardTitle>
        <CardDescription>展示回退到历史节点、重开实例等高级回退操作。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-3'>
        {items.length === 0 ? (
          <div className='rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground'>
            当前没有穿越时空记录。
          </div>
        ) : (
          items.map((item) => (
            <div key={item.executionId} className='rounded-md border px-4 py-3 text-sm'>
              <div className='flex flex-wrap items-center gap-2'>
                <Badge variant='outline'>{item.strategy}</Badge>
                <span className='font-medium'>
                  {item.strategy === 'BACK_TO_NODE' ? '回退到历史节点' : '重开流程实例'}
                </span>
              </div>
              <div className='mt-2 grid gap-2 text-xs text-muted-foreground md:grid-cols-2 xl:grid-cols-4'>
                <div>操作人：{item.operatorUserId || '--'}</div>
                <div>源任务：{item.taskId || '--'}</div>
                <div>目标节点：{item.targetNodeId || '--'}</div>
                <div>新实例：{item.newInstanceId || '--'}</div>
              </div>
              <div className='mt-2 text-xs text-muted-foreground'>
                时间：{formatDateTime(item.occurredAt)}
              </div>
            </div>
          ))
        )}
      </CardContent>
    </Card>
  )
}

export function ProcessTerminationSection({
  snapshot,
  audits,
}: {
  snapshot: ProcessTerminationSnapshot | null
  audits: ProcessTerminationAudit[]
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>终止高级策略</CardTitle>
        <CardDescription>展示当前实例终止传播范围预览，以及已发生的终止审计记录。</CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        {snapshot ? (
          <div className='rounded-md border bg-muted/20 p-3 text-sm'>
            <div className='flex flex-wrap items-center gap-2'>
              <Badge variant='secondary'>{snapshot.scope}</Badge>
              <Badge variant='outline'>{snapshot.propagationPolicy}</Badge>
              <span className='font-medium'>{snapshot.summary}</span>
            </div>
            <div className='mt-2 grid gap-2 text-xs text-muted-foreground md:grid-cols-3'>
              <div>根实例：{snapshot.rootInstanceId}</div>
              <div>目标数量：{snapshot.targetCount}</div>
              <div>生成时间：{formatDateTime(snapshot.generatedAt)}</div>
            </div>
          </div>
        ) : (
          <div className='rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground'>
            暂无终止策略快照。
          </div>
        )}

        {audits.length === 0 ? (
          <div className='rounded-md border border-dashed px-4 py-6 text-sm text-muted-foreground'>
            当前没有终止审计记录。
          </div>
        ) : (
          <div className='space-y-3'>
            {audits.map((item) => (
              <div key={item.auditId} className='rounded-md border px-4 py-3 text-sm'>
                <div className='flex flex-wrap items-center gap-2'>
                  <Badge variant='outline'>{item.eventType}</Badge>
                  <Badge variant='secondary'>{item.propagationPolicy}</Badge>
                  <span className='font-medium'>{item.resultStatus}</span>
                </div>
                <div className='mt-2 grid gap-2 text-xs text-muted-foreground md:grid-cols-2 xl:grid-cols-4'>
                  <div>目标实例：{item.targetInstanceId || '--'}</div>
                  <div>目标类型：{item.targetKind || '--'}</div>
                  <div>操作人：{item.operatorUserId || '--'}</div>
                  <div>时间：{formatDateTime(item.createdAt)}</div>
                </div>
                <div className='mt-2 text-xs text-muted-foreground'>
                  原因：{item.reason || '--'}
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
