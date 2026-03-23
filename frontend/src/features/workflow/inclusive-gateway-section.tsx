import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

type InclusiveGatewayHit = {
  splitNodeId: string
  splitNodeName: string
  joinNodeId?: string | null
  joinNodeName?: string | null
  gatewayStatus: string
  totalTargetCount: number
  activatedTargetCount: number
  activatedTargetNodeNames: string[]
  skippedTargetNodeNames: string[]
  firstActivatedAt?: string | null
  finishedAt?: string | null
}

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

export function InclusiveGatewaySection({
  hits,
  title = '包容分支命中',
  description = '展示包容分支实际命中的路径、跳过的路径和汇聚完成状态。',
}: {
  hits: InclusiveGatewayHit[] | null | undefined
  title?: string
  description?: string
}) {
  const items = hits ?? []

  if (!items.length) {
    return null
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        {items.map((hit) => (
          <div key={hit.splitNodeId} className='space-y-3 rounded-lg border p-4'>
            <div className='flex flex-wrap items-center gap-2'>
              <Badge variant='secondary'>包容分支</Badge>
              <Badge variant='outline'>{hit.gatewayStatus}</Badge>
              <span className='text-sm font-medium'>{hit.splitNodeName}</span>
              {hit.joinNodeName ? (
                <span className='text-sm text-muted-foreground'>
                  → {hit.joinNodeName}
                </span>
              ) : null}
            </div>

            <div className='grid gap-2 text-sm text-muted-foreground md:grid-cols-2 xl:grid-cols-4'>
              <div>总路径：{hit.totalTargetCount}</div>
              <div>命中路径：{hit.activatedTargetCount}</div>
              <div>首次命中：{formatDateTime(hit.firstActivatedAt)}</div>
              <div>汇聚完成：{formatDateTime(hit.finishedAt)}</div>
            </div>

            <div className='grid gap-3 md:grid-cols-2'>
              <div className='rounded-md border border-dashed p-3'>
                <p className='text-sm font-medium'>命中路径</p>
                <p className='mt-2 text-sm text-muted-foreground'>
                  {hit.activatedTargetNodeNames.length
                    ? hit.activatedTargetNodeNames.join('、')
                    : '无'}
                </p>
              </div>
              <div className='rounded-md border border-dashed p-3'>
                <p className='text-sm font-medium'>跳过路径</p>
                <p className='mt-2 text-sm text-muted-foreground'>
                  {hit.skippedTargetNodeNames.length
                    ? hit.skippedTargetNodeNames.join('、')
                    : '无'}
                </p>
              </div>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
