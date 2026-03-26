import { ClipboardList, FileText } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { type RuntimeFormComponentProps } from '@/features/forms/runtime/types'
import { RuntimeFormSectionTitle } from './runtime-form-section-title'

function readText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

export function OACommonStartForm({
  value,
  onChange,
  disabled,
  processFormKey,
  processFormVersion,
}: RuntimeFormComponentProps) {
  const titleValue = readText(value.title)
  const contentValue = readText(value.content)

  return (
    <div className='space-y-5'>
      <RuntimeFormSectionTitle icon={ClipboardList} title='申请信息' />

      <div className='space-y-2'>
        <Label htmlFor='common-title'>申请标题</Label>
        <Input
          id='common-title'
          placeholder='例如：资产借用'
          disabled={disabled}
          value={titleValue}
          onChange={(event) => {
            onChange({
              ...value,
              title: event.target.value,
            })
          }}
        />
      </div>

      <RuntimeFormSectionTitle icon={FileText} title='申请内容' />

      <div className='space-y-2'>
        <Label htmlFor='common-content'>申请内容</Label>
        <Textarea
          id='common-content'
          className='min-h-28'
          placeholder='请输入申请内容'
          disabled={disabled}
          value={contentValue}
          onChange={(event) => {
            onChange({
              ...value,
              content: event.target.value,
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
