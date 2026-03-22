import { type ReactNode } from 'react'
import { getRouteApi, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { type ColumnDef } from '@tanstack/react-table'
import { AlertCircle, ArrowLeft, Box, Cpu, Gauge, Loader2 } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { PageShell } from '@/features/shared/page-shell'
import { ResourceListPage } from '@/features/shared/crud/resource-list-page'
import {
  getNotificationChannelHealthDetail,
  getOrchestratorScanDetail,
  getTriggerExecutionDetail,
  listNotificationChannelHealths,
  listOrchestratorScans,
  listTriggerExecutions,
  type NotificationChannelHealthDetail,
  type NotificationChannelHealthListRecord,
  type OrchestratorScanDetail,
  type OrchestratorScanListRecord,
  type TriggerExecutionDetail,
  type TriggerExecutionListRecord,
} from '@/lib/api/system-monitor'
import { type ListQuerySearch as BaseListQuerySearch } from '@/features/shared/table/query-contract'

const orchestratorScanListRoute =
  getRouteApi('/_authenticated/system/monitor/orchestrator-scans/list')
const orchestratorScanDetailRoute =
  getRouteApi('/_authenticated/system/monitor/orchestrator-scans/$executionId/')
const triggerExecutionListRoute =
  getRouteApi('/_authenticated/system/monitor/trigger-executions/list')
const triggerExecutionDetailRoute =
  getRouteApi('/_authenticated/system/monitor/trigger-executions/$executionId/')
const notificationChannelHealthListRoute =
  getRouteApi('/_authenticated/system/monitor/notification-channels/health/list')
const notificationChannelHealthDetailRoute =
  getRouteApi('/_authenticated/system/monitor/notification-channels/health/$channelId/')

type ListQuerySearch = BaseListQuerySearch

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

function resolveMonitorStatusVariant(status: string) {
  const normalized = status.toUpperCase()
  if (['SUCCESS', 'DONE', 'ACTIVE', 'UP', 'HEALTHY'].includes(normalized)) {
    return 'secondary'
  }
  if (normalized === 'RUNNING') {
    return 'outline'
  }
  return 'destructive'
}

function resolveBooleanText(value: boolean | null | undefined) {
  if (value === null || value === undefined) {
    return '未设置'
  }

  return value ? '是' : '否'
}

function buildEmptyMonitorPage<T>(search: ListQuerySearch) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [] as T[],
    groups: [],
  }
}

const orchestratorScanColumns: ColumnDef<OrchestratorScanListRecord>[] = [
  {
    accessorKey: 'runId',
    header: '运行 ID',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.runId}</span>
        <span className='text-xs text-muted-foreground'>{row.original.executionId}</span>
      </div>
    ),
  },
  {
    accessorKey: 'automationType',
    header: '自动化类型',
  },
  {
    accessorKey: 'targetName',
    header: '目标名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span>{row.original.targetName}</span>
        <span className='text-xs text-muted-foreground'>{row.original.targetId}</span>
      </div>
    ),
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveMonitorStatusVariant(row.original.status)}>
        {row.original.status}
      </Badge>
    ),
  },
  {
    accessorKey: 'executedAt',
    header: '执行时间',
    cell: ({ row }) => formatDateTime(row.original.executedAt),
  },
  {
    accessorKey: 'scannedAt',
    header: '扫描时间',
    cell: ({ row }) => formatDateTime(row.original.scannedAt),
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <Button asChild variant='ghost' className='h-8 px-2'>
        <Link
          to='/system/monitor/orchestrator-scans/$executionId'
          params={{ executionId: row.original.executionId }}
        >
          详情
        </Link>
      </Button>
    ),
  },
]

const triggerExecutionColumns: ColumnDef<TriggerExecutionListRecord>[] = [
  {
    accessorKey: 'triggerName',
    header: '触发器名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.triggerName || '--'}</span>
        <span className='text-xs text-muted-foreground'>{row.original.triggerId}</span>
      </div>
    ),
  },
  { accessorKey: 'triggerEvent', header: '触发事件' },
  {
    accessorKey: 'action',
    header: '动作',
  },
  {
    accessorKey: 'enabled',
    header: '启用',
    cell: ({ row }) => resolveBooleanText(row.original.enabled),
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveMonitorStatusVariant(row.original.status)}>
        {row.original.status}
      </Badge>
    ),
  },
  {
    accessorKey: 'executedAt',
    header: '执行时间',
    cell: ({ row }) => formatDateTime(row.original.executedAt),
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <Button asChild variant='ghost' className='h-8 px-2'>
        <Link
          to='/system/monitor/trigger-executions/$executionId'
          params={{ executionId: row.original.executionId }}
        >
          详情
        </Link>
      </Button>
    ),
  },
]

