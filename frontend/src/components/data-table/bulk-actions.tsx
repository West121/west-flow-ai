import { useState, useEffect, useRef } from 'react'
import { type Table } from '@tanstack/react-table'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'

type DataTableBulkActionsProps<TData> = {
  table: Table<TData>
  entityName: string
  children: React.ReactNode
}

/**
 * 表格勾选后显示的批量操作工具条。
 *
 * @template TData 表格数据类型。
 * @param {object} props 组件参数。
 * @param {Table<TData>} props.table react-table 实例。
 * @param {string} props.entityName 被操作对象名称，例如 task、user。
 * @param {React.ReactNode} props.children 工具条里的操作按钮。
 * @returns {React.ReactNode | null} 有勾选时返回工具条，否则返回空。
 */
export function DataTableBulkActions<TData>({
  table,
  entityName,
  children,
}: DataTableBulkActionsProps<TData>): React.ReactNode | null {
  const selectedRows = table.getFilteredSelectedRowModel().rows
  const selectedCount = selectedRows.length
  const toolbarRef = useRef<HTMLDivElement>(null)
  const [announcement, setAnnouncement] = useState('')

  // 选中数量变化时，给屏幕阅读器发出提示。
 useEffect(() => {
    if (selectedCount > 0) {
      const message = `已选择 ${selectedCount} 个${entityName}，可执行批量操作。`

      // 延迟一下再更新，避免和当前渲染帧互相打架。
      queueMicrotask(() => {
        setAnnouncement(message)
      })

      // 提示只保留一小段时间，避免读屏内容堆积。
      const timer = setTimeout(() => setAnnouncement(''), 3000)
      return () => clearTimeout(timer)
    }
  }, [selectedCount, entityName])

  const handleClearSelection = () => {
    table.resetRowSelection()
  }

  const handleKeyDown = (event: React.KeyboardEvent) => {
    const buttons = toolbarRef.current?.querySelectorAll('button')
    if (!buttons) return

    const currentIndex = Array.from(buttons).findIndex(
      (button) => button === document.activeElement
    )

    switch (event.key) {
      case 'ArrowRight': {
        event.preventDefault()
        const nextIndex = (currentIndex + 1) % buttons.length
        buttons[nextIndex]?.focus()
        break
      }
      case 'ArrowLeft': {
        event.preventDefault()
        const prevIndex =
          currentIndex === 0 ? buttons.length - 1 : currentIndex - 1
        buttons[prevIndex]?.focus()
        break
      }
      case 'Home':
        event.preventDefault()
        buttons[0]?.focus()
        break
      case 'End':
        event.preventDefault()
        buttons[buttons.length - 1]?.focus()
        break
      case 'Escape': {
        // 先判断 Escape 是否来自下拉菜单，避免误清空选择。
        const target = event.target as HTMLElement
        const activeElement = document.activeElement as HTMLElement

        // 事件源或当前焦点如果在下拉触发器上，就交给下拉自己处理。
        const isFromDropdownTrigger =
          target?.getAttribute('data-slot') === 'dropdown-menu-trigger' ||
          activeElement?.getAttribute('data-slot') ===
            'dropdown-menu-trigger' ||
          target?.closest('[data-slot="dropdown-menu-trigger"]') ||
          activeElement?.closest('[data-slot="dropdown-menu-trigger"]')

        // 如果焦点在弹出的菜单内容里，也不要顺手清空勾选。
        const isFromDropdownContent =
          activeElement?.closest('[data-slot="dropdown-menu-content"]') ||
          target?.closest('[data-slot="dropdown-menu-content"]')

        if (isFromDropdownTrigger || isFromDropdownContent) {
          return
        }

        // 这次 Escape 是给工具条的，直接清空勾选。
        event.preventDefault()
        handleClearSelection()
        break
      }
    }
  }

  if (selectedCount === 0) {
    return null
  }

  return (
    <>
      {/* 给读屏软件的隐藏提示区。 */}
      <div
        aria-live='polite'
        aria-atomic='true'
        className='sr-only'
        role='status'
      >
        {announcement}
      </div>

      <div
        ref={toolbarRef}
        role='toolbar'
        aria-label={`${selectedCount} 个${entityName}的批量操作工具栏`}
        aria-describedby='bulk-actions-description'
        tabIndex={-1}
        onKeyDown={handleKeyDown}
        className={cn(
          'fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-xl',
          'transition-all delay-100 duration-300 ease-out hover:scale-105',
          'focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none'
        )}
      >
        <div
          className={cn(
            'p-2 shadow-xl',
            'rounded-xl border',
            'bg-background/95 backdrop-blur-lg supports-backdrop-filter:bg-background/60',
            'flex items-center gap-x-2'
          )}
        >
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant='outline'
                size='icon'
                onClick={handleClearSelection}
                className='size-6 rounded-full'
                aria-label='清空选择'
                title='清空选择（Escape）'
              >
                <X />
                <span className='sr-only'>清空选择</span>
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <p>清空选择（Escape）</p>
            </TooltipContent>
          </Tooltip>

          <Separator
            className='h-5'
            orientation='vertical'
            aria-hidden='true'
          />

          <div
            className='flex items-center gap-x-1 text-sm'
            id='bulk-actions-description'
          >
            <Badge
              variant='default'
              className='min-w-8 rounded-lg'
              aria-label={`已选择 ${selectedCount} 个${entityName}`}
            >
              {selectedCount}
            </Badge>{' '}
            <span className='hidden sm:inline'>
              个{entityName}已选
            </span>
          </div>

          <Separator
            className='h-5'
            orientation='vertical'
            aria-hidden='true'
          />

          {children}
        </div>
      </div>
    </>
  )
}
