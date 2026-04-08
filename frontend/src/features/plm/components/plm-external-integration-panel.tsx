import { type PLMExternalIntegration } from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'

function parsePayloadSummary(payload?: string | null) {
  if (!payload) {
    return null
  }
  try {
    const parsed = JSON.parse(payload) as Record<string, unknown>
    if (typeof parsed.message === 'string') {
      return parsed.message
    }
    if (
      typeof parsed.systemCode === 'string' &&
      typeof parsed.ackStatus === 'string'
    ) {
      return `${parsed.systemCode} ${parsed.ackStatus}`
    }
  } catch {
    return payload
  }
  return payload
}

export function PLMExternalIntegrationPanel({
  integrations,
}: {
  integrations: PLMExternalIntegration[]
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>外部系统边界</CardTitle>
        <CardDescription>
          展示当前 PLM 变更与 ERP、MES、PDM、CAD 等外部系统的同步边界与最近事件。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        {integrations.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前没有外部集成记录。
          </div>
        ) : (
          integrations.map((integration, index) => (
            <div key={integration.id} className='space-y-3'>
              <div className='rounded-xl border bg-muted/15 p-4'>
                <div className='flex flex-wrap items-center gap-2'>
                  <div className='font-medium'>{integration.systemName}</div>
                  <Badge variant='outline'>{integration.directionCode}</Badge>
                  <Badge
                    variant={
                      integration.status === 'SYNCED'
                        ? 'secondary'
                        : integration.status === 'FAILED'
                          ? 'destructive'
                          : 'outline'
                    }
                  >
                    {integration.status}
                  </Badge>
                </div>
                <div className='mt-2 text-sm text-muted-foreground'>
                  {integration.integrationType} · {integration.endpointKey ?? '--'} · 外部引用{' '}
                  {integration.externalRef ?? '--'}
                </div>
                {integration.message ? (
                  <div className='mt-2 text-sm text-muted-foreground'>
                    {integration.message}
                  </div>
                ) : null}
                <div className='mt-3 space-y-2'>
                  {integration.events.map((event) => (
                    <div
                      key={event.id}
                      className='rounded-lg border bg-background/70 p-3 text-sm'
                    >
                      <div className='flex flex-wrap items-center gap-2'>
                        <Badge variant='outline'>{event.eventType}</Badge>
                        <Badge
                          variant={
                            event.status === 'SUCCESS'
                              ? 'secondary'
                              : event.status === 'FAILED'
                                ? 'destructive'
                                : 'outline'
                          }
                        >
                          {event.status}
                        </Badge>
                        <span className='text-xs text-muted-foreground'>
                          {event.happenedAt ?? '--'}
                        </span>
                      </div>
                      {event.errorMessage ? (
                        <div className='mt-2 text-xs text-destructive'>
                          {event.errorMessage}
                        </div>
                      ) : null}
                      {parsePayloadSummary(event.payloadJson) ? (
                        <div className='mt-2 text-xs text-muted-foreground'>
                          {parsePayloadSummary(event.payloadJson)}
                        </div>
                      ) : null}
                    </div>
                  ))}
                </div>
              </div>
              {index < integrations.length - 1 ? <Separator /> : null}
            </div>
          ))
        )}
      </CardContent>
    </Card>
  )
}
