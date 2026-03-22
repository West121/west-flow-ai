import { type ReactNode } from 'react'
import { getRouteApi, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { type ColumnDef } from '@tanstack/react-table'
import {
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  Clock,
  MailCheck,
} from 'lucide-react'
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
  getAuditLogDetail,
  getLoginLogDetail,
  getNotificationLogDetail,
  listAuditLogs,
  listLoginLogs,
  listNotificationLogs,
  type AuditLogDetail,
  type AuditLogListRecord,
  type LoginLogDetail,
  type LoginLogListRecord,
  type NotificationLogDetail,
  type NotificationLogListRecord,
} from '@/lib/api/system-logs'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'

const auditLogListRoute = getRouteApi('/_authenticated/system/logs/audit/list')
const auditLogDetailRoute = getRouteApi('/_authenticated/system/logs/audit/$logId/')
const loginLogListRoute = getRouteApi('/_authenticated/system/logs/login/list')
const loginLogDetailRoute = getRouteApi('/_authenticated/system/logs/login/$logId/')
const notificationLogListRoute =
  getRouteApi('/_authenticated/system/logs/notifications/list')
const notificationLogDetailRoute =
  getRouteApi('/_authenticated/system/logs/notifications/$recordId/')

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
    hour12: false,
  }).format(date)
}

function resolveLogStatusVariant(status: string) {
  const normalized = status.toUpperCase()

  if (normalized === 'SUCCESS' || normalized === 'ENABLED') {
    return 'secondary'
  }

  return 'destructive'
}

function buildEmptyLogPage<T>(search: ListQuerySearch) {
  return {
    page: search.page,
    pageSize: search.pageSize,
    total: 0,
    pages: 0,
    records: [] as T[],
    groups: [],
  }
}

const auditLogColumns: ColumnDef<AuditLogListRecord>[] = [
  {
    accessorKey: 'requestId',
    header: '请求流水',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.requestId || '--'}</span>
        <span className='text-xs text-muted-foreground'>{row.original.logId}</span>
      </div>
    ),
  },
  { accessorKey: 'module', header: '模块' },
  { accessorKey: 'method', header: '方法' },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveLogStatusVariant(row.original.status)}>
        {row.original.status}
      </Badge>
    ),
  },
  {
    accessorKey: 'statusCode',
    header: '状态码',
    cell: ({ row }) => (
      <Badge variant='outline'>{row.original.statusCode}</Badge>
    ),
  },
  {
    accessorKey: 'createdAt',
    header: '时间',
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <Button asChild variant='ghost' className='h-8 px-2'>
        <Link
          to='/system/logs/audit/$logId'
          params={{ logId: row.original.logId }}
        >
          详情
        </Link>
      </Button>
    ),
  },
]

const loginLogColumns: ColumnDef<LoginLogListRecord>[] = [
  {
    accessorKey: 'username',
    header: '用户名',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.username}</span>
        <span className='text-xs text-muted-foreground'>UID {row.original.userId}</span>
      </div>
    ),
  },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveLogStatusVariant(row.original.status)}>
        {row.original.status}
      </Badge>
    ),
  },
  {
    accessorKey: 'statusCode',
    header: '状态码',
    cell: ({ row }) => <Badge variant='outline'>{row.original.statusCode}</Badge>,
  },
  { accessorKey: 'clientIp', header: '客户端 IP' },
  {
    accessorKey: 'createdAt',
    header: '时间',
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <Button asChild variant='ghost' className='h-8 px-2'>
        <Link
          to='/system/logs/login/$logId'
          params={{ logId: row.original.logId }}
        >
          详情
        </Link>
      </Button>
    ),
  },
]

const notificationLogColumns: ColumnDef<NotificationLogListRecord>[] = [
  {
    accessorKey: 'recipient',
    header: '接收人',
    cell: ({ row }) => (
      <div className='flex flex-col gap-1'>
        <span className='font-medium'>{row.original.recipient || '--'}</span>
        <span className='text-xs text-muted-foreground'>
          {row.original.channelName}
        </span>
      </div>
    ),
  },
  { accessorKey: 'channelCode', header: '渠道编码' },
  { accessorKey: 'title', header: '标题' },
  {
    accessorKey: 'status',
    header: '状态',
    cell: ({ row }) => (
      <Badge variant={resolveLogStatusVariant(row.original.status)}>
        {row.original.status}
      </Badge>
    ),
  },
  {
    accessorKey: 'sentAt',
    header: '发送时间',
    cell: ({ row }) => formatDateTime(row.original.sentAt),
  },
  {
    id: 'action',
    header: '操作',
    enableSorting: false,
    cell: ({ row }) => (
      <Button asChild variant='ghost' className='h-8 px-2'>
        <Link
          to='/system/logs/notifications/$recordId'
          params={{ recordId: row.original.recordId }}
        >
          详情
        </Link>
      </Button>
    ),
  },
]

