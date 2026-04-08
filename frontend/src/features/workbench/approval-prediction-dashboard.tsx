import { useMemo } from 'react'
import { AlertCircle, ArrowRight, CheckCircle2, Clock3, Sparkles, Target } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type {
  WorkbenchDashboardSummary,
  WorkbenchTaskPageResponse,
} from '@/lib/api/workbench'

type PredictionDashboardProps = {
  summary?: WorkbenchDashboardSummary | null
  taskPage?: WorkbenchTaskPageResponse | null
}

type DerivedBucket = {
  nodeId: string
  nodeName: string
  totalCount: number
  highRiskCount: number
  medianRemainingDurationMinutes: number | null
}

type DerivedProcessBucket = {
  processKey: string
  processName: string
  totalCount: number
  highRiskCount: number
  highRiskRate: number
}

type DerivedOperationsSnapshot = {
  totalCount: number
  predictedCount: number
  predictionCoverageRate: number | null
  highRiskCount: number
  mediumRiskCount: number
  overdueThresholdCount: number
  automationActionCount: number
  recommendedActionCount: number
  pathCandidateCount: number
  riskDistribution: Record<'HIGH' | 'MEDIUM' | 'LOW', number>
  thresholdTrend: Array<{ date: string; count: number }>
  bottleneckNodes: DerivedBucket[]
  topRiskProcesses: DerivedProcessBucket[]
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

function formatPercent(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return `${Math.round(value * 100)}%`
}

function formatShortDate(value?: string | null) {
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
  }).format(date)
}

function normalizeRisk(risk?: string | null) {
  return (risk ?? '').toUpperCase()
}

function median(values: number[]) {
  if (values.length === 0) {
    return null
  }
  const sorted = [...values].sort((left, right) => left - right)
  const middle = Math.floor(sorted.length / 2)
  if (sorted.length % 2 === 1) {
    return sorted[middle] ?? null
  }
  const left = sorted[middle - 1]
  const right = sorted[middle]
  if (left === undefined || right === undefined) {
    return null
  }
  return Math.round((left + right) / 2)
}

