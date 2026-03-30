'use client'

import * as React from 'react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import {
  Maximize2,
  Minus,
  Move,
  XIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type DialogMode = 'normal' | 'fullscreen' | 'minimized'

type DialogSize = {
  width: number
}

type DialogOffset = {
  x: number
  y: number
}

function getInitialDialogSize() {
  if (typeof window === 'undefined') {
    return { width: 720 }
  }

  return {
    width: Math.max(320, Math.min(720, window.innerWidth - 32)),
  }
}

function Dialog({
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Root>) {
  return <DialogPrimitive.Root data-slot='pro-dialog' {...props} />
}

function DialogTrigger({
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Trigger>) {
  return <DialogPrimitive.Trigger data-slot='pro-dialog-trigger' {...props} />
}

function DialogClose({
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Close>) {
  return <DialogPrimitive.Close data-slot='pro-dialog-close' {...props} />
}

function DialogPortal({
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Portal>) {
  return <DialogPrimitive.Portal data-slot='pro-dialog-portal' {...props} />
}

function DialogOverlay({
  className,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Overlay>) {
  return (
    <DialogPrimitive.Overlay
      data-slot='pro-dialog-overlay'
      className={cn(
        'fixed inset-0 z-50 bg-black/50 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:animate-in data-[state=open]:fade-in-0',
        className
      )}
      {...props}
    />
  )
}

function DialogHeader({
  className,
  ...props
}: React.ComponentProps<'div'>) {
  return (
    <div
      data-slot='pro-dialog-header'
      className={cn('flex flex-col gap-2 text-center sm:text-start', className)}
      {...props}
    />
  )
}

function DialogFooter({
  className,
  ...props
}: React.ComponentProps<'div'>) {
  return (
    <div
      data-slot='pro-dialog-footer'
      className={cn('flex flex-col-reverse gap-2 sm:flex-row sm:justify-end', className)}
      {...props}
    />
  )
}

function DialogTitle({
  className,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Title>) {
  return (
    <DialogPrimitive.Title
      data-slot='pro-dialog-title'
      className={cn('text-lg leading-none font-semibold', className)}
      {...props}
    />
  )
}

function DialogDescription({
  className,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Description>) {
  return (
    <DialogPrimitive.Description
      data-slot='pro-dialog-description'
      className={cn('text-sm text-muted-foreground', className)}
      {...props}
    />
  )
}

function DialogContent({
  className,
  bodyClassName,
  title,
  description,
  children,
  style,
  draggable = true,
  resizable = true,
  fullscreenable = true,
  minimizable = true,
  bodyScrollable = true,
  showCloseButton = true,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content> & {
  title: string
  description?: string
  draggable?: boolean
  resizable?: boolean
  fullscreenable?: boolean
  minimizable?: boolean
  bodyScrollable?: boolean
  bodyClassName?: string
  showCloseButton?: boolean
}) {
  const [mode, setMode] = React.useState<DialogMode>('normal')
  const [size, setSize] = React.useState<DialogSize>(getInitialDialogSize)
  const [offset, setOffset] = React.useState<DialogOffset>({ x: 0, y: 0 })
  const draggingRef = React.useRef<{
    pointerId: number
    startX: number
    startY: number
    startOffsetX: number
    startOffsetY: number
  } | null>(null)

  const beginDrag = React.useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      if (!draggable || mode !== 'normal') {
        return
      }

      const target = event.target as HTMLElement | null
      if (target?.closest('button, a, input, textarea, select, [data-no-drag]')) {
        return
      }

      event.preventDefault()
      document.body.style.userSelect = 'none'
      draggingRef.current = {
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        startOffsetX: offset.x,
        startOffsetY: offset.y,
      }

      const onPointerMove = (pointerEvent: PointerEvent) => {
        if (!draggingRef.current || pointerEvent.pointerId !== draggingRef.current.pointerId) {
          return
        }

        setOffset({
          x: draggingRef.current.startOffsetX + pointerEvent.clientX - draggingRef.current.startX,
          y: draggingRef.current.startOffsetY + pointerEvent.clientY - draggingRef.current.startY,
        })
      }

      const onPointerUp = () => {
        draggingRef.current = null
        document.body.style.userSelect = ''
        window.removeEventListener('pointermove', onPointerMove)
        window.removeEventListener('pointerup', onPointerUp)
      }

      window.addEventListener('pointermove', onPointerMove)
      window.addEventListener('pointerup', onPointerUp, { once: true })
    },
    [draggable, mode, offset.x, offset.y]
  )

  React.useEffect(() => {
    if (typeof window === 'undefined' || mode !== 'normal') {
      return
    }

    const handleResize = () => {
      setSize((currentSize) => ({
        width: Math.min(currentSize.width, Math.max(320, window.innerWidth - 32)),
      }))
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [mode])

  const shellStyle: React.CSSProperties = React.useMemo(() => {
    if (mode === 'fullscreen') {
      return {
        inset: '1rem',
        width: 'calc(100vw - 2rem)',
        height: 'calc(100vh - 2rem)',
        transform: 'none',
      }
    }

    if (mode === 'minimized') {
      return {
        right: '1rem',
        bottom: '1rem',
        left: 'auto',
        top: 'auto',
        width: 'min(24rem, calc(100vw - 2rem))',
        height: 'auto',
        transform: 'none',
      }
    }

    return {
      left: '50%',
      top: '50%',
      width: `${size.width}px`,
      height: 'auto',
      maxHeight: 'calc(100vh - 2rem)',
      transform: `translate(calc(-50% + ${offset.x}px), calc(-50% + ${offset.y}px))`,
    }
  }, [mode, offset.x, offset.y, size.width])

  return (
    <DialogPortal>
      <DialogOverlay />
      <DialogPrimitive.Content
        data-slot='pro-dialog-content'
        className={cn(
          'fixed z-50 flex flex-col overflow-hidden rounded-xl border bg-background shadow-2xl outline-hidden data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
          mode === 'normal' && resizable && 'resize',
          className
        )}
        style={{
          ...style,
          ...shellStyle,
          resize:
            mode === 'normal' && resizable
              ? 'both'
              : 'none',
        }}
        {...props}
      >
        <div
          className={cn(
            'flex items-start gap-3 border-b px-4 py-3',
            mode === 'normal' && draggable && 'cursor-move'
          )}
          onPointerDown={beginDrag}
        >
          <div className='min-w-0 flex-1'>
            <DialogTitle className='truncate text-base'>{title}</DialogTitle>
            {description ? (
              <DialogDescription className='truncate'>{description}</DialogDescription>
            ) : null}
          </div>
          <div className='flex shrink-0 items-center gap-1'>
            {mode === 'minimized' ? (
              <Button
                type='button'
                variant='ghost'
                size='icon'
                className='size-8'
                data-no-drag
                onClick={() => setMode('normal')}
              >
                <Move />
                <span className='sr-only'>还原</span>
              </Button>
            ) : (
              <>
                {minimizable ? (
                  <Button
                    type='button'
                    variant='ghost'
                    size='icon'
                    className='size-8'
                    data-no-drag
                    onClick={() => setMode('minimized')}
                  >
                    <Minus />
                    <span className='sr-only'>最小化</span>
                  </Button>
                ) : null}
                {fullscreenable ? (
                  <Button
                    type='button'
                    variant='ghost'
                    size='icon'
                    className='size-8'
                    data-no-drag
                    onClick={() =>
                      setMode((currentMode) => (currentMode === 'fullscreen' ? 'normal' : 'fullscreen'))
                    }
                  >
                    <Maximize2 />
                    <span className='sr-only'>
                      {mode === 'fullscreen' ? '退出全屏' : '全屏'}
                    </span>
                  </Button>
                ) : null}
              </>
            )}
            {showCloseButton ? (
              <DialogPrimitive.Close asChild>
                <Button type='button' variant='ghost' size='icon' className='size-8' data-no-drag>
                  <XIcon />
                  <span className='sr-only'>关闭</span>
                </Button>
              </DialogPrimitive.Close>
            ) : null}
          </div>
        </div>

        {mode !== 'minimized' ? (
          <div className='flex min-h-0 flex-1 flex-col overflow-hidden'>
            <div
              className={cn(
                'flex min-h-0 flex-1 flex-col p-4',
                bodyScrollable ? 'overflow-auto' : 'overflow-hidden',
                bodyClassName
              )}
            >
              {children}
            </div>
          </div>
        ) : null}
      </DialogPrimitive.Content>
    </DialogPortal>
  )
}

export {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogOverlay,
  DialogPortal,
  DialogTitle,
  DialogTrigger,
}
