import { type ReactNode } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export function ProFormShell({
  title,
  description,
  children,
  actions,
  className,
}: {
  title?: string
  description?: string
  children: ReactNode
  actions?: ReactNode
  className?: string
}) {
  return (
    <Card className={cn('py-4', className)}>
      {title || description ? (
        <CardHeader className='pb-0'>
          {title ? <CardTitle>{title}</CardTitle> : null}
          {description ? <CardDescription>{description}</CardDescription> : null}
        </CardHeader>
      ) : null}
      <CardContent className='space-y-6'>
        {children}
        {actions ? <div className='flex flex-wrap items-center gap-3'>{actions}</div> : null}
      </CardContent>
    </Card>
  )
}