function buildDerivedOperationsSnapshot(
  taskPage?: WorkbenchTaskPageResponse | null
): DerivedOperationsSnapshot {
  const records = taskPage?.records ?? []
  const totalCount = taskPage?.total ?? records.length
  const predictedRecords = records.filter((item) => Boolean(item.prediction))
  const highRiskRecords = records.filter(
    (item) => normalizeRisk(item.prediction?.overdueRiskLevel) === 'HIGH'
  )
  const mediumRiskRecords = records.filter(
    (item) => normalizeRisk(item.prediction?.overdueRiskLevel) === 'MEDIUM'
  )
  const overdueThresholdRecords = records.filter((item) =>
    Boolean(item.prediction?.predictedRiskThresholdTime)
  )

  const automationActionCount = records.reduce(
    (sum, item) => sum + (item.prediction?.automationActions?.length ?? 0),
    0
  )
  const recommendedActionCount = records.reduce(
    (sum, item) => sum + (item.prediction?.recommendedActions?.length ?? 0),
    0
  )
  const pathCandidateCount = records.reduce(
    (sum, item) => sum + (item.prediction?.nextNodeCandidates?.length ?? 0),
    0
  )

  const riskDistribution = records.reduce<Record<'HIGH' | 'MEDIUM' | 'LOW', number>>(
    (accumulator, item) => {
      const risk = normalizeRisk(item.prediction?.overdueRiskLevel)
      if (risk === 'HIGH' || risk === 'MEDIUM' || risk === 'LOW') {
        accumulator[risk] += 1
      }
      return accumulator
    },
    { HIGH: 0, MEDIUM: 0, LOW: 0 }
  )

  const thresholdTrend = records.reduce<Map<string, number>>((accumulator, item) => {
    const thresholdTime = item.prediction?.predictedRiskThresholdTime
    if (!thresholdTime) {
      return accumulator
    }
    const key = formatShortDate(thresholdTime)
    accumulator.set(key, (accumulator.get(key) ?? 0) + 1)
    return accumulator
  }, new Map())

  const bottleneckMap = new Map<
    string,
    {
      nodeId: string
      nodeName: string
      totalCount: number
      highRiskCount: number
      durations: number[]
    }
  >()
  const processMap = new Map<
    string,
    {
      processKey: string
      processName: string
      totalCount: number
      highRiskCount: number
    }
  >()

  for (const item of records) {
    if (item.prediction) {
      const nodeKey = `${item.nodeId}:${item.nodeName}`
      const bottleneck = bottleneckMap.get(nodeKey) ?? {
        nodeId: item.nodeId,
        nodeName: item.nodeName,
        totalCount: 0,
        highRiskCount: 0,
        durations: [],
      }
      bottleneck.totalCount += 1
      if (normalizeRisk(item.prediction.overdueRiskLevel) === 'HIGH') {
        bottleneck.highRiskCount += 1
      }
      if (item.prediction.remainingDurationMinutes !== null && item.prediction.remainingDurationMinutes !== undefined) {
        bottleneck.durations.push(item.prediction.remainingDurationMinutes)
      }
      bottleneckMap.set(nodeKey, bottleneck)
    }

    const processKey = `${item.processKey}:${item.processName}`
    const processBucket = processMap.get(processKey) ?? {
      processKey: item.processKey,
      processName: item.processName,
      totalCount: 0,
      highRiskCount: 0,
    }
    processBucket.totalCount += 1
    if (normalizeRisk(item.prediction?.overdueRiskLevel) === 'HIGH') {
      processBucket.highRiskCount += 1
    }
    processMap.set(processKey, processBucket)
  }

  const bottleneckNodes = Array.from(bottleneckMap.values())
    .map((item) => ({
      ...item,
      medianRemainingDurationMinutes: median(item.durations),
    }))
    .sort((left, right) => {
      if (right.highRiskCount !== left.highRiskCount) {
        return right.highRiskCount - left.highRiskCount
      }
      if (right.totalCount !== left.totalCount) {
        return right.totalCount - left.totalCount
      }
      return left.nodeName.localeCompare(right.nodeName, 'zh-CN')
    })
    .slice(0, 5)

  const topRiskProcesses = Array.from(processMap.values())
    .map((item) => ({
      ...item,
      highRiskRate: item.totalCount === 0 ? 0 : item.highRiskCount / item.totalCount,
    }))
    .sort((left, right) => {
      if (right.highRiskRate !== left.highRiskRate) {
        return right.highRiskRate - left.highRiskRate
      }
      if (right.highRiskCount !== left.highRiskCount) {
        return right.highRiskCount - left.highRiskCount
      }
      return left.processName.localeCompare(right.processName, 'zh-CN')
    })
    .slice(0, 5)

  return {
    totalCount,
    predictedCount: predictedRecords.length,
    predictionCoverageRate: records.length > 0 ? predictedRecords.length / records.length : null,
    highRiskCount: highRiskRecords.length,
    mediumRiskCount: mediumRiskRecords.length,
    overdueThresholdCount: overdueThresholdRecords.length,
    automationActionCount,
    recommendedActionCount,
    pathCandidateCount,
    riskDistribution,
    thresholdTrend: Array.from(thresholdTrend.entries())
      .map(([date, count]) => ({ date, count }))
      .sort((left, right) => left.date.localeCompare(right.date, 'zh-CN')),
    bottleneckNodes,
    topRiskProcesses,
  }
}

