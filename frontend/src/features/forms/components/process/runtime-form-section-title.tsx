import { type LucideIcon } from 'lucide-react'

export function RuntimeFormSectionTitle({
  icon: Icon,
  title,
}: {
  icon: LucideIcon
  title: string
}) {
  return (
    <div className='flex items-center gap-3 pt-1'>
      <span className='flex size-9 items-center justify-center rounded-xl bg-primary/10 text-primary'>
        <Icon className='size-4' />
      </span>
      <div className='text-base font-semibold tracking-tight text-foreground'>
        {title}
      </div>
    </div>
  )
}
