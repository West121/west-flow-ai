import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { type RuntimeFormComponentProps } from '@/features/forms/runtime/types'

function readNumber(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : ''
  }

  return ''
}

export function OALeaveStartForm({
  value,
  onChange,
  disabled,
  processFormKey,
  processFormVersion,
}: RuntimeFormComponentProps) {
  const daysValue = readNumber(value.days)
  const reasonValue = typeof value.reason === 'string' ? value.reason : ''

  return (
    <div className='space-y-4'>
      <div className='space-y-1'>
        <p className='text-sm font-medium'>OA 请假发起表单</p>
        <p className='text-xs text-muted-foreground'>
          表单编码 {processFormKey} · 版本 {processFormVersion}
        </p>
      </div>

      <div className='space-y-2'>
        <Label htmlFor='leave-days'>请假天数</Label>
        <Input
          id='leave-days'
          type='number'
          min='1'
          disabled={disabled}
          value={daysValue}
          onChange={(event) => {
            const nextValue = event.target.value.trim()
            onChange({
              ...value,
              days: nextValue ? Number(nextValue) : '',
            })
          }}
        />
      </div>

      <div className='space-y-2'>
        <Label htmlFor='leave-reason'>请假原因</Label>
        <Textarea
          id='leave-reason'
          disabled={disabled}
          className='min-h-28'
          value={reasonValue}
          onChange={(event) => {
            onChange({
              ...value,
              reason: event.target.value,
            })
          }}
        />
      </div>
    </div>
  )
}