function buildOperationalAdvice(snapshot: DerivedOperationsSnapshot) {
  const advice: string[] = []

  if (snapshot.highRiskCount > 0) {
    advice.push(`先处理 ${snapshot.highRiskCount} 条高风险待办，避免风险继续放大。`)
  }
  if (snapshot.overdueThresholdCount > 0) {
    advice.push(`有 ${snapshot.overdueThresholdCount} 条任务已命中高风险阈值，优先催办。`)
  }
  if (snapshot.automationActionCount > 0) {
    advice.push(`当前样本中有 ${snapshot.automationActionCount} 条自动化建议，适合批量触发提醒。`)
  }
  if (snapshot.predictionCoverageRate !== null && snapshot.predictionCoverageRate < 0.8) {
    advice.push('当前预测覆盖率还可继续提升，优先补齐缺少样本的流程和节点。')
  }

  if (advice.length === 0) {
    advice.push('当前风险和自动化信号都较平稳，可继续维持按风险优先调度。')
  }

  return advice
}

export function ApprovalPredictionDashboard({
  summary,
  taskPage,
}: PredictionDashboardProps) {
  if (!summary) {
    return null
  }

  const snapshot = buildDerivedOperationsSnapshot(taskPage)
  const operationalAdvice = buildOperationalAdvice(snapshot)
  const hasTaskPage = Boolean(taskPage?.records?.length)
  const riskDistribution = summary.riskDistribution ?? snapshot.riskDistribution
  const overdueTrend = summary.overdueTrend ?? snapshot.thresholdTrend
  const bottleneckNodes = summary.bottleneckNodes?.length ? summary.bottleneckNodes : snapshot.bottleneckNodes
  const topRiskProcesses = summary.topRiskProcesses?.length ? summary.topRiskProcesses : snapshot.topRiskProcesses
  const governance = summary.automationGovernance
  const automationMetrics = summary.automationMetrics

  return (
    <div className='grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]'>
      <Card className='border-slate-200/80 bg-white/90 shadow-sm'>
        <CardHeader className='gap-3'>
          <div className='flex items-start justify-between gap-3'>
            <div className='space-y-1'>
              <CardTitle>预测运营中心</CardTitle>
              <CardDescription>
                聚合高风险待办、路径级预测和自动化建议，把调度动作前置到今天。
              </CardDescription>
            </div>
            <Badge variant='outline' className='rounded-full px-3 py-1 text-xs'>
              {hasTaskPage ? `${snapshot.predictedCount} / ${snapshot.totalCount}` : '等待样本'}
            </Badge>
          </div>
        </CardHeader>
        <CardContent className='grid gap-3 md:grid-cols-2 xl:grid-cols-4'>
          {[
            {
              title: '高风险待办',
              value: summary.highRiskTodoCount ?? snapshot.highRiskCount,
              description: '优先催办和预警的目标任务。',
              icon: AlertCircle,
            },
            {
              title: '预计今日超期',
              value: summary.overdueTodayCount ?? snapshot.overdueThresholdCount,
              description: '今天可能进入高风险阈值的待办。',
              icon: Sparkles,
            },
            {
              title: '预测覆盖率',
              value:
                snapshot.predictionCoverageRate === null
                  ? '--'
                  : formatPercent(snapshot.predictionCoverageRate),
              description: hasTaskPage
                ? `当前样本已生成预测 ${snapshot.predictedCount} / ${snapshot.totalCount}`
                : '当前没有可用于统计的待办样本。',
              icon: Target,
            },
            {
              title: '自动化建议',
              value: snapshot.automationActionCount,
              description: '可触发提醒、预告和协同动作的总数。',
              icon: ArrowRight,
            },
            {
              title: '路径候选数',
              value: snapshot.pathCandidateCount,
              description: '待办中已生成的下一节点候选总数。',
              icon: CheckCircle2,
            },
            {
              title: '今日待办',
              value: summary.todoTodayCount,
              description: '当前登录人今日新增且仍需处理的任务数量。',
              icon: Clock3,
            },
            {
              title: '节点瓶颈数',
              value: bottleneckNodes.length,
              description: '高风险数靠前、适合优先治理的节点。',
              icon: AlertCircle,
            },
            {
              title: '高风险流程数',
              value: topRiskProcesses.length,
              description: '需要优先做流程治理的流程定义。',
              icon: CheckCircle2,
            },
          ].map((item) => (
            <div key={item.title} className='rounded-xl border bg-muted/20 p-4'>
              <div className='flex items-center justify-between gap-3'>
                <div className='text-sm text-muted-foreground'>{item.title}</div>
                <item.icon className='size-4 text-muted-foreground' />
              </div>
              <div className='mt-2 text-2xl font-semibold tracking-tight text-foreground'>
                {typeof item.value === 'number' ? item.value : item.value}
              </div>
              <p className='mt-2 text-xs leading-5 text-muted-foreground'>{item.description}</p>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card className='border-slate-200/80 bg-white/90 shadow-sm'>
        <CardHeader className='gap-3'>
          <CardTitle>运营建议</CardTitle>
          <CardDescription>
            把今天应该催谁、先看哪条路径、要不要上自动化提醒说清楚。
          </CardDescription>
        </CardHeader>
        <CardContent className='space-y-3'>
          {operationalAdvice.map((item) => (
            <div
              key={item}
              className='rounded-xl border border-dashed bg-slate-50/90 px-4 py-3 text-sm text-slate-700'
            >
              {item}
            </div>
          ))}
          <div className='grid gap-3 md:grid-cols-2'>
            <div className='rounded-xl border bg-muted/20 p-4'>
              <div className='text-xs text-muted-foreground'>风险优先队列</div>
              <div className='mt-2 text-lg font-semibold text-foreground'>
                {snapshot.highRiskCount + snapshot.mediumRiskCount}
              </div>
              <div className='mt-1 text-xs text-muted-foreground'>
                高风险与中风险待办合并排序，先处理最可能拖期的任务。
              </div>
            </div>
            <div className='rounded-xl border bg-muted/20 p-4'>
              <div className='text-xs text-muted-foreground'>样本治理</div>
              <div className='mt-2 text-lg font-semibold text-foreground'>
                {snapshot.recommendedActionCount}
              </div>
              <div className='mt-1 text-xs text-muted-foreground'>
                已生成建议动作的预测样本，适合做自动化治理和运营复盘。
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card className='border-slate-200/80 bg-white/90 shadow-sm xl:col-span-2'>
        <CardHeader className='gap-2'>
          <CardTitle>自动化治理与执行效果</CardTitle>
          <CardDescription>治理开关、静默时间窗、节流窗口和最近 7 天执行效果。</CardDescription>
        </CardHeader>
        <CardContent className='grid gap-3 lg:grid-cols-[1.05fr_1fr]'>
          <div className='grid gap-3 md:grid-cols-2'>
            <div className='rounded-xl border bg-muted/20 p-4'>
              <div className='text-xs text-muted-foreground'>治理状态</div>
              <div className='mt-2 flex flex-wrap gap-2'>
                <Badge variant={governance?.automationEnabled ? 'default' : 'secondary'}>
                  {governance?.automationEnabled ? '自动动作已启用' : '自动动作已关闭'}
                </Badge>
                <Badge variant='outline'>
                  {governance?.respectQuietHours
                    ? `静默时间窗 ${governance.quietHoursWindow}`
                    : '不限制静默时间窗'}
                </Badge>
                <Badge variant='outline'>
                  节流窗口 {governance?.dedupWindowMinutes ?? '--'} 分钟
                </Badge>
              </div>
              <div className='mt-3 text-xs text-muted-foreground'>
                渠道 {governance?.channelCode ?? '--'} · 当前
                {governance?.inQuietHours ? '处于静默期' : '不在静默期'}
              </div>
            </div>
            <div className='rounded-xl border bg-muted/20 p-4'>
              <div className='text-xs text-muted-foreground'>最近 7 天执行</div>
              <div className='mt-2 text-2xl font-semibold text-foreground'>
                {automationMetrics?.executedCount ?? snapshot.automationActionCount}
              </div>
              <div className='mt-1 text-xs text-muted-foreground'>
                已执行 {automationMetrics?.executedCount ?? 0} · 跳过 {automationMetrics?.skippedCount ?? 0} · 失败{' '}
                {automationMetrics?.failedCount ?? 0}
              </div>
            </div>
          </div>
          <div className='grid gap-3 md:grid-cols-2'>
            <div className='rounded-xl border bg-muted/20 p-4'>
              <div className='text-xs text-muted-foreground'>通知成功率</div>
              <div className='mt-2 text-2xl font-semibold text-foreground'>
                {formatPercent(automationMetrics?.notificationSuccessRate ?? null)}
              </div>
              <div className='mt-1 text-xs text-muted-foreground'>
                发送 {automationMetrics?.notificationSentCount ?? 0} · 成功 {automationMetrics?.notificationSuccessCount ?? 0}
              </div>
            </div>
            <div className='rounded-xl border bg-muted/20 p-4'>
              <div className='text-xs text-muted-foreground'>动作分布</div>
              <div className='mt-2 space-y-1 text-xs text-muted-foreground'>
                <div>自动催办 {automationMetrics?.autoUrgeExecutedCount ?? 0}</div>
                <div>SLA 提醒 {automationMetrics?.slaReminderExecutedCount ?? 0}</div>
                <div>下一节点预提醒 {automationMetrics?.nextNodePreNotifyExecutedCount ?? 0}</div>
                <div>协同动作 {automationMetrics?.collaborationExecutedCount ?? 0}</div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card className='xl:col-span-1'>
        <CardHeader>
          <CardTitle>风险分布</CardTitle>
          <CardDescription>按高 / 中 / 低风险分布看今天的待办压力。</CardDescription>
        </CardHeader>
        <CardContent className='space-y-3'>
          {[
            { label: '高风险', value: riskDistribution.HIGH ?? 0 },
            { label: '中风险', value: riskDistribution.MEDIUM ?? 0 },
            { label: '低风险', value: riskDistribution.LOW ?? 0 },
          ].map((item) => (
            <div key={item.label} className='space-y-1'>
              <div className='flex items-center justify-between text-sm'>
                <span className='text-muted-foreground'>{item.label}</span>
                <span className='font-semibold text-foreground'>{item.value}</span>
              </div>
              <div className='h-2 overflow-hidden rounded-full bg-muted'>
                <div
                  className='h-full rounded-full bg-slate-900'
                  style={{
                    width: `${Math.max(10, Math.min(100, item.value * 20 + 10))}%`,
                    opacity: item.label === '高风险' ? 0.9 : item.label === '中风险' ? 0.65 : 0.35,
                  }}
                />
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card className='xl:col-span-1'>
        <CardHeader>
          <CardTitle>预计超期趋势</CardTitle>
          <CardDescription>优先关注近 7 天趋势和风险峰值。</CardDescription>
        </CardHeader>
        <CardContent className='space-y-3'>
          {overdueTrend.length ? (
            overdueTrend.map((item) => (
              <div
                key={item.date}
                className='flex items-center justify-between rounded-lg border bg-muted/20 px-3 py-2 text-sm'
              >
                <span className='text-muted-foreground'>{item.date}</span>
                <span className='font-semibold text-foreground'>{item.count}</span>
              </div>
            ))
          ) : (
            <div className='rounded-lg border border-dashed px-3 py-4 text-sm text-muted-foreground'>
              当前没有可用趋势样本。
            </div>
          )}
        </CardContent>
      </Card>

      <Card className='xl:col-span-1'>
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
                  高风险 {item.highRiskCount} · 总量 {item.totalCount} · 中位剩余{' '}
                  {formatMinutes(item.medianRemainingDurationMinutes)}
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

      <Card className='xl:col-span-1'>
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
                  高风险 {item.highRiskCount} / {item.totalCount} · 风险率{' '}
                  {Math.round(item.highRiskRate * 100)}%
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
  )
}
