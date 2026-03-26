import { type ReactNode } from 'react'
import { Search } from 'lucide-react'
import { type Table } from '@tanstack/react-table'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { DataTableViewOptions } from '@/components/data-table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  type ProTableDensityMode,
  ProTableDensity,
} from './pro-table-density'
import { ProTableExport, type ProTableExportScope } from './pro-table-export'
import { ProTableImport } from './pro-table-import'
import { ProTableRefresh } from './pro-table-refresh'

export function ProTableToolbar<TData>({
  table,
  total,
  searchPlaceholder,
  searchValue,
  onSearchChange,
  queryBadges,
  density,
  onDensityChange,
  onRefresh,
  isRefreshing = false,
  onExport,
  onImport,
  extraActions,
  createAction,
  supportsBoard = false,
  viewMode = 'table',
  onViewModeChange,
  lastRefreshedLabel,
  groupOptions = [],
  activeGroupField,
  onGroupFieldChange,
}: {
  table: Table<TData>
  total: number
  searchPlaceholder: string
  searchValue: string
  onSearchChange: (value: string) => void
  queryBadges?: string[]
  density: ProTableDensityMode
  onDensityChange: (value: ProTableDensityMode) => void
  onRefresh?: () => void
  isRefreshing?: boolean
  onExport?: (scope: ProTableExportScope) => void
  onImport?: (file: File) => void
  extraActions?: ReactNode
  createAction?: ReactNode
  supportsBoard?: boolean
  viewMode?: 'table' | 'board'
  onViewModeChange?: (value: 'table' | 'board') => void
  lastRefreshedLabel?: string
  groupOptions?: Array<{
    field: string
    label: string
  }>
  activeGroupField?: string
  onGroupFieldChange?: (field?: string) => void
}) {
  return (
    <div className='flex flex-col gap-4'>
      <div className='flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between'>
        <div className='space-y-2'>
          <div className='text-sm text-muted-foreground'>
            共 {total} 条
            {lastRefreshedLabel ? (
              <span className='ms-3'>最近刷新：{lastRefreshedLabel}</span>
            ) : null}
          </div>
          {queryBadges && queryBadges.length > 0 ? (
            <div className='flex flex-wrap gap-2'>
              {queryBadges.map((item) => (
                <Badge key={item} variant='outline'>
                  {item}
                </Badge>
              ))}
            </div>
          ) : null}
        </div>

        <div className='flex flex-wrap items-center justify-end gap-2'>
          {supportsBoard ? (
            <div className='inline-flex items-center rounded-lg border p-1 text-sm'>
              <button
                type='button'
                className={cn(
                  'rounded-md px-3 py-1.5 transition-colors',
                  viewMode === 'table'
                    ? 'bg-foreground text-background'
                    : 'text-muted-foreground hover:text-foreground'
                )}
                onClick={() => onViewModeChange?.('table')}
              >
                表格
              </button>
              <button
                type='button'
                className={cn(
                  'rounded-md px-3 py-1.5 transition-colors',
                  viewMode === 'board'
                    ? 'bg-foreground text-background'
                    : 'text-muted-foreground hover:text-foreground'
                )}
                onClick={() => onViewModeChange?.('board')}
              >
                看板
              </button>
            </div>
          ) : null}
          <ProTableRefresh onRefresh={onRefresh} isRefreshing={isRefreshing} />
          <ProTableExport onExport={onExport} disabled={!onExport} />
          <ProTableImport onImport={onImport} disabled={!onImport} />
          {extraActions}
          {createAction}
        </div>
      </div>

      <div className='flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between'>
        <div className='flex w-full flex-1 flex-wrap items-center gap-2'>
          <div className='relative min-w-[220px] flex-1 xl:max-w-md'>
            <Search className='pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground' />
            <Input
              value={searchValue}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder={searchPlaceholder}
              className='ps-9'
            />
          </div>
        </div>

        <div className='flex flex-wrap items-center justify-end gap-2'>
          {viewMode === 'table' ? (
            <>
              {groupOptions && groupOptions.length > 0 ? (
                <Select
                  value={activeGroupField ?? '__none'}
                  onValueChange={(value) =>
                    onGroupFieldChange?.(value === '__none' ? undefined : value)
                  }
                >
                  <SelectTrigger className='w-[150px]'>
                    <SelectValue placeholder='按字段分组' />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value='__none'>不分组</SelectItem>
                    {groupOptions.map((option) => (
                      <SelectItem key={option.field} value={option.field}>
                        按{option.label}分组
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : null}
              <ProTableDensity value={density} onValueChange={onDensityChange} />
              <DataTableViewOptions table={table} />
            </>
          ) : null}
        </div>
      </div>
    </div>
  )
}
