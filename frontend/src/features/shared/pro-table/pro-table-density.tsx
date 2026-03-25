import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'

export type ProTableDensityMode = 'compact' | 'default' | 'comfortable'

export function ProTableDensity({
  value,
  onValueChange,
}: {
  value: ProTableDensityMode
  onValueChange: (value: ProTableDensityMode) => void
}) {
  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button variant='outline' size='sm'>
          密度
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align='end' className='w-40'>
        <DropdownMenuLabel>表格密度</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuRadioGroup
          value={value}
          onValueChange={(next) => onValueChange(next as ProTableDensityMode)}
        >
          <DropdownMenuRadioItem value='compact'>紧凑</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value='default'>标准</DropdownMenuRadioItem>
          <DropdownMenuRadioItem value='comfortable'>舒适</DropdownMenuRadioItem>
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
