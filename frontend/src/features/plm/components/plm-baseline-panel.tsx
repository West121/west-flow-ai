import { type PLMConfigurationBaseline } from '@/lib/api/plm'
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

export function PLMBaselinePanel({
  baselines,
  pendingBaselineId,
  onReleaseBaseline,
}: {
  baselines: PLMConfigurationBaseline[]
  pendingBaselineId?: string | null
  onReleaseBaseline?: (baseline: PLMConfigurationBaseline) => void
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>配置基线</CardTitle>
        <CardDescription>
          汇总当前单据的目标基线与覆盖对象。
        </CardDescription>
      </CardHeader>
      <CardContent>
        {baselines.length === 0 ? (
          <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
            当前没有配置基线。
          </div>
        ) : (
          <div className='space-y-4'>
            {baselines.map((baseline, index) => (
              <div key={baseline.id} className='space-y-3'>
                <div className='flex flex-wrap items-center gap-2'>
                  <div className='font-medium'>{baseline.baselineName}</div>
                  <Badge variant='outline'>{baseline.baselineType}</Badge>
                  <Badge variant='secondary'>{baseline.status}</Badge>
                  {baseline.status !== 'RELEASED' ? (
                    <Button
                      type='button'
                      size='sm'
                      variant='outline'
                      disabled={pendingBaselineId != null}
                      onClick={() => onReleaseBaseline?.(baseline)}
                    >
                      {pendingBaselineId === baseline.id ? '发布中...' : '发布基线'}
                    </Button>
                  ) : null}
                </div>
                <div className='text-sm text-muted-foreground'>
                  {baseline.baselineCode}
                  {baseline.releasedAt ? ` · 发布时间 ${baseline.releasedAt}` : ''}
                </div>
                <div className='space-y-2 rounded-xl border bg-muted/15 p-4'>
                  {baseline.items.map((item) => (
                    <div
                      key={item.id}
                      className='flex flex-wrap items-center justify-between gap-2 text-sm'
                    >
                      <div className='font-medium'>
                        {item.objectName} <span className='text-muted-foreground'>({item.objectCode})</span>
                      </div>
                      <div className='text-muted-foreground'>
                        {item.beforeRevisionCode ?? '--'} → {item.afterRevisionCode ?? '--'}
                      </div>
                    </div>
                  ))}
                </div>
                {index < baselines.length - 1 ? <Separator /> : null}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
