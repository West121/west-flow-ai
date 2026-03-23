import { useMemo, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { workflowFormulaFunctionHints } from './config'

export type WorkflowFieldOption = {
  fieldKey: string
  label: string
  valueType?: string
}

const fallbackFieldOptions: WorkflowFieldOption[] = [
  { fieldKey: 'amount', label: '金额', valueType: 'number' },
  { fieldKey: 'days', label: '天数', valueType: 'number' },
  { fieldKey: 'reason', label: '原因', valueType: 'string' },
  { fieldKey: 'department', label: '部门', valueType: 'string' },
  { fieldKey: 'departmentId', label: '部门 ID', valueType: 'string' },
  { fieldKey: 'applicantId', label: '申请人 ID', valueType: 'string' },
  { fieldKey: 'applicantName', label: '申请人姓名', valueType: 'string' },
  { fieldKey: 'currentUserId', label: '当前用户 ID', valueType: 'string' },
]

function insertText(
  text: string,
  value: string,
  selectionStart?: number | null,
  selectionEnd?: number | null
) {
  const start = selectionStart ?? value.length
  const end = selectionEnd ?? start
  return value.slice(0, start) + text + value.slice(end)
}

export function WorkflowFieldSelector({
  label,
  description,
  value,
  onChange,
  options,
  placeholder,
}: {
  label: string
  description?: string
  value: string
  onChange: (value: string) => void
  options?: WorkflowFieldOption[]
  placeholder?: string
}) {
  const fieldOptions = options?.length ? options : fallbackFieldOptions
  const datalistId = useMemo(
    () => `workflow-field-options-${label.replace(/\s+/g, '-').toLowerCase()}`,
    [label]
  )

  return (
    <div className='grid gap-2'>
      <Label>{label}</Label>
      {description ? <p className='text-xs text-muted-foreground'>{description}</p> : null}
      <Input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        list={datalistId}
        placeholder={placeholder ?? '请选择或输入字段编码'}
      />
      <datalist id={datalistId}>
        {fieldOptions.map((item) => (
          <option key={item.fieldKey} value={item.fieldKey}>
            {item.label}
          </option>
        ))}
      </datalist>
      <div className='flex flex-wrap gap-2'>
        {fieldOptions.slice(0, 6).map((item) => (
          <Button
            key={item.fieldKey}
            type='button'
            variant='secondary'
            size='sm'
            onClick={() => onChange(item.fieldKey)}
          >
            {item.label}
          </Button>
        ))}
      </div>
    </div>
  )
}

export function WorkflowFormulaEditor({
  label,
  description,
  value,
  onChange,
  fieldOptions,
  placeholder,
}: {
  label: string
  description?: string
  value: string
  onChange: (value: string) => void
  fieldOptions?: WorkflowFieldOption[]
  placeholder?: string
}) {
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)
  const functionHints = workflowFormulaFunctionHints()
  const fields = fieldOptions?.length ? fieldOptions : fallbackFieldOptions

  function insertSnippet(snippet: string) {
    const element = textareaRef.current
    if (!element) {
      onChange(value + snippet)
      return
    }

    const nextValue = insertText(
      snippet,
      value,
      element.selectionStart,
      element.selectionEnd
    )
    onChange(nextValue)
    requestAnimationFrame(() => {
      const cursor = (element.selectionStart ?? value.length) + snippet.length
      element.setSelectionRange(cursor, cursor)
      element.focus()
    })
  }

  return (
    <div className='grid gap-2'>
      <Label>{label}</Label>
      {description ? <p className='text-xs text-muted-foreground'>{description}</p> : null}
      <Textarea
        ref={textareaRef}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        rows={4}
        placeholder={placeholder ?? '请输入受控公式表达式，例如：ifElse(amount > 10000, "A", "B")'}
        className='font-mono text-xs'
      />
      <div className='flex flex-wrap gap-2'>
        {functionHints.map((name) => (
          <Button
            key={name}
            type='button'
            variant='secondary'
            size='sm'
            onClick={() =>
              insertSnippet(
                name === 'isBlank'
                  ? `${name}()`
                  : `${name}(<field>, <arg2>${name === 'ifElse' ? ', <arg3>' : ''})`
              )
            }
          >
            {name}()
          </Button>
        ))}
      </div>
      <div className='flex flex-wrap gap-2'>
        {fields.slice(0, 6).map((item) => (
          <Button
            key={item.fieldKey}
            type='button'
            variant='outline'
            size='sm'
            onClick={() => insertSnippet(item.fieldKey)}
          >
            {item.label}
          </Button>
        ))}
      </div>
    </div>
  )
}
