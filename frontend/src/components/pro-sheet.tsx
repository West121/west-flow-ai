'use client'

import * as React from 'react'
import * as SheetPrimitive from '@radix-ui/react-dialog'
import { XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type SheetSide = 'top' | 'right' | 'bottom' | 'left'

function Sheet({
  ...props
}: React.ComponentProps<typeof SheetPrimitive.Root>) {
  return <SheetPrimitive.Root data-slot='pro-sheet' {...props} />
}

function SheetTrigger({
  ...props
}: React.ComponentProps<typeof SheetPrimitive.Trigger>) {
  return <SheetPrimitive.Trigger data-slot='pro-sheet-trigger' {...props} />
}

function SheetClose({
  ...props
}: React.ComponentProps<typeof SheetPrimitive.Close>) {
  return <SheetPrimitive.Close data-slot='pro-sheet-close' {...props} />
}

function SheetPortal({
  ...props
}: React.ComponentProps<typeof SheetPrimitive.Portal>) {
  return <SheetPrimitive.Portal data-slot='pro-sheet-portal' {...props} />
}

function SheetOverlay({
  className,
  ...props
}: React.ComponentProps<typeof SheetPrimitive.Overlay>) {
  return (
    <SheetPrimitive.Overlay
      data-slot='pro-sheet-overlay'
      className={cn(
        'fixed inset-0 z-50 bg-black/50 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:animate-in data-[state=open]:fade-in-0',
        className
      )}
      {...props}
    />
  )
}

function SheetHeader({
  className,
  ...props
}: React.ComponentProps<'div'>) {
  return (
    <div
      data-slot='pro-sheet-header'
      className={cn('flex flex-col gap-1.5 p-4', className)}
      {...props}
    />
  )
}

function SheetFooter({
  className,
  ...props
}: React.ComponentProps<'div'>) {
  return (
    <div
      data-slot='pro-sheet-footer'
      className={cn('mt-auto flex flex-col gap-2 p-4', className)}
      {...props}
    />
  )
}

function SheetTitle({
  className,
  ...props
}: React.ComponentProps<typeof SheetPrimitive.Title>) {
  return (
    <SheetPrimitive.Title
      data-slot='pro-sheet-title'
      className={cn('font-semibold text-foreground', className)}
      {...props}
    />
  )
}

function SheetDescription({
  className,
  ...props
}: React.ComponentProps<typeof SheetPrimitive.Description>) {
  return (
    <SheetPrimitive.Description
      data-slot='pro-sheet-description'
      className={cn('text-sm text-muted-foreground', className)}
      {...props}
    />
  )
}

function SheetContent({
  className,
  title,
  description,
  children,
  style,
  side = 'right',
  resizable = true,
  showCloseButton = true,
  ...props
}: React.ComponentProps<typeof SheetPrimitive.Content> & {
  title: string
  description?: string
  side?: SheetSide
  resizable?: boolean
  showCloseButton?: boolean
}) {
  const [size, setSize] = React.useState(() => {
    if (typeof window === 'undefined') {
      return { width: 420, height: 360 }
    }

    return {
      width: Math.max(320, Math.min(420, window.innerWidth - 32)),
      height: Math.max(240, Math.min(360, window.innerHeight - 32)),
    }
  })

  React.useEffect(() => {
    if (typeof window === 'undefined') {
      return
    }

    const handleResize = () => {
      setSize((currentSize) => ({
        width: Math.max(320, Math.min(currentSize.width, Math.max(320, window.innerWidth - 32))),
        height: Math.max(240, Math.min(currentSize.height, Math.max(240, window.innerHeight - 32))),
      }))
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const shellStyle: React.CSSProperties = React.useMemo(() => {
    if (side === 'right' || side === 'left') {
      return {
        width: `${size.width}px`,
        maxWidth: 'calc(100vw - 2rem)',
        resize: resizable ? 'horizontal' : 'none',
      }
    }

    return {
      height: `${size.height}px`,
      maxHeight: 'calc(100vh - 2rem)',
      resize: resizable ? 'vertical' : 'none',
    }
  }, [resizable, side, size.height, size.width])

  return (
    <SheetPortal>
      <SheetOverlay />
      <SheetPrimitive.Content
        data-slot='pro-sheet-content'
        className={cn(
          'fixed z-50 flex flex-col overflow-hidden bg-background shadow-lg transition ease-in-out data-[state=closed]:animate-out data-[state=closed]:duration-300 data-[state=open]:animate-in data-[state=open]:duration-500',
          side === 'right' &&
            'inset-y-0 end-0 h-full border-s data-[state=closed]:slide-out-to-end data-[state=open]:slide-in-from-end',
          side === 'left' &&
            'inset-y-0 start-0 h-full border-e data-[state=closed]:slide-out-to-start data-[state=open]:slide-in-from-start',
          side === 'top' &&
            'inset-x-0 top-0 w-full border-b data-[state=closed]:slide-out-to-top data-[state=open]:slide-in-from-top',
          side === 'bottom' &&
            'inset-x-0 bottom-0 w-full border-t data-[state=closed]:slide-out-to-bottom data-[state=open]:slide-in-from-bottom',
          className
        )}
        style={{
          ...style,
          ...shellStyle,
        }}
        {...props}
      >
        <div className='flex items-start gap-3 border-b px-4 py-3'>
          <div className='min-w-0 flex-1'>
            <SheetTitle className='truncate text-base'>{title}</SheetTitle>
            {description ? (
              <SheetDescription className='truncate'>{description}</SheetDescription>
            ) : null}
          </div>
          {showCloseButton ? (
            <SheetPrimitive.Close asChild>
              <Button type='button' variant='ghost' size='icon' className='size-8'>
                <XIcon />
                <span className='sr-only'>关闭</span>
              </Button>
            </SheetPrimitive.Close>
          ) : null}
        </div>

        <div className='min-h-0 flex-1 overflow-auto p-4'>{children}</div>
      </SheetPrimitive.Content>
    </SheetPortal>
  )
}

export {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetOverlay,
  SheetPortal,
  SheetTitle,
  SheetTrigger,
}
