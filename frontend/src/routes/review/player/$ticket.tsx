import { useQuery } from '@tanstack/react-query'
import { createFileRoute } from '@tanstack/react-router'
import { AlertCircle, Loader2 } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ApprovalSheetGraph } from '@/features/workbench/approval-sheet-graph'
import { ApprovalPredictionSection } from '@/features/workbench/approval-prediction-section'
import { getWorkbenchReviewTicketDetail } from '@/lib/api/workbench'

export const Route = createFileRoute('/review/player/$ticket')({
  component: ReviewPlayerRoute,
})

function ReviewPlayerRoute() {
  const { ticket } = Route.useParams()
  const compatibilityMode =
    typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('source') === 'weapp'
      ? 'weapp'
      : 'default'
  const detailQuery = useQuery({
    queryKey: ['review-player', ticket],
    queryFn: () => getWorkbenchReviewTicketDetail(ticket),
  })

  if (detailQuery.isLoading) {
    return (
      <div className='flex min-h-screen items-center justify-center bg-slate-50 text-slate-700'>
        <div className='flex items-center gap-3 rounded-full border border-slate-200 bg-white px-5 py-3 shadow-sm'>
          <Loader2 className='size-4 animate-spin' />
          <span className='text-sm font-medium'>正在加载流程回顾...</span>
        </div>
      </div>
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <div className='flex min-h-screen items-center justify-center bg-slate-50 p-6'>
        <Alert className='max-w-lg border-amber-200 bg-white/95 shadow-sm'>
          <AlertCircle className='size-4' />
          <AlertTitle>流程回顾不可用</AlertTitle>
          <AlertDescription>
            当前票据已失效或对应任务不存在。请回到小程序审批详情页重新进入。
          </AlertDescription>
        </Alert>
      </div>
    )
  }

  const detail = detailQuery.data

  return (
    <div className='min-h-screen bg-[radial-gradient(circle_at_top,_rgba(226,232,240,0.85),_rgba(248,250,252,1)_55%)] p-4 md:p-6'>
      <div className='mx-auto flex max-w-6xl flex-col gap-4'>
        <Card className='border-slate-200/80 bg-white/90 shadow-sm backdrop-blur'>
          <CardHeader className='gap-3'>
            <div className='flex flex-wrap items-center gap-3'>
              <CardTitle className='text-2xl font-semibold text-slate-950'>
                {detail.processName}
              </CardTitle>
              <Badge variant='secondary' className='rounded-full px-3 py-1 text-xs'>
                只读回顾
              </Badge>
              {detail.prediction?.predictedRiskThresholdTime ? (
                <Badge variant='outline' className='rounded-full px-3 py-1 text-xs'>
                  预计风险阈值 {new Intl.DateTimeFormat('zh-CN', {
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit',
                    hour12: false,
                  }).format(new Date(detail.prediction.predictedRiskThresholdTime))}
                </Badge>
              ) : null}
            </div>
            <CardDescription className='text-sm text-slate-500'>
              {detail.nodeName} · 实例 {detail.instanceId}
            </CardDescription>
          </CardHeader>
          <CardContent className='pt-0'>
            <div className='mb-4'>
              <ApprovalPredictionSection prediction={detail.prediction ?? null} />
            </div>
            <ApprovalSheetGraph
              flowNodes={detail.flowNodes ?? []}
              flowEdges={detail.flowEdges ?? []}
              taskTrace={detail.taskTrace ?? []}
              instanceEvents={detail.instanceEvents ?? []}
              instanceStatus={detail.instanceStatus}
              prediction={detail.prediction ?? null}
              userDisplayNames={detail.userDisplayNames ?? null}
              compatibilityMode={compatibilityMode}
            />
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
