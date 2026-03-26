import { useEffect, useMemo, useState } from 'react'
import { Check, ChevronsUpDown, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import {
  listSystemUsers,
  type SystemUserRecord,
} from '@/lib/api/system-users'
import { cn } from '@/lib/utils'

type UserPickerOption = {
  userId: string
  displayName: string
  username: string
  departmentName: string
  postName: string
}

function buildSearch(keyword: string): ListQuerySearch {
  return {
    page: 1,
    pageSize: 20,
    keyword,
    filters: [],
    sorts: [],
    groups: [],
  }
}

function toOption(record: SystemUserRecord): UserPickerOption {
  return {
    userId: record.userId,
    displayName: record.displayName || record.username || record.userId,
    username: record.username,
    departmentName: record.departmentName,
    postName: record.postName,
  }
}

function mergeOptions(options: UserPickerOption[]) {
  const optionMap = new Map<string, UserPickerOption>()
  options.forEach((option) => {
    optionMap.set(option.userId, option)
  })
  return Array.from(optionMap.values())
}

function describeUser(option: UserPickerOption) {
  return [option.username, option.departmentName, option.postName]
    .filter(Boolean)
    .join(' · ')
}

export function UserPickerField({
  id,
  value,
  onChange,
  disabled,
  placeholder = '请选择负责人',
  ariaLabel,
  displayNames,
}: {
  id?: string
  value?: string
  onChange: (value: string) => void
  disabled?: boolean
  placeholder?: string
  ariaLabel?: string
  displayNames?: Record<string, string> | null
}) {
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [options, setOptions] = useState<UserPickerOption[]>([])

  useEffect(() => {
    let cancelled = false

    void listSystemUsers(buildSearch(keyword.trim()))
      .then((response) => {
        if (!cancelled) {
          setOptions(response.records.map(toOption))
        }
      })
      .catch(() => {
        if (!cancelled) {
          setOptions([])
        }
      })

    return () => {
      cancelled = true
    }
  }, [keyword])

  useEffect(() => {
    let cancelled = false

    if (!value) {
      return () => {
        cancelled = true
      }
    }

    if (options.some((option) => option.userId === value)) {
      return () => {
        cancelled = true
      }
    }

    void Promise.all([listSystemUsers(buildSearch('')), listSystemUsers(buildSearch(value))])
      .then(([baseResponse, selectedResponse]) => {
        if (!cancelled) {
          setOptions((currentOptions) =>
            mergeOptions([
              ...currentOptions,
              ...baseResponse.records.map(toOption),
              ...selectedResponse.records.map(toOption),
            ])
          )
        }
      })
      .catch(() => {})

    return () => {
      cancelled = true
    }
  }, [options, value])

  const selected = useMemo(
    () => options.find((option) => option.userId === value),
    [options, value]
  )
  const selectedDisplayName =
    selected?.displayName ?? (value ? displayNames?.[value] ?? value : null)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type='button'
          variant='outline'
          disabled={disabled}
          aria-label={ariaLabel}
          className='h-auto min-h-11 w-full justify-between px-3 py-2 text-left font-normal'
        >
          <span className='flex min-w-0 items-center gap-3'>
            <span className='flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary'>
              <UserRound className='size-4' />
            </span>
            <span className='min-w-0'>
              {selected ? (
                <span className='block min-w-0'>
                  <span className='block truncate text-sm font-medium text-foreground'>
                    {selectedDisplayName}
                  </span>
                  <span className='block truncate text-xs text-muted-foreground'>
                    {describeUser(selected) || selected.userId}
                  </span>
                </span>
              ) : selectedDisplayName ? (
                <span className='block min-w-0'>
                  <span className='block truncate text-sm font-medium text-foreground'>
                    {selectedDisplayName}
                  </span>
                  <span className='block truncate text-xs text-muted-foreground'>
                    {value}
                  </span>
                </span>
              ) : (
                <span className='text-sm text-muted-foreground'>
                  {value || placeholder}
                </span>
              )}
            </span>
          </span>
          <ChevronsUpDown className='size-4 shrink-0 text-muted-foreground' />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align='start'
        side='bottom'
        sideOffset={8}
        collisionPadding={16}
        className='w-[var(--radix-popover-trigger-width)] min-w-[360px] max-w-[calc(100vw-2rem)] overflow-hidden rounded-xl p-0'
      >
        <Command className='overflow-hidden rounded-xl'>
          <CommandInput
            value={keyword}
            onValueChange={setKeyword}
            placeholder='搜索姓名、账号、部门或岗位'
          />
          <CommandList className='max-h-[320px] overflow-y-auto'>
            <CommandEmpty>未找到匹配人员</CommandEmpty>
            <CommandGroup heading='人员列表'>
              {options.map((option) => {
                const active = option.userId === value
                return (
                  <CommandItem
                    key={option.userId}
                    value={[
                      option.displayName,
                      option.username,
                      option.userId,
                      option.departmentName,
                      option.postName,
                    ]
                      .filter(Boolean)
                      .join(' ')}
                    onSelect={() => {
                      onChange(option.userId)
                      setOpen(false)
                    }}
                    className='items-start py-3'
                  >
                    <span
                      className={cn(
                        'mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-sm border',
                        active ? 'border-primary bg-primary text-primary-foreground' : 'border-muted-foreground/30'
                      )}
                    >
                      {active ? <Check className='size-3' /> : null}
                    </span>
                    <span className='flex min-w-0 flex-1 flex-col gap-1'>
                      <span className='truncate text-sm font-medium'>{option.displayName}</span>
                      <span className='truncate text-xs text-muted-foreground'>
                        {describeUser(option) || option.userId}
                      </span>
                    </span>
                  </CommandItem>
                )
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
