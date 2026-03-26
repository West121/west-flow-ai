import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { FileDown } from 'lucide-react'

export type ProTableExportScope = 'current-page' | 'filtered-results' | 'selected-rows'
export type ProTableExportHandler<TData> = (
  scope: ProTableExportScope,
  rows: TData[]
) => void

export function ProTableExport({
  onExport,
  disabled = false,
}: {
  onExport?: (scope: ProTableExportScope) => void
  disabled?: boolean
}) {
  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button variant='outline' size='sm' disabled={disabled}>
          <FileDown />
          导出
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align='end' className='w-48'>
        <DropdownMenuLabel>导出范围</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => onExport?.('current-page')}>
          当前页
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => onExport?.('filtered-results')}>
          当前筛选结果
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => onExport?.('selected-rows')}>
          已选行
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
