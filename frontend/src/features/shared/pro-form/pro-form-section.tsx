import { type ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function ProFormSection({
  title,
  description,
  children,
  columns = 2,
}: {
  title: string
  description?: string
  children: ReactNode
  columns?: 1 | 2
}) {
  return (
    <section className='space-y-4'>
      <div className='space-y-1'>
        <h3 className='text-sm font-semibold'>{title}</h3>
        {description ? (
          <p className='text-sm text-muted-foreground'>{description}</p>
        ) : null}
      </div>
      <div className={cn('grid gap-4', columns === 2 ? 'md:grid-cols-2' : 'grid-cols-1')}>
        {children}
      </div>
    </section>
  )
}
