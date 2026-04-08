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

function clampPercentage(value: number) {
  return Math.max(0, Math.min(100, Math.round(value)))
}

function toneVariant(tone: 'healthy' | 'warn' | 'risk' | 'neutral') {
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

function SummaryTile({
  label,
  value,
  helper,
}: {
  label: string
  value: string
  helper: string
}) {
  return (
    <div className='rounded-xl border bg-muted/10 p-4'>
      <div className='text-sm text-muted-foreground'>{label}</div>
      <div className='mt-1 text-2xl font-semibold tracking-tight'>{value}</div>
      <div className='mt-1 text-xs text-muted-foreground'>{helper}</div>
    </div>
  )
}

export function PLMExecutionOverviewPanel({
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
  const implementationTasks = workspace.implementationTasks ?? []
  const dependencies = workspace.dependencies ?? []
  const acceptanceCheckpoints = workspace.acceptanceCheckpoints ?? []
  const evidences = workspace.evidences ?? []
  const releasedBaselineCount = baselines.filter((item) => item.status === 'RELEASED').length
  const releasedAssetCount = documentAssets.filter((item) => item.vaultState === 'RELEASED').length
  const publishTotal = baselines.length + documentAssets.length
  const publishReleased = releasedBaselineCount + releasedAssetCount
  const publishCoverage = publishTotal > 0 ? clampPercentage((publishReleased / publishTotal) * 100) : 100

  const pendingIntegrationCount = integrations.filter((item) =>
    ['PENDING', 'RUNNING', 'BLOCKED'].includes(item.status)
  ).length
  const failedIntegrationCount = integrations.filter((item) => item.status === 'FAILED').length
  const failedSyncCount = syncEvents.filter((item) => item.status === 'FAILED').length
  const failedConnectorTaskCount = connectorTasks.filter((item) => item.status === 'FAILED').length
  const pendingConnectorTaskCount = connectorTasks.filter((item) =>
    ['PENDING', 'QUEUED', 'RUNNING', 'DISPATCHED'].includes(item.status)
  ).length
  const blockedDependencyCount = dependencies.filter((item) => item.blocking).length
  const pendingAcceptanceCount = acceptanceCheckpoints.filter(
    (item) => item.required && item.status !== 'ACCEPTED'
  ).length
  const pendingEvidenceCount = evidences.filter(
    (item) => item.status !== 'COLLECTED'
  ).length
  const blockingCount =
    blockedDependencyCount + pendingAcceptanceCount + pendingEvidenceCount + failedSyncCount + failedConnectorTaskCount
  const closeReadiness = clampPercentage(
    100 -
      blockedDependencyCount * 18 -
      pendingAcceptanceCount * 22 -
      pendingEvidenceCount * 12 -
      failedSyncCount * 20 -
      failedConnectorTaskCount * 20 -
      Math.max(0, 80 - publishCoverage) * 0.35
  )

  const nextActions: string[] = []
  if (failedSyncCount > 0 || failedConnectorTaskCount > 0 || failedIntegrationCount > 0) {
    nextActions.push('优先处理失败回执与连接器任务')
  }
  if (publishCoverage < 100) {
    nextActions.push('补齐基线或文档发布，避免外部系统缺对象')
  }
  if (pendingEvidenceCount > 0 || pendingAcceptanceCount > 0) {
    nextActions.push('完成证据与验收检查后再进入关闭')
  }
  if (blockedDependencyCount > 0) {
    nextActions.push('解除实施依赖阻塞，避免下游任务空转')
  }
  if (nextActions.length === 0) {
    nextActions.push('当前可进入关闭确认或等待外部系统回执')
  }

  const overviewTone: 'healthy' | 'warn' | 'risk' =
    blockingCount > 0 || failedSyncCount > 0 || failedConnectorTaskCount > 0
      ? 'risk'
      : pendingIntegrationCount > 0 || publishCoverage < 100
        ? 'warn'
        : 'healthy'

  return (
    <Card>
      <CardHeader>
        <div className='flex flex-wrap items-start justify-between gap-3'>
          <div className='space-y-1'>
            <CardTitle>执行总览</CardTitle>
            <CardDescription>
              先看对象发布、外部推进和关闭准备度，再决定去处理哪一块工作区。
            </CardDescription>
          </div>
          <Badge variant={toneVariant(overviewTone)}>
            {overviewTone === 'healthy' ? '推进稳定' : overviewTone === 'warn' ? '需补齐发布' : '需立即介入'}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='grid gap-3 md:grid-cols-2 xl:grid-cols-4'>
          <SummaryTile
            label='对象发布覆盖'
            value={`${publishCoverage}%`}
            helper={`${publishReleased}/${publishTotal} 个对象已进入受控状态`}
          />
          <SummaryTile
            label='外部推进压力'
            value={String(pendingIntegrationCount + pendingConnectorTaskCount)}
            helper={`失败 ${failedSyncCount + failedConnectorTaskCount}，待推进 ${pendingIntegrationCount}`}
          />
          <SummaryTile
            label='关闭准备度'
            value={`${closeReadiness}%`}
            helper={`阻塞 ${blockingCount} 项，验收待办 ${pendingAcceptanceCount}`}
          />
          <SummaryTile
            label='实施现场'
            value={String(implementationTasks.length)}
            helper={`依赖阻塞 ${blockedDependencyCount}，证据待补 ${pendingEvidenceCount}`}
          />
        </div>

        <div className='grid gap-4 xl:grid-cols-[1.15fr_0.85fr]'>
          <div className='rounded-xl border bg-muted/10 p-4'>
            <div className='text-sm font-medium'>下一步动作</div>
            <div className='mt-3 flex flex-wrap gap-2'>
              {nextActions.map((action) => (
                <Badge key={action} variant='outline' className='rounded-full px-3 py-1'>
                  {action}
                </Badge>
              ))}
            </div>
          </div>

          <div className='rounded-xl border bg-muted/10 p-4'>
            <div className='text-sm font-medium'>风险摘要</div>
            <div className='mt-3 grid gap-2 text-sm text-muted-foreground'>
              <div>失败同步事件 {failedSyncCount} 条</div>
              <div>失败连接器任务 {failedConnectorTaskCount} 条</div>
              <div>未发布基线 {Math.max(0, baselines.length - releasedBaselineCount)} 条</div>
              <div>未受控文档 {Math.max(0, documentAssets.length - releasedAssetCount)} 份</div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