function LogPageErrorState({
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
        <AlertDescription>日志数据请求未成功，请重试或返回列表页。</AlertDescription>
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

function LogPageLoadingState({
  title,
  description,
  hasSidebar = false,
}: {
  title: string
  description: string
  hasSidebar?: boolean
}) {
  return (
    <PageShell title={title} description={description}>
      <div
        className={
          hasSidebar
            ? 'grid gap-4 xl:grid-cols-[minmax(0,2fr)_380px]'
            : 'space-y-4'
        }
      >
        <Card>
          <CardHeader>
            <Skeleton className='h-6 w-40' />
            <Skeleton className='h-4 w-full max-w-xl' />
          </CardHeader>
          <CardContent className='grid gap-4 md:grid-cols-2'>
            {Array.from({ length: hasSidebar ? 5 : 4 }).map((_, index) => (
              <div key={index} className='space-y-2 rounded-lg border p-4'>
                <Skeleton className='h-4 w-24' />
                <Skeleton className='h-5 w-full' />
                <Skeleton className='h-3 w-3/4' />
              </div>
            ))}
          </CardContent>
        </Card>
        {hasSidebar ? (
          <Card>
            <CardHeader>
              <Skeleton className='h-6 w-32' />
            </CardHeader>
            <CardContent className='space-y-3'>
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={index} className='h-10 w-full' />
              ))}
            </CardContent>
          </Card>
        ) : null}
      </div>
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

export function SystemAuditLogListPage() {
  const search = auditLogListRoute.useSearch()
  const navigate = auditLogListRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-log-audit-list', search],
    queryFn: () => listAuditLogs(search),
  })
  const data = query.data ?? buildEmptyLogPage<AuditLogListRecord>(search)

  return (
    <ResourceListPage
      title='审计日志'
      description='记录系统接口请求的鉴权、方法、模块与执行结果。'
      endpoint='/api/v1/system/logs/audit/page'
      searchPlaceholder='搜索 requestId、模块、路径或用户名'
      search={search}
      navigate={navigate}
      columns={auditLogColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '记录总数', value: String(data.total), hint: '支持时间区间筛选和关键字检索。' },
        { label: '当前分页', value: String(search.page), hint: '每页参数来自查询态。' },
        { label: '关键字', value: search.keyword || '未设置', hint: '支持路径、模块等模糊匹配。' },
      ]}
    />
  )
}

export function SystemLoginLogListPage() {
  const search = loginLogListRoute.useSearch()
  const navigate = loginLogListRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-log-login-list', search],
    queryFn: () => listLoginLogs(search),
  })
  const data = query.data ?? buildEmptyLogPage<LoginLogListRecord>(search)

  return (
    <ResourceListPage
      title='登录日志'
      description='查看登录成功与失败、登录来源和请求信息。'
      endpoint='/api/v1/system/logs/login/page'
      searchPlaceholder='搜索用户名、UID 或 IP 地址'
      search={search}
      navigate={navigate}
      columns={loginLogColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '登录记录', value: String(data.total), hint: '可用作账号安全排障。' },
        { label: '当前分页', value: String(search.page), hint: '支持分页与排序。' },
        { label: '状态过滤', value: '未设定', hint: '后端返回的状态码便于快速分拣。' },
      ]}
    />
  )
}

export function SystemNotificationLogListPage() {
  const search = notificationLogListRoute.useSearch()
  const navigate = notificationLogListRoute.useNavigate()
  const query = useQuery({
    queryKey: ['system-log-notification-list', search],
    queryFn: () => listNotificationLogs(search),
  })
  const data = query.data ?? buildEmptyLogPage<NotificationLogListRecord>(search)

  return (
    <ResourceListPage
      title='通知日志'
      description='查看通知发送记录、通道信息与返回状态。'
      endpoint='/api/v1/system/logs/notifications/page'
      searchPlaceholder='搜索接收人、标题或渠道名称'
      search={search}
      navigate={navigate}
      columns={notificationLogColumns}
      data={data.records}
      total={data.total}
      summaries={[
        { label: '发送记录', value: String(data.total), hint: '主要用于追踪发送失败。' },
        { label: '当前分页', value: String(search.page), hint: '支持关键字模糊检索。' },
        { label: '回执状态', value: 'SUCCESS/FAILED', hint: '按状态可快速区分成功与失败。' },
      ]}
    />
  )
}

