import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { WorkbenchProcessPrediction } from '@/lib/api/workbench'

function formatDateTime(value?: string | null) {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function formatMinutes(value?: number | null) {
  if (value === null || value === undefined) {
    return '--'
  }
  if (value < 60) {
    return `${value} 分钟`
  }
  const hours = Math.floor(value / 60)
  const minutes = value % 60
  return minutes === 0 ? `${hours} 小时` : `${hours} 小时 ${minutes} 分钟`
}

function badgeVariantForRisk(risk?: string | null) {
  switch ((risk ?? '').toUpperCase()) {
    case 'HIGH':
      return 'destructive' as const
    case 'MEDIUM':
      return 'secondary' as const
    default:
      return 'outline' as const
  }
}

function badgeVariantForConfidence(confidence?: string | null) {
  switch ((confidence ?? '').toUpperCase()) {
    case 'HIGH':
      return 'default' as const
    case 'MEDIUM':
      return 'secondary' as const
    default:
      return 'outline' as const
  }
}

function labelForRisk(risk?: string | null) {
  switch ((risk ?? '').toUpperCase()) {
    case 'HIGH':
      return '高风险'
    case 'MEDIUM':
      return '中风险'
    case 'LOW':
      return '低风险'
    default:
      return '--'
  }
}

function labelForConfidence(confidence?: string | null) {
  switch ((confidence ?? '').toUpperCase()) {
    case 'HIGH':
      return '高置信度'
    case 'MEDIUM':
      return '中置信度'
    case 'LOW':
      return '低置信度'
    default:
      return '--'
  }
}

export function ApprovalPredictionSection({
  prediction,
}: {
  prediction?: WorkbenchProcessPrediction | null
}) {
  if (!prediction) {
    return null
  }

  return (
    <Card className='border-slate-200/80 bg-white/90 shadow-sm'>
      <CardHeader className='pb-3'>
        <div className='flex items-start justify-between gap-4'>
          <div className='space-y-1'>
            <CardTitle className='text-base font-semibold'>流程预测</CardTitle>
            <p className='text-sm text-muted-foreground'>
              {prediction.basisSummary ?? prediction.noPredictionReason ?? '基于历史样本和当前节点走势生成。'}
            </p>
          </div>
          <div className='flex flex-wrap justify-end gap-2'>
            <Badge variant={badgeVariantForRisk(prediction.overdueRiskLevel)}>
              超期风险 {labelForRisk(prediction.overdueRiskLevel)}
            </Badge>
            <Badge variant={badgeVariantForConfidence(prediction.confidence)}>
              置信度 {labelForConfidence(prediction.confidence)}
            </Badge>
          </div>
        </div>
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='grid gap-3 md:grid-cols-3'>
          <div className='rounded-lg border bg-muted/30 p-4'>
            <div className='text-xs text-muted-foreground'>预计完成</div>
            <div className='mt-2 text-lg font-semibold'>{formatDateTime(prediction.predictedFinishTime)}</div>
          </div>
          <div className='rounded-lg border bg-muted/30 p-4'>
            <div className='text-xs text-muted-foreground'>剩余时长</div>
            <div className='mt-2 text-lg font-semibold'>{formatMinutes(prediction.remainingDurationMinutes)}</div>
          </div>
          <div className='rounded-lg border bg-muted/30 p-4'>
            <div className='text-xs text-muted-foreground'>当前节点已停留</div>
            <div className='mt-2 text-lg font-semibold'>{formatMinutes(prediction.currentElapsedMinutes)}</div>
          </div>
        </div>

        <div className='grid gap-4 md:grid-cols-[1.25fr_1fr]'>
          <div className='space-y-2'>
            {prediction.explanation ? (
              <div className='rounded-lg border bg-slate-50/80 p-3 text-sm text-slate-700'>
                {prediction.explanation}
              </div>
            ) : null}
            <div className='text-sm font-medium'>下一节点候选</div>
            {prediction.nextNodeCandidates?.length ? (
              <div className='space-y-2'>
                {prediction.nextNodeCandidates.map((item) => (
                  <div
                    key={`${item.nodeId}:${item.nodeName}`}
                    className='flex items-center justify-between rounded-lg border bg-muted/20 px-3 py-2'
                  >
                    <div className='space-y-1'>
                      <div className='font-medium'>{item.nodeName}</div>
                      <div className='text-xs text-muted-foreground'>
                        命中 {item.hitCount} 次
                        {item.medianDurationMinutes !== null &&
                        item.medianDurationMinutes !== undefined
                          ? ` · 历史中位 ${formatMinutes(item.medianDurationMinutes)}`
                          : ''}
                      </div>
                    </div>
                    <Badge variant='outline'>{Math.round((item.probability ?? 0) * 100)}%</Badge>
                  </div>
                ))}
              </div>
            ) : (
              <div className='rounded-lg border border-dashed px-3 py-4 text-sm text-muted-foreground'>
                当前没有可用候选节点。
              </div>
            )}
          </div>
          <div className='space-y-2'>
            <div className='text-sm font-medium'>延迟因素</div>
            <div className='rounded-lg border bg-muted/20 p-3'>
              <ul className='space-y-2 text-sm text-muted-foreground'>
                {(prediction.topDelayReasons ?? []).length ? (
                  (prediction.topDelayReasons ?? []).map((item) => <li key={item}>• {item}</li>)
                ) : (
                  <li>• 当前没有明显延迟信号。</li>
                )}
              </ul>
              <div className='mt-3 text-xs text-muted-foreground'>
                历史样本 {prediction.historicalSampleSize ?? 0} 条
              </div>
            </div>
            <div className='text-sm font-medium'>建议动作</div>
            <div className='rounded-lg border bg-muted/20 p-3'>
              <ul className='space-y-2 text-sm text-muted-foreground'>
                {(prediction.recommendedActions ?? []).length ? (
                  (prediction.recommendedActions ?? []).map((item) => <li key={item}>• {item}</li>)
                ) : (
                  <li>• 当前没有额外建议动作。</li>
                )}
              </ul>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
