import { ClipboardList, FileText } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { type RuntimeFormComponentProps } from '@/features/forms/runtime/types'
import { RuntimeFormSectionTitle } from './runtime-form-section-title'

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

function readText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

export function OAExpenseStartForm({
  value,
  onChange,
  disabled,
  processFormKey,
  processFormVersion,
}: RuntimeFormComponentProps) {
  const amountValue = readNumber(value.amount)
  const reasonValue = readText(value.reason)

  return (
    <div className='space-y-5'>
      <RuntimeFormSectionTitle icon={ClipboardList} title='报销信息' />

      <div className='space-y-2'>
        <Label htmlFor='expense-amount'>报销金额</Label>
        <Input
          id='expense-amount'
          type='number'
          min='0'
          step='0.01'
          placeholder='例如：128.5'
          disabled={disabled}
          value={amountValue}
          onChange={(event) => {
            const nextValue = event.target.value.trim()
            onChange({
              ...value,
              amount: nextValue,
            })
          }}
        />
        <p className='text-xs text-muted-foreground'>支持小数金额。</p>
      </div>

      <RuntimeFormSectionTitle icon={FileText} title='报销说明' />

      <div className='space-y-2'>
        <Label htmlFor='expense-reason'>报销事由</Label>
        <Textarea
          id='expense-reason'
          className='min-h-28'
          placeholder='请输入报销事由'
          disabled={disabled}
          value={reasonValue}
          onChange={(event) => {
            onChange({
              ...value,
              reason: event.target.value,
            })
          }}
        />
      </div>

      <p className='text-xs text-muted-foreground'>
        表单编码 {processFormKey} · 版本 {processFormVersion}
      </p>
    </div>
  )
}
