import { type ReactNode } from 'react'
import { AlertCircle } from 'lucide-react'
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
import { Link } from '@tanstack/react-router'

// AI 管理页统一的时间格式，保证注册表和运行记录展示风格一致。
export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

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

// 将 JSON 字符串尽量转成可读块，坏数据则原样展示。
export function prettyJson(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

// 把逗号分隔字符串或数组展示成中文标签。
export function renderTags(value: string[] | string | null | undefined) {
  if (!value) {
    return '-'
  }

  if (Array.isArray(value)) {
    return value.length > 0 ? value.join('，') : '-'
  }

  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .join('，') || '-'
}

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
