import { type WorkflowFieldBinding } from '@/features/workflow/designer/types'

// 运行时表单渲染器统一使用这组参数描述当前值、交互回调和流程上下文。
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
