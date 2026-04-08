import { type PLMImplementationWorkspace } from '@/lib/api/plm'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

function resolveStatusVariant(status: string) {
  if (status === 'FAILED' || status === 'BLOCKED') {
    return 'destructive' as const
  }
  if (status === 'COMPLETED' || status === 'COLLECTED' || status === 'READY') {
    return 'secondary' as const
  }
  return 'outline' as const
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className='rounded-lg border border-dashed p-4 text-sm text-muted-foreground'>
      {text}
    </div>
  )
}

function HealthRail({
  label,
  value,
  detail,
  tone,
}: {
  label: string
  value: number
  detail: string
  tone: 'primary' | 'amber' | 'emerald'
}) {
  const toneClass =
    tone === 'amber'
      ? 'bg-amber-500'
      : tone === 'emerald'
        ? 'bg-emerald-500'
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

export function PLMImplementationWorkspacePanel({
  workspace,
  onUpdateAcceptance,
  pendingAcceptanceId,
}: {
  workspace: PLMImplementationWorkspace
  onUpdateAcceptance?: (
    checkpoint: PLMImplementationWorkspace['acceptanceCheckpoints'][number],
    payload: {
      status: string
      resultSummary?: string
    }
  ) => void
  pendingAcceptanceId?: string | null
}) {
  const blockingCount = workspace.dependencies.filter(
    (item) => item.blocking
  ).length
  const collectedEvidenceCount = workspace.evidences.filter(
    (item) => item.status === 'COLLECTED'
  ).length
  const readyAcceptanceCount = workspace.acceptanceCheckpoints.filter(
    (item) => item.status === 'READY' || item.status === 'COMPLETED'
  ).length
  const dependencyClearanceRate =
    workspace.dependencies.length > 0
      ? Math.round(
          ((workspace.dependencies.length - blockingCount) /
            workspace.dependencies.length) *
            100
        )
      : 100
  const evidenceCollectionRate =
    workspace.evidences.length > 0
      ? Math.round((collectedEvidenceCount / workspace.evidences.length) * 100)
      : 100
  const acceptanceReadinessRate =
    workspace.acceptanceCheckpoints.length > 0
      ? Math.round(
          (readyAcceptanceCount / workspace.acceptanceCheckpoints.length) * 100
        )
      : 100

  return (
    <Card>
      <CardHeader>
        <CardTitle>实施依赖 / 证据 / 验收</CardTitle>
        <CardDescription>
          用于协调实施依赖、收集验证证据，并判断关闭前的验收准备度。
        </CardDescription>
      </CardHeader>
      <CardContent className='space-y-4'>
        <div className='grid gap-3 sm:grid-cols-3'>
          <div className='rounded-xl border bg-muted/15 p-4'>
            <div className='text-sm text-muted-foreground'>依赖项</div>
            <div className='mt-1 text-2xl font-semibold'>
              {workspace.dependencies.length}
            </div>
            <div className='mt-1 text-xs text-muted-foreground'>
              阻塞项 {blockingCount}
            </div>
          </div>
          <div className='rounded-xl border bg-muted/15 p-4'>
            <div className='text-sm text-muted-foreground'>验证证据</div>
            <div className='mt-1 text-2xl font-semibold'>
              {workspace.evidences.length}
            </div>
            <div className='mt-1 text-xs text-muted-foreground'>
              已收集 {collectedEvidenceCount}
            </div>
          </div>
          <div className='rounded-xl border bg-muted/15 p-4'>
            <div className='text-sm text-muted-foreground'>验收检查点</div>
            <div className='mt-1 text-2xl font-semibold'>
              {workspace.acceptanceCheckpoints.length}
            </div>
            <div className='mt-1 text-xs text-muted-foreground'>
              已就绪 {readyAcceptanceCount}
            </div>
          </div>
        </div>

        <div className='grid gap-3 xl:grid-cols-3'>
          <HealthRail
            label='依赖疏通'
            value={dependencyClearanceRate}
            detail={`阻塞 ${blockingCount} / ${workspace.dependencies.length}`}
            tone='primary'
          />
          <HealthRail
            label='证据收集'
            value={evidenceCollectionRate}
            detail={`已收集 ${collectedEvidenceCount} / ${workspace.evidences.length}`}
            tone='amber'
          />
          <HealthRail
            label='验收就绪'
            value={acceptanceReadinessRate}
            detail={`已就绪 ${readyAcceptanceCount} / ${workspace.acceptanceCheckpoints.length}`}
            tone='emerald'
          />
        </div>

        <Tabs defaultValue='dependencies' className='space-y-4'>
          <TabsList className='grid w-full grid-cols-3'>
            <TabsTrigger value='dependencies'>实施依赖</TabsTrigger>
            <TabsTrigger value='evidences'>验证证据</TabsTrigger>
            <TabsTrigger value='acceptance'>验收检查</TabsTrigger>
          </TabsList>

          <TabsContent value='dependencies'>
            {workspace.dependencies.length === 0 ? (
              <EmptyState text='当前没有实施依赖。' />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>依赖类型</TableHead>
                    <TableHead>上游任务</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>阻塞</TableHead>
                    <TableHead>到期</TableHead>
                    <TableHead>备注</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {workspace.dependencies.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>{item.dependencyType}</TableCell>
                      <TableCell>
                        <div className='space-y-1'>
                          <div className='font-medium'>
                            {item.upstreamTitle ?? '--'}
                          </div>
                          <div className='text-xs text-muted-foreground'>
                            {item.upstreamTaskNo ?? '--'}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant={resolveStatusVariant(item.status)}>
                          {item.status}
                        </Badge>
                      </TableCell>
                      <TableCell>{item.blocking ? '是' : '否'}</TableCell>
                      <TableCell>{item.dueAt ?? '--'}</TableCell>
                      <TableCell className='max-w-64 whitespace-normal'>
                        {item.note ?? '--'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </TabsContent>

          <TabsContent value='evidences'>
            {workspace.evidences.length === 0 ? (
              <EmptyState text='当前没有验证证据。' />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>证据类型</TableHead>
                    <TableHead>标题</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>责任人</TableHead>
                    <TableHead>外部引用</TableHead>
                    <TableHead>摘要</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {workspace.evidences.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>{item.evidenceType}</TableCell>
                      <TableCell className='font-medium'>{item.title}</TableCell>
                      <TableCell>
                        <Badge variant={resolveStatusVariant(item.status)}>
                          {item.status}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {item.ownerDisplayName ?? item.ownerUserId ?? '--'}
                      </TableCell>
                      <TableCell>{item.externalRef ?? '--'}</TableCell>
                      <TableCell className='max-w-64 whitespace-normal'>
                        {item.summary ?? '--'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </TabsContent>

          <TabsContent value='acceptance'>
            {workspace.acceptanceCheckpoints.length === 0 ? (
              <EmptyState text='当前没有验收检查点。' />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>检查点</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>必需</TableHead>
                    <TableHead>责任人</TableHead>
                    <TableHead>完成时间</TableHead>
                    <TableHead>摘要</TableHead>
                    <TableHead className='text-right'>操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {workspace.acceptanceCheckpoints.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <div className='space-y-1'>
                          <div className='font-medium'>{item.checkpointName}</div>
                          <div className='text-xs text-muted-foreground'>
                            {item.checkpointCode}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant={resolveStatusVariant(item.status)}>
                          {item.status}
                        </Badge>
                      </TableCell>
                      <TableCell>{item.required ? '必需' : '可选'}</TableCell>
                      <TableCell>
                        {item.ownerDisplayName ?? item.ownerUserId ?? '--'}
                      </TableCell>
                      <TableCell>{item.completedAt ?? '--'}</TableCell>
                      <TableCell className='max-w-64 whitespace-normal'>
                        {item.summary ?? '--'}
                      </TableCell>
                      <TableCell className='w-[140px]'>
                        <div className='flex justify-end gap-2'>
                          <Button
                            type='button'
                            size='sm'
                            variant='outline'
                            disabled={pendingAcceptanceId != null}
                            onClick={() =>
                              onUpdateAcceptance?.(item, {
                                status: 'READY',
                                resultSummary: item.summary ?? '已恢复为待验收状态。',
                              })
                            }
                          >
                            {pendingAcceptanceId === item.id ? '处理中...' : '回退'}
                          </Button>
                          <Button
                            type='button'
                            size='sm'
                            disabled={pendingAcceptanceId != null}
                            onClick={() =>
                              onUpdateAcceptance?.(item, {
                                status: 'ACCEPTED',
                                resultSummary:
                                  item.summary ??
                                  `${item.checkpointName} 已在 PLM 工作区完成验收确认。`,
                              })
                            }
                          >
                            {pendingAcceptanceId === item.id ? '处理中...' : '完成'}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}
