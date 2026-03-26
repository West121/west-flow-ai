import { type ReactNode } from 'react'
import { FormLabel } from '@/components/ui/form'
import { cn } from '@/lib/utils'

export function ProFormLabel({
  children,
  required = false,
}: {
  children: ReactNode
  required?: boolean
}) {
  return (
    <FormLabel
      className={cn(
        required
          ? "before:mr-1 before:text-destructive before:content-['*']"
          : undefined
      )}
    >
      {children}
    </FormLabel>
  )
}