export function SystemAuditLogDetailPage() {
  const { logId } = auditLogDetailRoute.useParams()
  const query = useQuery({
    queryKey: ['system-log-audit-detail', logId],
    queryFn: () => getAuditLogDetail(logId),
  })

  if (query.isLoading) {
    return (
      <LogPageLoadingState
        title='审计日志详情'
        description='查看单条请求链路的上下文、耗时与异常信息。'
        hasSidebar
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <LogPageErrorState
        title='审计日志详情'
        description='查看单条请求链路的上下文、耗时与异常信息。'
        retry={query.refetch}
        listHref='/system/logs/audit/list'
      />
    )
  }

  const detail: AuditLogDetail = query.data

  return (
    <PageShell
      title='审计日志详情'
      description='查看单条请求链路完整上下文和执行摘要。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/logs/audit/list'>
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
              <BadgeCheck data-icon='inline-start' className='text-primary' />
              请求基础信息
            </CardTitle>
            <CardDescription>包含方法、路径、状态码和耗时。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 md:grid-cols-2'>
            <InfoCell label='请求 ID' value={detail.requestId} />
            <InfoCell label='日志 ID' value={detail.logId} />
            <InfoCell label='模块' value={detail.module} />
            <InfoCell label='请求路径' value={detail.path} />
            <InfoCell label='请求方法' value={detail.method} />
            <InfoCell label='发起用户' value={detail.username} />
            <InfoCell label='登陆 ID' value={detail.loginId} />
            <InfoCell
              label='状态'
              value={
                <Badge variant={resolveLogStatusVariant(detail.status)}>
                  {detail.status}
                </Badge>
              }
            />
            <InfoCell label='状态码' value={detail.statusCode} />
            <InfoCell label='客户端 IP' value={detail.clientIp} />
            <InfoCell label='耗时' value={`${detail.durationMs}ms`} />
            <InfoCell label='创建时间' value={formatDateTime(detail.createdAt)} />
            <InfoCell
              label='时间戳'
              value={new Date(detail.createdAt).toISOString()}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className='flex items-center gap-2'>
              <Clock data-icon='inline-start' className='text-muted-foreground' />
              运行上下文
            </CardTitle>
            <CardDescription>请求头、UA 与异常信息用于问题定位。</CardDescription>
          </CardHeader>
          <CardContent className='space-y-3'>
            <InfoCell label='User-Agent' value={detail.userAgent || '未记录'} />
            <InfoCell
              label='错误信息'
              value={<pre className='whitespace-pre-wrap text-xs'>{detail.errorMessage || '无'}</pre>}
            />
          </CardContent>
        </Card>
      </div>
    </PageShell>
  )
}

