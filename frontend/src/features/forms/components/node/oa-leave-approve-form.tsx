import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { type RuntimeFormComponentProps } from '@/features/forms/runtime/types'

function readDecision(value: unknown) {
  if (value === false || value === 'REJECT') {
    return 'REJECT'
  }

  return 'APPROVE'
}

// OA 审批节点表单只负责展示审批结果和意见输入。
export function OALeaveApproveForm({
  value,
  onChange,
  disabled,
  nodeFormKey,
  nodeFormVersion,
  fieldBindings,
}: RuntimeFormComponentProps) {
  const decisionValue = readDecision(value.approved)
  const commentValue = typeof value.comment === 'string' ? value.comment : ''

  return (
    <div className='space-y-4'>
      <div className='space-y-1'>
        <p className='text-sm font-medium'>OA 审批节点表单</p>
        <p className='text-xs text-muted-foreground'>
          节点表单编码 {nodeFormKey} · 版本 {nodeFormVersion}
        </p>
        {fieldBindings?.length ? (
          <p className='text-xs text-muted-foreground'>
            字段映射：
            {fieldBindings
              .map((binding) => `${binding.sourceFieldKey} -> ${binding.targetFieldKey}`)
              .join('，')}
          </p>
        ) : null}
      </div>

      <div className='space-y-2'>
        <Label>审批结果</Label>
        <RadioGroup
          className='grid gap-3'
          value={decisionValue}
          disabled={disabled}
          onValueChange={(nextValue) => {
            onChange({
              ...value,
              approved: nextValue === 'APPROVE',
            })
          }}
        >
          <div className='flex items-center gap-2'>
            <RadioGroupItem value='APPROVE' id='approval-approve' />
            <Label htmlFor='approval-approve' className='font-normal'>
              同意通过
            </Label>
          </div>
          <div className='flex items-center gap-2'>
            <RadioGroupItem value='REJECT' id='approval-reject' />
            <Label htmlFor='approval-reject' className='font-normal'>
              驳回
            </Label>
          </div>
        </RadioGroup>
      </div>

      <div className='space-y-2'>
        <Label htmlFor='approval-comment'>审批意见</Label>
        <Textarea
          id='approval-comment'
          disabled={disabled}
          className='min-h-28'
          value={commentValue}
          onChange={(event) => {
            onChange({
              ...value,
              comment: event.target.value,
            })
          }}
        />
      </div>
    </div>
  )
}
