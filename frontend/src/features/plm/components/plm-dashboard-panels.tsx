import { type ReactNode } from 'react'
import {
  type PLMDashboardOwnerRankingItem,
  type PLMDashboardStageDistributionItem,
  type PLMDashboardSummary,
  type PLMDashboardSummaryMetrics,
  type PLMDashboardTaskAlertItem,
  type PLMDashboardTrendItem,
  type PLMDashboardTypeDistributionItem,
  type PlmBusinessTypeCode,
} from '@/lib/api/plm'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { formatApprovalSheetDateTime } from '@/features/workbench/approval-sheet-list'

const PLM_BUSINESS_LABELS: Record<PlmBusinessTypeCode, string> = {
  PLM_ECR: 'ECR',
  PLM_ECO: 'ECO',
  PLM_MATERIAL: '物料变更',
}

function formatBusinessTypeLabel(value: PlmBusinessTypeCode | string) {
  return PLM_BUSINESS_LABELS[value as PlmBusinessTypeCode] ?? value
}

function formatDashboardValue(value: number | undefined) {
  return typeof value === 'number' ? String(value) : '--'
}

function getDashboardSummary(summary?: PLMDashboardSummary | null) {
  return summary?.summary ?? summary
}

function resolveDashboardMetric(
  summary: PLMDashboardSummary | null | undefined,
  key: keyof PLMDashboardSummaryMetrics
) {
  const nested = getDashboardSummary(summary)
  return (nested?.[key] as number | undefined) ?? 0
}

function DashboardMetricCard({
  label,
  value,
  hint,
}: {
  label: string
  value: string
  hint: string
}) {
  return (
    <Card>
      <CardHeader className='space-y-2'>
        <CardDescription>{label}</CardDescription>
        <CardTitle className='text-3xl'>{value}</CardTitle>
      </CardHeader>
      <CardContent className='text-sm text-muted-foreground'>
        {hint}
      </CardContent>
    </Card>
  )
}

