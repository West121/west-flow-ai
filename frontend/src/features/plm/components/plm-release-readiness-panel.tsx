import {
  type PLMConfigurationBaseline,
  type PLMConnectorTask,
  type PLMDocumentAsset,
  type PLMExternalIntegration,
  type PLMExternalSyncEventEnvelope,
  type PLMImplementationWorkspace,
} from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

function statusBadgeVariant(
  tone: 'healthy' | 'warn' | 'risk' | 'neutral'
) {
  switch (tone) {
    case 'healthy':
      return 'secondary' as const
    case 'risk':
      return 'destructive' as const
    case 'warn':
      return 'outline' as const
    default:
      return 'outline' as const
  }
}

function MetricTile({
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

function EmptyBlock({ text }: { text: string }) {
  return (
    <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
      {text}
    </div>
  )
}

function ProgressRail({
  label,
  value,
  detail,
  tone,
}: {
  label: string
  value: number
  detail: string
  tone: 'primary' | 'sky' | 'emerald'
}) {
  const toneClass =
    tone === 'sky'
      ? 'bg-sky-500'
      : tone === 'emerald'
        ? 'bg-emerald-500'
        : 'bg-primary'

  return (
    <div className='space-y-2 rounded-xl border bg-muted/15 p-4'>
      <div className='flex items-center justify-between gap-2'>
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

export function PLMReleaseReadinessPanel({
  baselines,
  documentAssets,
  integrations,
  syncEvents,
  connectorTasks,
  workspace,
}: {
  baselines: PLMConfigurationBaseline[]
  documentAssets: PLMDocumentAsset[]
  integrations: PLMExternalIntegration[]
  syncEvents: PLMExternalSyncEventEnvelope[]
  connectorTasks: PLMConnectorTask[]
  workspace: PLMImplementationWorkspace
}) {
  const activeBaselines = baselines.filter((item) =>
    ['ACTIVE', 'RELEASED'].includes(item.status)
  ).length
  const releasedAssets = documentAssets.filter(
    (item) => item.vaultState === 'RELEASED'
  ).length
  const pendingIntegrations = integrations.filter((item) =>
    ['PENDING', 'BLOCKED', 'RUNNING'].includes(item.status)
  )
  const failedEvents = syncEvents.filter((item) => item.status === 'FAILED')
  const blockedDependencies = workspace.dependencies.filter(
    (item) => item.blocking
  )
  const pendingAcceptance = workspace.acceptanceCheckpoints.filter(
    (item) => item.required && item.status !== 'ACCEPTED'
  )
  const evidenceBacklog = workspace.evidences.filter(
    (item) => item.status !== 'COLLECTED'
  )
  const pendingReceipts = connectorTasks.flatMap((task) =>
    task.receipts
      .filter((receipt) => receipt.receiptStatus === 'PENDING')
      .map((receipt) => ({
        ...receipt,
        connectorName: task.connectorName,
      }))
  )
  const failedConnectorTasks = connectorTasks.filter(
    (task) => task.status === 'FAILED'
  )
  const releaseCoverage =
    documentAssets.length > 0
      ? Math.round((releasedAssets / documentAssets.length) * 100)
      : 100
  const baselineCoverage =
    baselines.length > 0
      ? Math.round((activeBaselines / baselines.length) * 100)
      : 100
  const blockingCount =
    blockedDependencies.length + pendingAcceptance.length + evidenceBacklog.length
  const connectorBacklogCount = Math.max(
    0,
    connectorTasks.filter((item) =>
      ['PENDING', 'QUEUED', 'RUNNING', 'DISPATCHED'].includes(item.status)
    ).length
  )
  const externalProgress = Math.max(
    0,
    100 -
      Math.round(
        ((pendingIntegrations.length +
          failedEvents.length +
          failedConnectorTasks.length +
          pendingReceipts.length) /
          Math.max(
            1,
            integrations.length +
              failedEvents.length +
              failedConnectorTasks.length +
              pendingReceipts.length
          )) *
          100
      )
  )
  const implementationClosure = Math.max(
    0,
    100 -
      Math.round(
        (blockingCount /
          Math.max(
            1,
            workspace.dependencies.length +
              workspace.evidences.length +
              workspace.acceptanceCheckpoints.length
          )) *
          100
      )
  )
  const syncBacklogRows = [
    ...failedEvents.slice(0, 2).map((event) => ({
      id: event.id,
      title: event.systemName,
      badge: event.eventType,
      tone: 'risk' as const,
      detail: event.errorMessage ?? '外部同步失败，需要人工介入',
    })),
    ...failedConnectorTasks.slice(0, 2).map((task) => ({
      id: task.id,
      title: task.connectorName,
      badge: task.status,
      tone: 'risk' as const,
      detail: `${task.targetSystem} · ${task.payloadSummary ?? '连接器任务失败，等待重试'}`,
    })),
    ...pendingReceipts.slice(0, 2).map((receipt) => ({
      id: receipt.id,
      title: receipt.connectorName,
      badge: '待回执',
      tone: 'warn' as const,
      detail: `${receipt.receiptNo ?? '--'} · 等待外部确认`,
    })),
  ]
  const closeBlockRows = [
    ...blockedDependencies.slice(0, 2).map((item) => ({
      id: item.id,
      title: item.upstreamTitle ?? item.dependencyType,
      badge: '阻塞',
      detail: item.note ?? '需要先解除上游依赖',
    })),
    ...pendingAcceptance.slice(0, 2).map((item) => ({
      id: item.id,
      title: item.checkpointName,
      badge: '待验收',
      detail: `${item.ownerDisplayName ?? item.ownerUserId ?? '未指派'} · ${item.summary ?? '尚未完成验收确认'}`,
    })),
    ...evidenceBacklog.slice(0, 2).map((item) => ({
      id: item.id,
      title: item.title,
      badge: '待补证据',
      detail: `${item.ownerDisplayName ?? item.ownerUserId ?? '未指派'} · ${item.summary ?? '尚未收集到验证证据'}`,
    })),
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>发布 / 外部推进总览</CardTitle>
        <CardDescription>
          把对象发布状态、连接器健康和关闭阻塞收拢到一个工作区里，先看卡住点，再决定进入哪块面板。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='grid gap-3 sm:grid-cols-2 xl:grid-cols-4'>
          <MetricTile
            label='发布基线'
            value={`${activeBaselines}/${baselines.length}`}
            hint='已激活或已发布的配置基线'
          />
          <MetricTile
            label='受控文档'
            value={`${releasedAssets}/${documentAssets.length}`}
            hint={`文档发布覆盖率 ${releaseCoverage}%`}
          />
          <MetricTile
            label='连接器积压'
            value={String(connectorBacklogCount)}
            hint={`待回执 ${pendingReceipts.length} · 失败 ${failedEvents.length + failedConnectorTasks.length}`}
          />
          <MetricTile
            label='关闭阻塞'
            value={String(blockingCount)}
            hint='依赖、验收和证据的剩余阻塞项'
          />
        </div>

        <div className='grid gap-3 xl:grid-cols-3'>
          <ProgressRail
            label='发布覆盖'
            value={Math.round((releaseCoverage + baselineCoverage) / 2)}
            detail={`基线 ${baselineCoverage}% · 文档 ${releaseCoverage}%`}
            tone='primary'
          />
          <ProgressRail
            label='连接器健康'
            value={externalProgress}
            detail={`待推进 ${pendingIntegrations.length} · 失败 ${failedEvents.length + failedConnectorTasks.length} · 待回执 ${pendingReceipts.length}`}
            tone='sky'
          />
          <ProgressRail
            label='实施闭环'
            value={implementationClosure}
            detail={`阻塞 ${blockedDependencies.length} · 待验收 ${pendingAcceptance.length} · 待证据 ${evidenceBacklog.length}`}
            tone='emerald'
          />
        </div>

        <div className='grid gap-4 xl:grid-cols-3'>
          <div className='space-y-3 rounded-xl border bg-muted/10 p-4'>
            <div className='flex items-center justify-between gap-2'>
              <div className='font-medium'>发布状态</div>
              <Badge
                variant={statusBadgeVariant(
                  releaseCoverage >= 80 && baselineCoverage >= 80
                    ? 'healthy'
                    : releaseCoverage > 0 || baselineCoverage > 0
                      ? 'warn'
                      : 'neutral'
                )}
              >
                {releaseCoverage >= 80 && baselineCoverage >= 80
                  ? '发布就绪'
                  : '待补齐'}
              </Badge>
            </div>
            {baselines.length === 0 && documentAssets.length === 0 ? (
              <EmptyBlock text='当前没有可评估的基线或文档资产。' />
            ) : (
              <div className='space-y-2 text-sm'>
                {baselines.slice(0, 2).map((baseline) => (
                  <div
                    key={baseline.id}
                    className='rounded-lg border bg-background/70 p-3'
                  >
                    <div className='flex flex-wrap items-center gap-2'>
                      <span className='font-medium'>{baseline.baselineName}</span>
                      <Badge variant='outline'>{baseline.status}</Badge>
                    </div>
                    <div className='mt-1 text-xs text-muted-foreground'>
                      {baseline.baselineCode} · 覆盖 {baseline.items.length} 个对象
                    </div>
                  </div>
                ))}
                {documentAssets.slice(0, 2).map((asset) => (
                  <div
                    key={asset.id}
                    className='rounded-lg border bg-background/70 p-3'
                  >
                    <div className='flex flex-wrap items-center gap-2'>
                      <span className='font-medium'>{asset.documentName}</span>
                      <Badge
                        variant={statusBadgeVariant(
                          asset.vaultState === 'RELEASED' ? 'healthy' : 'warn'
                        )}
                      >
                        {asset.vaultState}
                      </Badge>
                    </div>
                    <div className='mt-1 text-xs text-muted-foreground'>
                      {asset.documentCode} · 版本 {asset.versionLabel ?? '--'}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className='space-y-3 rounded-xl border bg-muted/10 p-4'>
            <div className='flex items-center justify-between gap-2'>
              <div className='font-medium'>卡住同步</div>
              <Badge
                variant={statusBadgeVariant(
                  failedEvents.length > 0 || failedConnectorTasks.length > 0
                    ? 'risk'
                    : pendingIntegrations.length > 0 || pendingReceipts.length > 0
                      ? 'warn'
                      : 'healthy'
                )}
              >
                {failedEvents.length > 0 || failedConnectorTasks.length > 0
                  ? '需介入'
                  : pendingIntegrations.length > 0 || pendingReceipts.length > 0
                    ? '推进中'
                    : '已收敛'}
              </Badge>
            </div>
            {syncBacklogRows.length === 0 && pendingIntegrations.length === 0 ? (
              <EmptyBlock text='当前没有外部推进阻塞。' />
            ) : (
              <div className='space-y-2 text-sm'>
                {syncBacklogRows.map((item) => (
                  <div
                    key={item.id}
                    className='rounded-lg border bg-background/70 p-3'
                  >
                    <div className='flex flex-wrap items-center gap-2'>
                      <span className='font-medium'>{item.title}</span>
                      <Badge
                        variant={statusBadgeVariant(
                          item.tone === 'risk' ? 'risk' : 'warn'
                        )}
                      >
                        {item.badge}
                      </Badge>
                    </div>
                    <div className='mt-1 text-xs text-muted-foreground'>
                      {item.detail}
                    </div>
                  </div>
                ))}
                {pendingIntegrations.slice(0, 1).map((integration) => (
                  <div
                    key={integration.id}
                    className='rounded-lg border bg-background/70 p-3'
                  >
                    <div className='flex flex-wrap items-center gap-2'>
                      <span className='font-medium'>{integration.systemName}</span>
                      <Badge variant='outline'>{integration.status}</Badge>
                    </div>
                    <div className='mt-1 text-xs text-muted-foreground'>
                      {integration.endpointKey ?? '--'} ·{' '}
                      {integration.message ?? '外部系统仍在处理中'}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className='space-y-3 rounded-xl border bg-muted/10 p-4'>
            <div className='flex items-center justify-between gap-2'>
              <div className='font-medium'>阻塞关闭清单</div>
              <Badge
                variant={statusBadgeVariant(blockingCount > 0 ? 'risk' : 'healthy')}
              >
                {blockingCount > 0 ? '未就绪' : '可关闭'}
              </Badge>
            </div>
            {closeBlockRows.length === 0 ? (
              <EmptyBlock text='依赖、证据和验收都已满足关闭条件。' />
            ) : (
              <div className='space-y-2 text-sm'>
                {closeBlockRows.map((item) => (
                  <div
                    key={item.id}
                    className='rounded-lg border bg-background/70 p-3'
                  >
                    <div className='flex flex-wrap items-center gap-2'>
                      <span className='font-medium'>{item.title}</span>
                      <Badge
                        variant={
                          item.badge === '阻塞' ? 'destructive' : 'outline'
                        }
                      >
                        {item.badge}
                      </Badge>
                    </div>
                    <div className='mt-1 text-xs text-muted-foreground'>
                      {item.detail}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