const notificationChannelHealthColumns: ColumnDef<NotificationChannelHealthListRecord>[] = [
  {
    accessorKey: 'channelName',
    header: '渠道名称',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.channelName}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.channelCode}
        </span>
      </div>
    ),
  },
  { accessorKey: 'channelType', header: '渠道类型' },
  {
    accessorKey: 'status',
    header: '通道状态',
    cell: ({ row }) => (
      <Badge variant={resolveMonitorStatusVariant(row.original.status)}>
        {row.original.status}
      </Badge>
    ),
  },
  {
    accessorKey: 'latestStatus',
    header: '最新回执',
    cell: ({ row }) => (
      <Badge variant={resolveMonitorStatusVariant(row.original.latestStatus)}>
        {row.original.latestStatus}
      </Badge>
    ),
  },
  { accessorKey: 'successRate', header: '成功率 (%)' },
  {
    accessorKey: 'totalAttempts',
    header: '总尝试',
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <Button asChild variant='ghost' className='h-8 px-2'>
        <Link
          to='/system/monitor/notification-channels/health/$channelId'
          params={{ channelId: row.original.channelId }}
        >
          详情
        </Link>
      </Button>
    ),
  },
]

function MonitorPageErrorState({
  title,
  description,
  retry,
  listHref,
}: {
  title: string
  description: string
  retry?: () => void
  listHref: string
}) {
  return (
    <PageShell title={title} description={description}>
      <Alert variant='destructive'>
        <AlertCircle />
        <AlertTitle>页面加载失败</AlertTitle>
        <AlertDescription>监控数据请求未成功，请重试或返回列表页。</AlertDescription>
      </Alert>
      <div className='flex flex-wrap gap-2'>
        {retry ? <Button onClick={retry}>重新加载</Button> : null}
        <Button asChild variant='outline'>
          <Link to={listHref}>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      </div>
    </PageShell>
  )
}

function MonitorPageLoadingState({
  title,
  description,
}: {
  title: string
  description: string
}) {
  return (
    <PageShell title={title} description={description}>
      <Card>
        <CardHeader>
          <Skeleton className='h-6 w-40' />
          <Skeleton className='h-4 w-full max-w-xl' />
        </CardHeader>
        <CardContent className='grid gap-4 md:grid-cols-2'>
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className='space-y-2 rounded-lg border p-4'>
              <Skeleton className='h-4 w-24' />
              <Skeleton className='h-5 w-full' />
              <Skeleton className='h-3 w-3/4' />
            </div>
          ))}
        </CardContent>
      </Card>
    </PageShell>
  )
}

function InfoCell({
  label,
  value,
}: {
  label: string
  value: ReactNode
}) {
  return (
    <div className='rounded-lg border p-4'>
      <p className='text-sm text-muted-foreground'>{label}</p>
      <div className='mt-2 text-sm font-medium'>{value}</div>
    </div>
  )
}

export function SystemMonitorOrchestratorScanListPage() {
  const search = orchestratorScanListRoute.useSearch()
  const navigate = orchestratorScanListRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-monitor-orchestrator-scans', search],
    queryFn: () => listOrchestratorScans(search),
  })
  const data = query.data ?? buildEmptyMonitorPage<OrchestratorScanListRecord>(search)

  return (
    <ResourceListPage
      title='编排扫描监控'
      description='监控自动化编排扫描任务的执行结果和耗时。'
      endpoint='/api/v1/system/monitor/orchestrator-scans/page'
      searchPlaceholder='搜索运行 ID、目标 ID 或目标名称'
      search={search}
      navigate={navigate}
      columns={orchestratorScanColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '扫描任务', value: String(data.total), hint: '按目标和执行 ID 可快速定位。' },
        { label: '当前分页', value: String(search.page), hint: '支持全局关键字与字段筛选。' },
        { label: '最新状态', value: 'RUNNING/SUCCESS/FAILED', hint: '运行态用于识别阻塞任务。' },
      ]}
    />
  )
}

