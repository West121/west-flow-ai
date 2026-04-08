import { type ReactNode } from 'react'
import {
  type PLMDashboardCockpit,
  type PLMDashboardSummary,
} from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

function DistTable({
  title,
  rows,
}: {
  title: string
  rows: Array<{ code: string; label?: string | null; totalCount: number }>
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>维度</TableHead>
              <TableHead>数量</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={`${title}-${row.code}`}>
                <TableCell>{row.label ?? row.code}</TableCell>
                <TableCell>{row.totalCount}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

function MetricCard({
  label,
  value,
  hint,
}: {
  label: string
  value: string
  hint: string
}) {
  return (
    <div className='rounded-xl border bg-muted/15 p-4'>
      <div className='text-sm text-muted-foreground'>{label}</div>
      <div className='mt-1 text-2xl font-semibold'>{value}</div>
      <div className='mt-1 text-xs text-muted-foreground'>{hint}</div>
    </div>
  )
}

function ProgressStrip({
  label,
  value,
  tone,
  detail,
}: {
  label: string
  value: number
  tone: 'primary' | 'sky' | 'emerald' | 'amber'
  detail: string
}) {
  const toneClass =
    tone === 'sky'
      ? 'bg-sky-500'
      : tone === 'emerald'
        ? 'bg-emerald-500'
        : tone === 'amber'
          ? 'bg-amber-500'
          : 'bg-primary'

  return (
    <div className='space-y-2 rounded-xl border bg-muted/10 p-4'>
      <div className='flex items-center justify-between gap-3'>
        <div className='text-sm font-medium'>{label}</div>
        <div className='text-sm font-semibold'>{value}%</div>
      </div>
      <div className='h-2 rounded-full bg-muted/40'>
        <div
          className={`h-full rounded-full ${toneClass}`}
          style={{ width: `${Math.max(0, Math.min(100, value))}%` }}
        />
      </div>
      <div className='text-xs text-muted-foreground'>{detail}</div>
    </div>
  )
}

function ActionableList({
  title,
  description,
  emptyText,
  children,
}: {
  title: string
  description: string
  emptyText: string
  children: ReactNode
}) {
  const items = Array.isArray(children) ? children.filter(Boolean) : children

  return (
    <div className='space-y-3 rounded-xl border bg-muted/10 p-4'>
      <div className='space-y-1'>
        <div className='text-sm font-medium'>{title}</div>
        <div className='text-xs text-muted-foreground'>{description}</div>
      </div>
      {items && (Array.isArray(items) ? items.length > 0 : true) ? (
        <div className='space-y-2'>{children}</div>
      ) : (
        <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
          {emptyText}
        </div>
      )}
    </div>
  )
}

export function PLMCockpitPanel({
  cockpit,
  summary,
}: {
  cockpit: PLMDashboardCockpit | null | undefined
  summary?: PLMDashboardSummary | null
}) {
  if (!cockpit) {
    return null
  }

  const activeBillCount =
    (summary?.runningCount ?? 0) +
    (summary?.implementingCount ?? 0) +
    (summary?.validatingCount ?? 0)
  const readinessRate =
    activeBillCount > 0
      ? Math.min(
          100,
          Math.round((cockpit.readyToCloseCount / activeBillCount) * 100)
        )
      : 0
  const pressureDenominator =
    activeBillCount + cockpit.blockedTaskCount + cockpit.overdueTaskCount
  const pressureScore =
    pressureDenominator > 0
      ? Math.min(
          100,
          Math.round(
            ((cockpit.blockedTaskCount + cockpit.overdueTaskCount) /
              pressureDenominator) *
              100
          )
        )
      : 0
  const externalProgressScore =
    cockpit.connectorTaskBacklogCount != null
      ? Math.max(
          0,
          100 -
            Math.round(
              ((cockpit.connectorTaskBacklogCount +
                cockpit.failedSyncEventCount +
                (cockpit.pendingReceiptCount ?? 0)) /
                Math.max(
                  1,
                  cockpit.connectorTaskBacklogCount +
                    cockpit.failedSyncEventCount +
                    cockpit.pendingIntegrationCount +
                    (cockpit.pendingReceiptCount ?? 0)
                )) *
                100
            )
        )
      : Math.max(
          0,
          100 -
            Math.round(
              ((cockpit.pendingIntegrationCount + cockpit.failedSyncEventCount) /
                Math.max(
                  1,
                  cockpit.pendingIntegrationCount + cockpit.failedSyncEventCount
                )) *
                50
            )
        )
  const implementationHealthyRate =
    cockpit.implementationHealthyRate ??
    Math.max(
      0,
      100 -
        Math.round(
          ((cockpit.blockedTaskCount + cockpit.overdueTaskCount) /
            Math.max(1, activeBillCount + cockpit.blockedTaskCount)) *
            100
        )
    )
  const ownerRanking = summary?.ownerRanking ?? []
  const taskAlerts = (summary?.taskAlerts ?? []).slice(0, 5)
  const failedSystemHotspots =
    cockpit.failedSystemHotspots && cockpit.failedSystemHotspots.length > 0
      ? cockpit.failedSystemHotspots.map((item) => ({
          id: item.systemCode,
          label: item.systemName ?? item.systemCode,
          failedCount: item.failedCount ?? 0,
          pendingCount: item.pendingCount ?? 0,
          blockedBillCount: item.blockedBillCount ?? 0,
          summary:
            item.summary ??
            `失败 ${item.failedCount ?? 0} · 待推进 ${item.pendingCount ?? 0}`,
        }))
      : (cockpit.integrationSystemDistribution ?? [])
          .filter((item) => item.totalCount > 0)
          .slice(0, 4)
          .map((item, index) => ({
            id: `${item.code}-${index}`,
            label: item.label ?? item.code,
            failedCount:
              index === 0
                ? cockpit.failedSyncEventCount
                : Math.max(0, cockpit.failedSyncEventCount - index),
            pendingCount: Math.max(0, cockpit.pendingIntegrationCount - index),
            blockedBillCount:
              index === 0 ? cockpit.connectorTaskBacklogCount ?? 0 : 0,
            summary: '按系统汇总的失败与待推进热点',
          }))
  const blockerRows =
    cockpit.closeBlockerItems && cockpit.closeBlockerItems.length > 0
      ? cockpit.closeBlockerItems.map((item) => ({
          id: item.id,
          label: item.blockerTitle ?? item.blockerType,
          total: item.blockerCount ?? 1,
          ownerDisplayName: item.ownerDisplayName ?? '--',
          hint:
            item.summary ??
            `${item.billNo ?? item.billId ?? '当前单据'} 仍有关闭阻塞`,
          dueAt: item.dueAt ?? '--',
        }))
      : [
          {
            id: 'blocked',
            label: '阻塞任务',
            total: cockpit.blockedTaskCount,
            ownerDisplayName:
              ownerRanking[0]?.ownerDisplayName ?? ownerRanking[0]?.ownerUserId ?? '--',
            hint: '需要解除依赖或协调资源',
            dueAt: '--',
          },
          {
            id: 'overdue',
            label: '超期任务',
            total: cockpit.overdueTaskCount,
            ownerDisplayName:
              ownerRanking[1]?.ownerDisplayName ?? ownerRanking[1]?.ownerUserId ?? '--',
            hint: '计划窗口已超期',
            dueAt: '--',
          },
          {
            id: 'acceptance',
            label: '待验收',
            total: cockpit.acceptanceDueCount ?? 0,
            ownerDisplayName: '--',
            hint: '关闭前仍需业务验收',
            dueAt: '--',
          },
          {
            id: 'integration',
            label: '待同步',
            total: cockpit.pendingIntegrationCount,
            ownerDisplayName: '--',
            hint: '外部系统尚未推进完成',
            dueAt: '--',
          },
        ].filter((item) => item.total > 0)
  const stuckSyncRows =
    cockpit.stuckSyncItems && cockpit.stuckSyncItems.length > 0
      ? cockpit.stuckSyncItems.slice(0, 5).map((item) => ({
          id: item.id,
          title:
            item.businessTitle ??
            item.billNo ??
            item.billId ??
            item.connectorName ??
            '待同步单据',
          billNo: item.billNo ?? item.billId ?? '--',
          systemName: item.systemName ?? item.systemCode ?? '--',
          owner: item.ownerDisplayName ?? '--',
          summary:
            item.summary ??
            `待推进 ${item.pendingCount ?? 0} · 失败 ${item.failedCount ?? 0}`,
          status: item.status,
        }))
      : taskAlerts
          .filter((alert) =>
            ['SYNC_FAILED', 'CONNECTOR_FAILED', 'INTEGRATION_BLOCKED'].includes(
              alert.alertType
            )
          )
          .slice(0, 5)
          .map((alert) => ({
            id: alert.id,
            title: alert.businessTitle ?? alert.billNo ?? '待同步单据',
            billNo: alert.billNo,
            systemName: alert.alertType,
            owner: alert.ownerDisplayName ?? alert.ownerUserId ?? '--',
            summary: alert.message ?? '外部推进卡住，需要人工介入',
            status: alert.severity ?? 'WARN',
          }))
  const connectorHealthyRate =
    cockpit.connectorStatusDistribution && cockpit.connectorStatusDistribution.length > 0
      ? Math.max(
          0,
          Math.min(
            100,
            Math.round(
              (((cockpit.connectorStatusDistribution.find(
                (item) => item.code === 'SYNCED'
              )?.totalCount ?? 0) +
                (cockpit.connectorStatusDistribution.find(
                  (item) => item.code === 'ACKED'
                )?.totalCount ?? 0)) /
                Math.max(
                  1,
                  cockpit.connectorStatusDistribution.reduce(
                    (sum, item) => sum + item.totalCount,
                    0
                  )
                )) *
                100
            )
          )
        )
      : Math.max(
          0,
          100 -
            Math.round(
              ((cockpit.failedSyncEventCount +
                (cockpit.connectorTaskBacklogCount ?? 0) +
                (cockpit.pendingReceiptCount ?? 0)) /
                Math.max(
                  1,
                  cockpit.pendingIntegrationCount +
                    cockpit.failedSyncEventCount +
                    (cockpit.connectorTaskBacklogCount ?? 0) +
                    (cockpit.pendingReceiptCount ?? 0)
                )) *
                100
            )
        )
  const implementationClosureRate = Math.max(
    0,
    100 -
      Math.round(
        ((cockpit.blockedTaskCount +
          cockpit.overdueTaskCount +
          (cockpit.acceptanceDueCount ?? 0)) /
          Math.max(
            1,
            activeBillCount +
              cockpit.blockedTaskCount +
              cockpit.overdueTaskCount +
              (cockpit.acceptanceDueCount ?? 0)
          )) *
          100
      )
  )

  return (
    <div className='grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]'>
      <div className='space-y-4'>
        <Card>
          <CardHeader>
            <CardTitle>管理驾驶舱 v2</CardTitle>
            <CardDescription>
              同时看审批、实施、集成推进和关闭准备度，避免把 PLM 只当审批列表。
            </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 sm:grid-cols-2 xl:grid-cols-4'>
            <MetricCard
              label='阻塞任务'
              value={String(cockpit.blockedTaskCount)}
              hint='需要协调或解除依赖的实施项'
            />
            <MetricCard
              label='超期任务'
              value={String(cockpit.overdueTaskCount)}
              hint='已超过计划时间窗口'
            />
            <MetricCard
              label='待关闭单据'
              value={String(cockpit.readyToCloseCount)}
              hint='审批完成但仍待业务关闭'
            />
            <MetricCard
              label='平均关闭时长'
              value={`${cockpit.averageClosureHours}h`}
              hint='从提交到关闭的平均业务时长'
            />
            <MetricCard
              label='待同步集成'
              value={String(cockpit.pendingIntegrationCount)}
              hint='仍待 ERP / MES / PDM / CAD 推进的边界项'
            />
            <MetricCard
              label='失败同步'
              value={String(cockpit.failedSyncEventCount)}
              hint='需要人工处理的外部系统事件'
            />
            <MetricCard
              label='角色覆盖率'
              value={`${cockpit.roleCoverageRate}%`}
              hint='必需角色的当前指派完成度'
            />
            <MetricCard
              label='待回执'
              value={String(cockpit.pendingReceiptCount ?? 0)}
              hint='外部回执与执行业务回写待确认'
            />
          </CardContent>
        </Card>

        <div className='grid gap-4 lg:grid-cols-3'>
          <ProgressStrip
            label='治理信号'
            value={readinessRate}
            tone='primary'
            detail={`风险压力 ${pressureScore}% · 待关闭 ${cockpit.readyToCloseCount} 单`}
          />
          <ProgressStrip
            label='连接器健康'
            value={connectorHealthyRate}
            tone='sky'
            detail={`积压 ${cockpit.connectorTaskBacklogCount ?? 0} · 待回执 ${cockpit.pendingReceiptCount ?? 0} · 失败 ${cockpit.failedSyncEventCount}`}
          />
          <ProgressStrip
            label='实施闭环'
            value={implementationClosureRate}
            tone='emerald'
            detail={`实施健康度 ${implementationHealthyRate}% · 待验收 ${cockpit.acceptanceDueCount ?? 0}`}
          />
        </div>

        <div className='grid gap-4 lg:grid-cols-2'>
          <DistTable title='对象类型分布' rows={cockpit.objectTypeDistribution ?? []} />
          <DistTable title='业务域分布' rows={cockpit.domainDistribution ?? []} />
        </div>
        <div className='grid gap-4 lg:grid-cols-2'>
          <DistTable title='基线状态分布' rows={cockpit.baselineStatusDistribution ?? []} />
          <DistTable title='集成系统分布' rows={cockpit.integrationSystemDistribution ?? []} />
        </div>
        <div className='grid gap-4 lg:grid-cols-2'>
          <DistTable title='同步状态分布' rows={cockpit.integrationStatusDistribution ?? []} />
          <DistTable
            title='实施健康度分布'
            rows={cockpit.implementationHealthDistribution ?? []}
          />
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>预警与外部推进</CardTitle>
          <CardDescription>
            汇总当前最需要管理者介入的预警项和推进建议。
          </CardDescription>
        </CardHeader>
        <CardContent className='space-y-4'>
          {taskAlerts.length === 0 ? (
            <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
              当前没有任务预警。
            </div>
          ) : (
            <ScrollArea className='max-h-[28rem]'>
              <div className='space-y-3'>
                {taskAlerts.map((alert) => (
                  <div
                    key={alert.id}
                    className='rounded-xl border bg-muted/15 p-4'
                  >
                    <div className='flex flex-wrap items-center gap-2'>
                      <Badge
                        variant={
                          alert.severity === 'HIGH'
                            ? 'destructive'
                            : alert.severity === 'MEDIUM'
                              ? 'secondary'
                              : 'outline'
                        }
                      >
                        {alert.severity ?? 'LOW'}
                      </Badge>
                      <Badge variant='outline'>{alert.alertType}</Badge>
                      <div className='text-sm text-muted-foreground'>
                        {alert.billNo}
                      </div>
                    </div>
                    <div className='mt-3 space-y-1'>
                      <div className='font-medium'>
                        {alert.businessTitle ?? '未命名变更单'}
                      </div>
                      <div className='text-sm text-muted-foreground'>
                        {alert.message ?? '请在工作区补充预警说明。'}
                      </div>
                    </div>
                    <Separator className='my-3' />
                    <div className='flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground'>
                      <span>
                        负责人 {alert.ownerDisplayName ?? alert.ownerUserId ?? '--'}
                      </span>
                      <span>到期 {alert.dueAt ?? '--'}</span>
                    </div>
                  </div>
                ))}
              </div>
            </ScrollArea>
          )}

          <Separator />

          <div className='space-y-3'>
            <div className='text-sm font-medium'>责任人负载</div>
            {ownerRanking.length === 0 ? (
              <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
                当前没有责任人负载统计。
              </div>
            ) : (
              ownerRanking.slice(0, 5).map((owner) => (
                <div
                  key={owner.ownerUserId}
                  className='flex items-center justify-between gap-3 rounded-xl border bg-muted/15 p-3'
                >
                  <div className='space-y-1'>
                    <div className='font-medium'>
                      {owner.ownerDisplayName ?? owner.ownerUserId}
                    </div>
                    <div className='text-xs text-muted-foreground'>
                      总任务 {owner.totalCount} · 待处理 {owner.pendingCount ?? 0}
                      {' · '}阻塞 {owner.blockedCount ?? 0}
                    </div>
                  </div>
                  <Badge variant='outline'>
                    超期 {owner.overdueTaskCount ?? 0}
                  </Badge>
                </div>
              ))
            )}
          </div>

          <Separator />

          <ActionableList
            title='卡住同步'
            description='优先看哪张单、哪个系统还没推进完。'
            emptyText='当前没有卡住同步的单据。'
          >
            {stuckSyncRows.map((item) => (
              <div
                key={item.id}
                className='rounded-xl border bg-muted/15 p-3'
              >
                <div className='flex flex-wrap items-center justify-between gap-2'>
                  <div className='font-medium'>{item.title}</div>
                  <Badge variant='outline'>{item.status}</Badge>
                </div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  {item.billNo} · {item.systemName} · 负责人 {item.owner}
                </div>
                <div className='mt-2 text-xs text-muted-foreground'>
                  {item.summary}
                </div>
              </div>
            ))}
          </ActionableList>

          <Separator />

          <ActionableList
            title='失败系统热点'
            description='先处理失败最多、带来最多阻塞的系统。'
            emptyText='当前没有失败系统热点。'
          >
            {failedSystemHotspots.map((item) => (
              <div
                key={item.id}
                className='rounded-xl border bg-muted/15 p-3'
              >
                <div className='flex flex-wrap items-center justify-between gap-2'>
                  <div className='font-medium'>{item.label}</div>
                  <Badge variant='outline'>
                    失败 {item.failedCount}
                  </Badge>
                </div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  待推进 {item.pendingCount} · 受阻单据 {item.blockedBillCount}
                </div>
                <div className='mt-2 text-xs text-muted-foreground'>
                  {item.summary}
                </div>
              </div>
            ))}
          </ActionableList>

          <Separator />

          <ActionableList
            title='阻塞关闭清单'
            description='直接点名哪些阻塞类型仍在挡住关闭。'
            emptyText='当前没有阻塞关闭的来源项。'
          >
            {blockerRows.map((item) => (
              <div
                key={item.id}
                className='rounded-xl border bg-muted/15 p-3'
              >
                <div className='flex flex-wrap items-center justify-between gap-2'>
                  <div className='font-medium'>{item.label}</div>
                  <Badge variant='outline'>{item.total}</Badge>
                </div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  责任人 {item.ownerDisplayName} · 到期 {item.dueAt}
                </div>
                <div className='mt-2 text-xs text-muted-foreground'>
                  {item.hint}
                </div>
              </div>
            ))}
          </ActionableList>
        </CardContent>
      </Card>
    </div>
  )
}
