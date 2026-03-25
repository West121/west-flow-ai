import { useEffect, useMemo, useRef, useState } from 'react'
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
import { type ListQuerySearch } from '@/features/shared/table/query-contract'
import { listSystemUsers, type SystemUserRecord } from '@/lib/api/system-users'
import { cn } from '@/lib/utils'

type UserPickerOption = {
  userId: string
  displayName: string
  username: string
  departmentName: string
  postName: string
}

type PickerPanelMetrics = {
  left: number
  width: number
  top?: number
  bottom?: number
  maxHeight: number
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
}: {
  id?: string
  value?: string
  onChange: (value: string) => void
  disabled?: boolean
  placeholder?: string
  ariaLabel?: string
}) {
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [options, setOptions] = useState<UserPickerOption[]>([])
  const [panelMetrics, setPanelMetrics] = useState<PickerPanelMetrics | null>(null)
  const triggerRef = useRef<HTMLButtonElement | null>(null)

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

    void Promise.all([
      listSystemUsers(buildSearch('')),
      listSystemUsers(buildSearch(value)),
    ])
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

  useEffect(() => {
    if (!open) {
      return undefined
    }

    const updatePanelMetrics = () => {
      const trigger = triggerRef.current
      if (!trigger) {
        return
      }

      const rect = trigger.getBoundingClientRect()
      const viewportWidth = window.innerWidth
      const viewportHeight = window.innerHeight
      const preferredWidth = Math.max(rect.width, 360)
      const width = Math.min(preferredWidth, viewportWidth - 32)
      const left = Math.min(
        Math.max(16, rect.left),
        Math.max(16, viewportWidth - width - 16)
      )
      const availableBelow = viewportHeight - rect.bottom - 16
      const availableAbove = rect.top - 16
      const openUpward = availableBelow < 280 && availableAbove > availableBelow
      const availableHeight = openUpward ? availableAbove : availableBelow
      const maxHeight = Math.max(180, Math.min(420, availableHeight - 8))

      setPanelMetrics(
        openUpward
          ? {
              left,
              width,
              bottom: Math.max(16, viewportHeight - rect.top + 8),
              maxHeight,
            }
          : {
              left,
              width,
              top: Math.max(16, rect.bottom + 8),
              maxHeight,
            }
      )
    }

    updatePanelMetrics()
    window.addEventListener('resize', updatePanelMetrics)
    window.addEventListener('scroll', updatePanelMetrics, true)

    return () => {
      window.removeEventListener('resize', updatePanelMetrics)
      window.removeEventListener('scroll', updatePanelMetrics, true)
    }
  }, [open])

  const selected = useMemo(
    () => options.find((option) => option.userId === value),
    [options, value]
  )

  return (
    <div className='relative'>
      {open ? (
        <div
          className='fixed inset-0 z-40'
          onClick={() => setOpen(false)}
        />
      ) : null}
      <div className='relative z-50'>
        <Button
          id={id}
          ref={triggerRef}
          type='button'
          variant='outline'
          disabled={disabled}
          aria-label={ariaLabel}
          className='h-auto min-h-11 w-full justify-between px-3 py-2 text-left font-normal'
          onClick={() => setOpen((currentValue) => !currentValue)}
        >
          <span className='flex min-w-0 items-center gap-3'>
            <span className='flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary'>
              <UserRound className='size-4' />
            </span>
            <span className='min-w-0'>
              {selected ? (
                <span className='block min-w-0'>
                  <span className='block truncate text-sm font-medium text-foreground'>
                    {selected.displayName}
                  </span>
                  <span className='block truncate text-xs text-muted-foreground'>
                    {describeUser(selected) || selected.userId}
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
        {open ? (
          <div
            className='fixed z-50 rounded-xl border bg-popover shadow-lg'
            style={panelMetrics ?? undefined}
          >
            <Command className='overflow-hidden rounded-xl'>
              <CommandInput
                value={keyword}
                onValueChange={setKeyword}
                placeholder='搜索姓名、账号、部门或岗位'
              />
              <CommandList
                className='overflow-y-auto'
                style={{
                  maxHeight: panelMetrics ? Math.max(panelMetrics.maxHeight - 56, 120) : 320,
                }}
              >
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
          </div>
        ) : null}
      </div>
    </div>
  )
}