function SectionCard({
  title,
  description,
  children,
}: {
  title: string
  description: string
  children: ReactNode
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

function renderDistributionBar(value: number, max: number) {
  const width = max > 0 ? `${Math.max((value / max) * 100, 6)}%` : '0%'
  return (
    <div className='h-2 rounded-full bg-muted'>
      <div className='h-2 rounded-full bg-primary' style={{ width }} />
    </div>
  )
}

function TypeDistributionTable({
  items,
}: {
  items: PLMDashboardTypeDistributionItem[]
}) {
  const max = Math.max(...items.map((item) => item.totalCount), 0)

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>业务类型</TableHead>
          <TableHead>总量</TableHead>
          <TableHead>草稿</TableHead>
          <TableHead>审批中</TableHead>
          <TableHead>已完成</TableHead>
          <TableHead>分布</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.businessType}>
            <TableCell>{formatBusinessTypeLabel(item.businessType)}</TableCell>
            <TableCell>{item.totalCount}</TableCell>
            <TableCell>{formatDashboardValue(item.draftCount)}</TableCell>
            <TableCell>{formatDashboardValue(item.runningCount)}</TableCell>
            <TableCell>{formatDashboardValue(item.completedCount)}</TableCell>
            <TableCell className='min-w-40'>
              {renderDistributionBar(item.totalCount, max)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function StageDistributionTable({
  items,
}: {
  items: PLMDashboardStageDistributionItem[]
}) {
  const max = Math.max(...items.map((item) => item.totalCount), 0)

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>阶段</TableHead>
          <TableHead>说明</TableHead>
          <TableHead>总量</TableHead>
          <TableHead>占比</TableHead>
          <TableHead>分布</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.stage}>
            <TableCell>{item.stage}</TableCell>
            <TableCell>{item.stageLabel ?? '--'}</TableCell>
            <TableCell>{item.totalCount}</TableCell>
            <TableCell>
              {item.percent != null ? `${item.percent}%` : '--'}
            </TableCell>
            <TableCell className='min-w-40'>
              {renderDistributionBar(item.totalCount, max)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function TrendSeriesTable({ items }: { items: PLMDashboardTrendItem[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>日期</TableHead>
          <TableHead>总量</TableHead>
          <TableHead>草稿</TableHead>
          <TableHead>审批中</TableHead>
          <TableHead>已完成</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.day}>
            <TableCell>{item.day}</TableCell>
            <TableCell>{item.totalCount}</TableCell>
            <TableCell>{formatDashboardValue(item.draftCount)}</TableCell>
            <TableCell>{formatDashboardValue(item.runningCount)}</TableCell>
            <TableCell>{formatDashboardValue(item.completedCount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function TaskAlertsList({ items }: { items: PLMDashboardTaskAlertItem[] }) {
  if (items.length === 0) {
    return (
      <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
        当前没有任务预警。
      </div>
    )
  }

  return (
    <div className='space-y-3'>
      {items.map((item) => (
        <div key={item.id} className='rounded-lg border bg-muted/20 p-4'>
          <div className='flex flex-wrap items-start justify-between gap-3'>
            <div className='space-y-1'>
              <div className='flex flex-wrap items-center gap-2'>
                <span className='font-medium'>
                  {item.businessTitle ?? item.billNo}
                </span>
                <Badge variant='outline'>
                  {formatBusinessTypeLabel(item.businessType)}
                </Badge>
                {item.severity ? (
                  <Badge variant='secondary'>{item.severity}</Badge>
                ) : null}
              </div>
              <p className='text-xs text-muted-foreground'>
                {item.billNo} ·{' '}
                {item.ownerDisplayName ?? item.ownerUserId ?? '未指定负责人'}
              </p>
              {item.message ? (
                <p className='text-sm text-muted-foreground'>{item.message}</p>
              ) : null}
            </div>
            <div className='text-right text-xs text-muted-foreground'>
              <div>{item.alertType}</div>
              <div>
                {item.dueAt ? formatApprovalSheetDateTime(item.dueAt) : '--'}
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

function OwnerRankingTable({
  items,
}: {
  items: PLMDashboardOwnerRankingItem[]
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>负责人</TableHead>
          <TableHead>总量</TableHead>
          <TableHead>待办</TableHead>
          <TableHead>阻塞</TableHead>
          <TableHead>逾期任务</TableHead>
          <TableHead>已完成</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.ownerUserId}>
            <TableCell>{item.ownerDisplayName ?? item.ownerUserId}</TableCell>
            <TableCell>{item.totalCount}</TableCell>
            <TableCell>{formatDashboardValue(item.pendingCount)}</TableCell>
            <TableCell>{formatDashboardValue(item.blockedCount)}</TableCell>
            <TableCell>{formatDashboardValue(item.overdueTaskCount)}</TableCell>
            <TableCell>{formatDashboardValue(item.completedCount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

export function PLMDashboardPanels({
  summary,
  isLoading = false,
}: {
  summary?: PLMDashboardSummary | null
  isLoading?: boolean
}) {
  const typeDistribution = summary?.typeDistribution ?? []
  const stageDistribution = summary?.stageDistribution ?? []
  const trendSeries = summary?.trendSeries ?? []
  const taskAlerts = summary?.taskAlerts ?? []
  const ownerRanking = summary?.ownerRanking ?? []
  const loadingHint = isLoading && !summary ? '正在加载企业级统计大盘。' : null
  const emptyHint = !isLoading && !summary ? '当前暂无 PLM 统计数据。' : null

  return (
    <div className='grid gap-4'>
      <div className='grid gap-4 md:grid-cols-2 xl:grid-cols-3'>
        <DashboardMetricCard
          label='总单据'
          value={String(resolveDashboardMetric(summary, 'totalCount'))}
          hint='当前可见的三类 PLM 业务单总数。'
        />
        <DashboardMetricCard
          label='审批中'
          value={String(resolveDashboardMetric(summary, 'runningCount'))}
          hint='仍处于流程运行中的单据数量。'
        />
        <DashboardMetricCard
          label='已完成'
          value={String(resolveDashboardMetric(summary, 'completedCount'))}
          hint='审批结束且已完成归档的业务单数量。'
        />
        <DashboardMetricCard
          label='草稿'
          value={String(resolveDashboardMetric(summary, 'draftCount'))}
          hint='尚未提交流程、仍可继续编辑的单据数量。'
        />
        <DashboardMetricCard
          label='实施中'
          value={String(resolveDashboardMetric(summary, 'implementingCount'))}
          hint='进入实施阶段、等待任务完成的单据数量。'
        />
        <DashboardMetricCard
          label='验证中'
          value={String(resolveDashboardMetric(summary, 'validatingCount'))}
          hint='进入验证阶段、等待关闭条件满足的单据数量。'
        />
      </div>

      {loadingHint || emptyHint ? (
        <Card>
          <CardContent className='px-6 py-4 text-sm text-muted-foreground'>
            {loadingHint ?? emptyHint}
          </CardContent>
        </Card>
      ) : null}

      <div className='grid gap-4 xl:grid-cols-2'>
        <SectionCard
          title='业务类型分布'
          description='按业务类型查看 PLM 变更单台账分布。'
        >
          {typeDistribution.length === 0 ? (
            <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
              当前没有业务类型分布数据。
            </div>
          ) : (
            <TypeDistributionTable items={typeDistribution} />
          )}
        </SectionCard>

        <SectionCard
          title='生命周期分布'
          description='按草稿、审批、实施和关闭阶段查看单据分布。'
        >
          {stageDistribution.length === 0 ? (
            <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
              当前没有生命周期分布数据。
            </div>
          ) : (
            <StageDistributionTable items={stageDistribution} />
          )}
        </SectionCard>
      </div>

      <div className='grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]'>
        <SectionCard
          title='近 30 天趋势'
          description='按日期查看 PLM 单据的新增与流转趋势。'
        >
          {trendSeries.length === 0 ? (
            <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
              当前没有趋势数据。
            </div>
          ) : (
            <ScrollArea className='max-h-[320px]'>
              <TrendSeriesTable items={trendSeries} />
            </ScrollArea>
          )}
        </SectionCard>

        <SectionCard
          title='负责人排行'
          description='按责任人查看待办、阻塞和完成情况。'
        >
          {ownerRanking.length === 0 ? (
            <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
              当前没有负责人排行数据。
            </div>
          ) : (
            <ScrollArea className='max-h-[320px]'>
              <OwnerRankingTable items={ownerRanking} />
            </ScrollArea>
          )}
        </SectionCard>
      </div>

      <SectionCard
        title='任务预警'
        description='显示超期、阻塞和接近关闭条件的 PLM 实施任务。'
      >
        <TaskAlertsList items={taskAlerts} />
      </SectionCard>
    </div>
  )
}
