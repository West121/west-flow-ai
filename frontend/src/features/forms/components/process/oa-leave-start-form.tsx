import { ClipboardList, FileText } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { UserPickerField } from '@/features/shared/pro-form'
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

function readBoolean(value: unknown) {
  return value === true
}

const leaveTypeOptions = [
  { value: 'ANNUAL', label: '年假' },
  { value: 'PERSONAL', label: '事假' },
  { value: 'SICK', label: '病假' },
]

// OA 请假发起表单只负责展示和回填基础业务字段。
export function OALeaveStartForm({
  value,
  onChange,
  disabled,
  userDisplayNames,
  processFormKey,
  processFormVersion,
}: RuntimeFormComponentProps) {
  const daysValue = readNumber(value.days)
  const reasonValue = readText(value.reason)
  const leaveTypeValue = readText(value.leaveType) || 'ANNUAL'
  const urgentValue = readBoolean(value.urgent)
  const managerUserIdValue = readText(value.managerUserId)

  return (
    <div className='space-y-5'>
      <RuntimeFormSectionTitle icon={ClipboardList} title='申请信息' />

      <div className='space-y-2'>
        <Label htmlFor='leave-type'>请假类型</Label>
        <Select
          value={leaveTypeValue}
          disabled={disabled}
          onValueChange={(nextValue) => {
            onChange({
              ...value,
              leaveType: nextValue,
            })
          }}
        >
          <SelectTrigger id='leave-type'>
            <SelectValue placeholder='请选择请假类型' />
          </SelectTrigger>
          <SelectContent>
            {leaveTypeOptions.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className='space-y-2'>
        <Label htmlFor='leave-days'>请假天数</Label>
        <Input
          id='leave-days'
          type='number'
          min='1'
          placeholder='请输入请假天数'
          disabled={disabled}
          value={daysValue}
          onChange={(event) => {
            const nextValue = event.target.value.trim()
            onChange({
              ...value,
              days: nextValue ? Number(nextValue) : '',
              leaveDays: nextValue ? Number(nextValue) : '',
            })
          }}
        />
      </div>

      <div className='flex items-center justify-between rounded-xl border px-4 py-3'>
        <div className='space-y-0.5'>
          <Label htmlFor='leave-urgent'>是否紧急</Label>
          <p className='text-xs text-muted-foreground'>紧急请假会命中 HR 备案分支</p>
        </div>
        <Switch
          id='leave-urgent'
          disabled={disabled}
          checked={urgentValue}
          onCheckedChange={(checked) => {
            onChange({
              ...value,
              urgent: checked,
            })
          }}
        />
      </div>

      <div className='space-y-2'>
        <Label htmlFor='manager-user-id'>直属负责人</Label>
        <UserPickerField
          id='manager-user-id'
          value={managerUserIdValue}
          disabled={disabled}
          placeholder='请选择直属负责人'
          ariaLabel='直属负责人'
          displayNames={userDisplayNames}
          onChange={(nextValue) => {
            onChange({
              ...value,
              managerUserId: nextValue,
            })
          }}
        />
        <p className='text-xs text-muted-foreground'>
          将作为负责人确认节点的默认办理人
        </p>
      </div>

      <RuntimeFormSectionTitle icon={FileText} title='申请说明' />

      <div className='space-y-2'>
        <Label htmlFor='leave-reason'>请假原因</Label>
        <Textarea
          id='leave-reason'
          disabled={disabled}
          className='min-h-28'
          placeholder='请输入请假原因'
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