export function SystemMonitorOrchestratorScanDetailPage() {
  const { executionId } = orchestratorScanDetailRoute.useParams()
  const query = useQuery({
    queryKey: ['system-monitor-orchestrator-detail', executionId],
    queryFn: () => getOrchestratorScanDetail(executionId),
  })

  if (query.isLoading) {
    return (
      <MonitorPageLoadingState
        title='编排扫描详情'
        description='查看单条编排扫描任务的执行摘要和运行时间。'
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <MonitorPageErrorState
        title='编排扫描详情'
        description='查看单条编排扫描任务的执行摘要和运行时间。'
        retry={query.refetch}
        listHref='/system/monitor/orchestrator-scans/list'
      />
    )
  }

  const detail: OrchestratorScanDetail = query.data

  return (
    <PageShell
      title='编排扫描详情'
      description='从运行号到扫描输出结果的执行链路。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/monitor/orchestrator-scans/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle className='flex items-center gap-2'>
              <Cpu data-icon='inline-start' className='text-primary' />
              执行基础信息
            </CardTitle>
            <CardDescription>查看执行号、运行状态与执行时间。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 md:grid-cols-2'>
            <InfoCell label='执行 ID' value={detail.executionId} />
            <InfoCell label='运行 ID' value={detail.runId} />
            <InfoCell label='目标 ID' value={detail.targetId} />
            <InfoCell label='目标名称' value={detail.targetName} />
            <InfoCell label='自动化类型' value={detail.automationType} />
            <InfoCell
              label='状态'
              value={
                <Badge variant={resolveMonitorStatusVariant(detail.status)}>
                  {detail.status}
                </Badge>
              }
            />
            <InfoCell label='执行时间' value={formatDateTime(detail.executedAt)} />
            <InfoCell label='扫描时间' value={formatDateTime(detail.scannedAt)} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className='flex items-center gap-2'>
              <Loader2 data-icon='inline-start' className='text-muted-foreground' />
              扫描信息
            </CardTitle>
            <CardDescription>执行输出 message 用于快速排障。</CardDescription>
          </CardHeader>
          <CardContent>
            <InfoCell
              label='执行信息'
              value={<pre className='whitespace-pre-wrap text-xs'>{detail.message}</pre>}
            />
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

export function SystemMonitorTriggerExecutionListPage() {
  const search = triggerExecutionListRoute.useSearch()
  const navigate = triggerExecutionListRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-monitor-trigger-executions', search],
    queryFn: () => listTriggerExecutions(search),
  })
  const data = query.data ?? buildEmptyMonitorPage<TriggerExecutionListRecord>(search)

  return (
    <ResourceListPage
      title='触发执行监控'
      description='监控触发器执行请求、动作与渠道调用链。'
      endpoint='/api/v1/system/monitor/trigger-executions/page'
      searchPlaceholder='搜索触发器名称、事件、动作或执行 ID'
      search={search}
      navigate={navigate}
      columns={triggerExecutionColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '执行记录', value: String(data.total), hint: '聚合查看触发执行结果。' },
        { label: '当前分页', value: String(search.page), hint: '筛选条件可结合执行 ID 使用。' },
        { label: '启用筛选', value: '默认全部', hint: '页面支持布尔状态过滤。' },
      ]}
    />
  )
}

export function SystemMonitorTriggerExecutionDetailPage() {
  const { executionId } = triggerExecutionDetailRoute.useParams()
  const query = useQuery({
    queryKey: ['system-monitor-trigger-detail', executionId],
    queryFn: () => getTriggerExecutionDetail(executionId),
  })

  if (query.isLoading) {
    return (
      <MonitorPageLoadingState
        title='触发执行详情'
        description='查看触发执行明细、动作与条件表达式。'
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <MonitorPageErrorState
        title='触发执行详情'
        description='查看触发执行明细、动作与条件表达式。'
        retry={query.refetch}
        listHref='/system/monitor/trigger-executions/list'
      />
    )
  }

  const detail: TriggerExecutionDetail = query.data

  return (
    <PageShell
      title='触发执行详情'
      description='跟踪动作、条件和参与渠道的执行快照。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/monitor/trigger-executions/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>执行基础信息</CardTitle>
            <CardDescription>执行身份、触发事件与链路状态。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 md:grid-cols-2'>
            <InfoCell label='执行 ID' value={detail.executionId} />
            <InfoCell label='触发器 ID' value={detail.triggerId} />
            <InfoCell label='触发器名称' value={detail.triggerName} />
            <InfoCell label='触发键' value={detail.triggerKey} />
            <InfoCell label='触发事件' value={detail.triggerEvent} />
            <InfoCell label='动作' value={detail.action} />
            <InfoCell label='启用' value={resolveBooleanText(detail.enabled)} />
            <InfoCell
              label='执行状态'
              value={
                <Badge variant={resolveMonitorStatusVariant(detail.status)}>
                  {detail.status}
                </Badge>
              }
            />
            <InfoCell label='操作者' value={detail.operatorUserId || '系统触发'} />
            <InfoCell label='执行时间' value={formatDateTime(detail.executedAt)} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>说明与上下文</CardTitle>
            <CardDescription>便于排查执行是否命中条件。</CardDescription>
          </CardHeader>
          <CardContent className='space-y-3'>
            <InfoCell
              label='描述'
              value={<pre className='whitespace-pre-wrap text-xs'>{detail.description || '无'}</pre>}
            />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>触发上下文</CardTitle>
          <CardDescription>渠道与条件表达式信息。</CardDescription>
        </CardHeader>
        <CardContent className='grid gap-3'>
          <InfoCell
            label='关联渠道'
            value={detail.channelIds.length ? detail.channelIds.join('，') : '无'}
          />
          <InfoCell
            label='条件表达式'
            value={
              <pre className='whitespace-pre-wrap rounded-lg bg-muted p-4 text-xs'>
                {detail.conditionExpression || '--'}
              </pre>
            }
          />
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function SystemMonitorNotificationChannelHealthListPage() {
  const search = notificationChannelHealthListRoute.useSearch()
  const navigate = notificationChannelHealthListRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-monitor-channel-health-list', search],
    queryFn: () => listNotificationChannelHealths(search),
  })
  const data = query.data ?? buildEmptyMonitorPage<NotificationChannelHealthListRecord>(search)

  return (
    <ResourceListPage
      title='通知渠道健康监控'
      description='追踪通知渠道的成功率、错误率与最近回执状态。'
      endpoint='/api/v1/system/monitor/notification-channels/health/page'
      searchPlaceholder='搜索渠道名称、编码或类型'
      search={search}
      navigate={navigate}
      columns={notificationChannelHealthColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '监控渠道', value: String(data.total), hint: '渠道级指标用于快速判断系统可用性。' },
        { label: '当前分页', value: String(search.page), hint: '分页与筛选均走统一契约。' },
        { label: '成功率指标', value: '% 越高越好', hint: '可用于告警阈值对比。' },
      ]}
    />
  )
}

