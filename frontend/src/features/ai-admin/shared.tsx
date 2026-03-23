import { type ReactNode } from 'react'
import { AlertCircle } from 'lucide-react'
import { Link } from '@tanstack/react-router'
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
import { PageShell } from '@/features/shared/page-shell'
import {
  formatDateTime,
  formatDurationMillis,
  prettyJson,
} from './shared-formatters'
import {
  type AiObservabilitySummary,
  type AiRegistryLink,
} from '@/lib/api/ai-admin'

export function AiPageErrorState({
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
        <AlertDescription>AI 管理页面请求失败，请重试或返回列表页。</AlertDescription>
      </Alert>
      <div className='flex flex-wrap gap-2'>
        {retry ? <Button onClick={retry}>重试</Button> : null}
        <Button asChild variant='outline'>
          <Link to={listHref} search={{}}>
            返回列表
          </Link>
        </Button>
      </div>
    </PageShell>
  )
}

export function AiInfoCard({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <Card>
      <CardHeader className='pb-3'>
        <CardTitle>{title}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

export function AiKeyValueGrid({
  items,
}: {
  items: Array<{
    label: string
    value: ReactNode
  }>
}) {
  return (
    <div className='grid gap-3 md:grid-cols-2 xl:grid-cols-3'>
      {items.map((item) => (
        <div key={item.label} className='rounded-lg border bg-muted/20 p-4'>
          <div className='text-xs text-muted-foreground'>{item.label}</div>
          <div className='mt-2 text-sm leading-6'>{item.value}</div>
        </div>
      ))}
    </div>
  )
}

export function AiJsonBlock({ value }: { value: string | null | undefined }) {
  return (
    <pre className='max-h-[420px] overflow-auto rounded-lg border bg-muted/20 p-4 text-xs leading-6'>
      {prettyJson(value)}
    </pre>
  )
}

export function AiStatusBadge({
  label,
  variant = 'secondary',
}: {
  label: string
  variant?: 'secondary' | 'outline' | 'destructive'
}) {
  return <Badge variant={variant}>{label}</Badge>
}

export function AiPageHeaderBadge({ children }: { children: ReactNode }) {
  return <Badge variant='secondary'>{children}</Badge>
}

export function AiRegistryLinkList({
  title,
  links,
}: {
  title: string
  links: AiRegistryLink[]
}) {
  return (
    <div className='space-y-2'>
      <div className='text-xs text-muted-foreground'>{title}</div>
      {links.length > 0 ? (
        <div className='flex flex-wrap gap-2'>
          {links.map((link) => (
            <Badge key={`${link.entityType}-${link.entityId}`} variant='outline'>
              {link.entityCode}
            </Badge>
          ))}
        </div>
      ) : (
        <div className='text-sm text-muted-foreground'>-</div>
      )}
    </div>
  )
}

export function AiObservabilityGrid({
  value,
}: {
  value: AiObservabilitySummary | null | undefined
}) {
  if (!value) {
    return <div className='text-sm text-muted-foreground'>暂无运行态观测数据。</div>
  }

  return (
    <AiKeyValueGrid
      items={[
        { label: '累计调用', value: value.totalToolCalls },
        { label: '成功调用', value: value.successfulToolCalls },
        { label: '失败调用', value: value.failedToolCalls },
        { label: '待确认', value: value.pendingConfirmations },
        { label: '平均耗时', value: formatDurationMillis(value.averageDurationMillis) },
        { label: '最近 ToolCall', value: value.latestToolCallId || '-' },
        { label: '最近调用时间', value: formatDateTime(value.latestToolCallAt) },
        { label: '最近失败原因', value: value.latestFailureReason || '-' },
      ]}
    />
  )
}

export function AiEntityPageShell({
  title,
  description,
  actions,
  children,
}: {
  title: string
  description: string
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <PageShell title={title} description={description} actions={actions}>
      {children}
    </PageShell>
  )
}
