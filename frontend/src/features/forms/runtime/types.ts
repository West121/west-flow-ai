import { type WorkflowFieldBinding } from '@/features/workflow/designer/types'

export type RuntimeFormComponentProps = {
  value: Record<string, unknown>
  onChange: (value: Record<string, unknown>) => void
  disabled?: boolean
  processFormKey?: string
  processFormVersion?: string
  nodeFormKey?: string
  nodeFormVersion?: string
  fieldBindings?: WorkflowFieldBinding[]
  taskFormData?: Record<string, unknown>
}