export function SystemMonitorNotificationChannelHealthDetailPage() {
  const { channelId } = notificationChannelHealthDetailRoute.useParams()
  const query = useQuery({
    queryKey: ['system-monitor-channel-health-detail', channelId],
    queryFn: () => getNotificationChannelHealthDetail(channelId),
  })

  if (query.isLoading) {
    return (
      <MonitorPageLoadingState
        title='渠道健康详情'
        description='查看渠道健康指标、最近回执和端点配置。'
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <MonitorPageErrorState
        title='渠道健康详情'
        description='查看渠道健康指标、最近回执和端点配置。'
        retry={query.refetch}
        listHref='/system/monitor/notification-channels/health/list'
      />
    )
  }

  const detail: NotificationChannelHealthDetail = query.data

  return (
    <PageShell
      title='渠道健康详情'
      description='聚焦单个通知渠道的可用性与历史回执。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/monitor/notification-channels/health/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle className='flex items-center gap-2'>
              <Gauge data-icon='inline-start' className='text-primary' />
              通道基础信息
            </CardTitle>
            <CardDescription>通道状态、编码、类型与开关。 </CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 md:grid-cols-2'>
            <InfoCell label='渠道 ID' value={detail.channelId} />
            <InfoCell label='渠道编码' value={detail.channelCode} />
            <InfoCell label='渠道名称' value={detail.channelName} />
            <InfoCell label='渠道类型' value={detail.channelType} />
            <InfoCell label='通道状态' value={detail.status} />
            <InfoCell label='是否启用' value={resolveBooleanText(detail.enabled)} />
            <InfoCell
              label='最新回执状态'
              value={
                <Badge variant={resolveMonitorStatusVariant(detail.latestStatus)}>
                  {detail.latestStatus}
                </Badge>
              }
            />
            <InfoCell label='最近发送时间' value={formatDateTime(detail.lastSentAt)} />
            <InfoCell label='创建时间' value={formatDateTime(detail.createdAt)} />
            <InfoCell label='更新时间' value={formatDateTime(detail.updatedAt)} />
            <InfoCell label='端点地址' value={detail.channelEndpoint || '未配置'} />
            <InfoCell
              label='最新回执'
              value={
                <pre className='whitespace-pre-wrap text-xs'>{detail.latestResponseMessage || '无'}</pre>
              }
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className='flex items-center gap-2'>
              <Box data-icon='inline-start' className='text-muted-foreground' />
              指标摘要
            </CardTitle>
            <CardDescription>用于评估渠道稳定性。</CardDescription>
          </CardHeader>
          <CardContent className='space-y-3'>
            <InfoCell label='总尝试' value={String(detail.totalAttempts)} />
            <InfoCell label='成功次数' value={String(detail.successAttempts)} />
            <InfoCell label='失败次数' value={String(detail.failedAttempts)} />
            <InfoCell label='成功率' value={`${detail.successRate}%`} />
            <InfoCell label='启用状态' value={resolveBooleanText(detail.enabled)} />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>备注</CardTitle>
          <CardDescription>渠道运维备注信息。</CardDescription>
        </CardHeader>
        <CardContent>
          <pre className='whitespace-pre-wrap rounded-lg border p-4 text-sm'>
            {detail.remark || '暂无备注'}
          </pre>
        </CardContent>
      </Card>
    </PageShell>
  )
}
