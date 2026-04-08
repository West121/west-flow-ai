import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { WorkbenchDashboardSummary } from '@/lib/api/workbench'

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

export function ApprovalPredictionDashboard({
  summary,
}: {
  summary?: WorkbenchDashboardSummary
}) {
  if (!summary) {
    return null
  }

  const riskDistribution = summary.riskDistribution ?? {}
  const overdueTrend = summary.overdueTrend ?? []
  const bottleneckNodes = summary.bottleneckNodes ?? []
  const topRiskProcesses = summary.topRiskProcesses ?? []

  return (
    <div className='grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]'>
      <Card>
        <CardHeader>
          <CardTitle>预测运营面板</CardTitle>
          <CardDescription>按风险分布、预计超期趋势和瓶颈节点快速判断今天的调度重点。</CardDescription>
        </CardHeader>
        <CardContent className='grid gap-4 md:grid-cols-2'>
          <div className='space-y-3 rounded-lg border bg-muted/20 p-4'>
            <div className='text-sm font-medium'>风险分布</div>
            <div className='space-y-2 text-sm text-muted-foreground'>
              <div className='flex items-center justify-between'>
                <span>高风险</span>
                <span className='font-semibold text-foreground'>{riskDistribution.HIGH ?? 0}</span>
              </div>
              <div className='flex items-center justify-between'>
                <span>中风险</span>
                <span className='font-semibold text-foreground'>{riskDistribution.MEDIUM ?? 0}</span>
              </div>
              <div className='flex items-center justify-between'>
                <span>低风险</span>
                <span className='font-semibold text-foreground'>{riskDistribution.LOW ?? 0}</span>
              </div>
            </div>
          </div>

          <div className='space-y-3 rounded-lg border bg-muted/20 p-4'>
            <div className='text-sm font-medium'>预计超期趋势（7 天）</div>
            {overdueTrend.length ? (
              <div className='space-y-2 text-sm text-muted-foreground'>
                {overdueTrend.map((item) => (
                  <div key={item.date} className='flex items-center justify-between'>
                    <span>{item.date}</span>
                    <span className='font-semibold text-foreground'>{item.count}</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className='text-sm text-muted-foreground'>当前没有可用趋势样本。</div>
            )}
          </div>
        </CardContent>
      </Card>

      <div className='grid gap-4 md:grid-cols-2'>
        <Card>
          <CardHeader>
            <CardTitle>节点瓶颈排行</CardTitle>
            <CardDescription>按高风险数量优先看当前最容易拖期的节点。</CardDescription>
          </CardHeader>
          <CardContent className='space-y-3'>
            {bottleneckNodes.length ? (
              bottleneckNodes.map((item) => (
                <div key={item.nodeId} className='rounded-lg border bg-muted/20 p-3'>
                  <div className='font-medium'>{item.nodeName}</div>
                  <div className='mt-1 text-xs text-muted-foreground'>
                    高风险 {item.highRiskCount} · 总量 {item.totalCount} · 中位剩余 {formatMinutes(item.medianRemainingDurationMinutes)}
                  </div>
                </div>
              ))
            ) : (
              <div className='rounded-lg border border-dashed px-3 py-4 text-sm text-muted-foreground'>
                当前没有明显瓶颈节点。
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>高风险流程排行</CardTitle>
            <CardDescription>识别最需要流程治理的流程定义。</CardDescription>
          </CardHeader>
          <CardContent className='space-y-3'>
            {topRiskProcesses.length ? (
              topRiskProcesses.map((item) => (
                <div key={item.processKey} className='rounded-lg border bg-muted/20 p-3'>
                  <div className='font-medium'>{item.processName}</div>
                  <div className='mt-1 text-xs text-muted-foreground'>
                    高风险 {item.highRiskCount} / {item.totalCount} · 风险率 {Math.round(item.highRiskRate * 100)}%
                  </div>
                </div>
              ))
            ) : (
              <div className='rounded-lg border border-dashed px-3 py-4 text-sm text-muted-foreground'>
                当前没有高风险流程排行数据。
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
