import { Check, ChevronsUpDown } from 'lucide-react'
import * as React from 'react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from '@/components/ui/command'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'

export type MultiSelectOption = {
  label: string
  value: string
  description?: string
}

type MultiSelectDropdownProps = Omit<
  React.ComponentProps<typeof Button>,
  'onChange' | 'value'
> & {
  value: string[]
  onChange: (value: string[]) => void
  options: MultiSelectOption[]
  placeholder?: string
  searchPlaceholder?: string
  emptyText?: string
}

// 通用下拉多选，支持搜索、勾选和清空选择。
export const MultiSelectDropdown = React.forwardRef<
  HTMLButtonElement,
  MultiSelectDropdownProps
>(function MultiSelectDropdown(
  {
    value,
    onChange,
    options,
    placeholder = '请选择',
    searchPlaceholder = '搜索',
    emptyText = '未找到匹配项',
    className,
    disabled,
    ...buttonProps
  },
  ref
) {
  const selectedOptions = options.filter((option) => value.includes(option.value))

  const toggleOption = (optionValue: string) => {
    if (value.includes(optionValue)) {
      onChange(value.filter((item) => item !== optionValue))
      return
    }

    onChange([...value, optionValue])
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          ref={ref}
          type='button'
          variant='outline'
          disabled={disabled}
          className={cn('justify-between gap-2', className)}
          {...buttonProps}
        >
          <span className='flex min-w-0 flex-1 items-center gap-2 text-left'>
            {selectedOptions.length === 0 ? (
              <span className='truncate text-muted-foreground'>{placeholder}</span>
            ) : (
              <span className='truncate'>已选 {selectedOptions.length} 项</span>
            )}
          </span>
          <ChevronsUpDown className='size-4 shrink-0 opacity-50' />
        </Button>
      </PopoverTrigger>
      <PopoverContent className='w-[420px] p-0' align='start'>
        <Command>
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList>
            <CommandEmpty>{emptyText}</CommandEmpty>
            <CommandGroup>
              {options.map((option) => {
                const selected = value.includes(option.value)

                return (
                  <CommandItem
                    key={option.value}
                    value={`${option.label} ${option.description ?? ''}`}
                    onSelect={() => toggleOption(option.value)}
                  >
                    <div
                      className={cn(
                        'flex size-4 items-center justify-center rounded-sm border border-primary',
                        selected
                          ? 'bg-primary text-primary-foreground'
                          : 'opacity-50 [&_svg]:invisible'
                      )}
                    >
                      <Check className='size-4' />
                    </div>
                    <div className='grid gap-0.5'>
                      <span className='text-sm font-medium'>{option.label}</span>
                      {option.description ? (
                        <span className='text-xs text-muted-foreground'>
                          {option.description}
                        </span>
                      ) : null}
                    </div>
                  </CommandItem>
                )
              })}
            </CommandGroup>
            {value.length > 0 ? (
              <>
                <CommandSeparator />
                <CommandGroup>
                  <CommandItem
                    onSelect={() => onChange([])}
                    className='justify-center text-center'
                  >
                    清空选择
                  </CommandItem>
                </CommandGroup>
              </>
            ) : null}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
})