export function SystemLoginLogDetailPage() {
  const { logId } = loginLogDetailRoute.useParams()
  const query = useQuery({
    queryKey: ['system-log-login-detail', logId],
    queryFn: () => getLoginLogDetail(logId),
  })

  if (query.isLoading) {
    return (
      <LogPageLoadingState
        title='登录日志详情'
        description='查看单次登录请求的账户、结果与耗时。'
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <LogPageErrorState
        title='登录日志详情'
        description='查看单次登录请求的账户、结果与耗时。'
        retry={query.refetch}
        listHref='/system/logs/login/list'
      />
    )
  }

  const detail: LoginLogDetail = query.data

  return (
    <PageShell
      title='登录日志详情'
      description='聚焦登录成功与失败的请求信息。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/logs/login/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <Card>
        <CardHeader>
          <CardTitle>登录上下文</CardTitle>
          <CardDescription>包含请求标识、路径、返回码与耗时。</CardDescription>
        </CardHeader>
        <CardContent className='grid gap-3 md:grid-cols-2'>
          <InfoCell label='日志 ID' value={detail.logId} />
          <InfoCell label='请求 ID' value={detail.requestId || '未记录'} />
          <InfoCell label='用户名' value={detail.username} />
          <InfoCell label='用户 ID' value={detail.userId} />
          <InfoCell label='路径' value={detail.path} />
          <InfoCell
            label='状态'
            value={
              <Badge variant={resolveLogStatusVariant(detail.status)}>
                {detail.status}
              </Badge>
            }
          />
          <InfoCell label='状态码' value={detail.statusCode} />
          <InfoCell label='客户端 IP' value={detail.clientIp} />
          <InfoCell label='耗时' value={`${detail.durationMs}ms`} />
          <InfoCell label='创建时间' value={formatDateTime(detail.createdAt)} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>登录结果</CardTitle>
          <CardDescription>用于快速判断是否为异常登录行为。</CardDescription>
        </CardHeader>
        <CardContent className='space-y-3'>
          <InfoCell
            label='User-Agent'
            value={detail.userAgent || '未记录'}
          />
          <InfoCell
            label='结果消息'
            value={<pre className='whitespace-pre-wrap text-xs'>{detail.resultMessage || '无'}</pre>}
          />
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function SystemNotificationLogDetailPage() {
  const { recordId } = notificationLogDetailRoute.useParams()
  const query = useQuery({
    queryKey: ['system-log-notification-detail', recordId],
    queryFn: () => getNotificationLogDetail(recordId),
  })

  if (query.isLoading) {
    return (
      <LogPageLoadingState
        title='通知日志详情'
        description='查看单条通知发送记录的内容、回执与载荷。'
      />
    )
  }

  if (query.isError || !query.data) {
    return (
      <LogPageErrorState
        title='通知日志详情'
        description='查看单条通知发送记录的内容、回执与载荷。'
        retry={query.refetch}
        listHref='/system/logs/notifications/list'
      />
    )
  }

  const detail: NotificationLogDetail = query.data

  return (
    <PageShell
      title='通知日志详情'
      description='查看发送内容、通道回执和原始载荷。'
      actions={
        <Button asChild variant='ghost'>
          <Link to='/system/logs/notifications/list'>
            <ArrowLeft data-icon='inline-start' />
            返回列表
          </Link>
        </Button>
      }
    >
      <div className='grid gap-4 xl:grid-cols-[minmax(0,2fr)_360px]'>
        <Card>
          <CardHeader>
            <CardTitle>发送基础信息</CardTitle>
            <CardDescription>记录主键、接收人、通道与发送状态。</CardDescription>
          </CardHeader>
          <CardContent className='grid gap-3 md:grid-cols-2'>
            <InfoCell label='记录 ID' value={detail.recordId} />
            <InfoCell label='渠道 ID' value={detail.channelId} />
            <InfoCell label='渠道名称' value={detail.channelName} />
            <InfoCell label='渠道编码' value={detail.channelCode} />
            <InfoCell label='渠道类型' value={detail.channelType} />
            <InfoCell label='接收人' value={detail.recipient} />
            <InfoCell label='标题' value={detail.title} />
            <InfoCell label='发送状态' value={resolveLogStatusVariant(detail.status) === 'destructive'
              ? <Badge variant='destructive'>{detail.status}</Badge>
              : <Badge variant='secondary'>{detail.status}</Badge>} />
            <InfoCell label='发送时间' value={formatDateTime(detail.sentAt)} />
            <InfoCell label='成功标记' value={detail.success ? 'true' : 'false'} />
            <InfoCell label='服务商' value={detail.providerName} />
            <InfoCell label='渠道端点' value={detail.channelEndpoint || '未配置'} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className='flex items-center gap-2'>
              <MailCheck data-icon='inline-start' className='text-muted-foreground' />
              结果与载荷
            </CardTitle>
            <CardDescription>回执信息用于二次排障。</CardDescription>
          </CardHeader>
          <CardContent className='space-y-3'>
            <InfoCell
              label='回执信息'
              value={
                <pre className='whitespace-pre-wrap text-xs'>{detail.responseMessage || '无'}</pre>
              }
            />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>通知内容</CardTitle>
          <CardDescription>原始内容与 JSON 载荷。 </CardDescription>
        </CardHeader>
        <CardContent className='grid gap-3'>
          <InfoCell label='内容' value={detail.content} />
          <InfoCell
            label='Payload'
            value={
              <pre className='whitespace-pre-wrap rounded-lg bg-muted p-4 text-xs'>
                {JSON.stringify(detail.payload, null, 2)}
              </pre>
            }
          />
        </CardContent>
      </Card>
    </PageShell>
  )
}
