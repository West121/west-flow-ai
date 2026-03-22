import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import {
  findRuntimeFormRegistration,
  resolveRuntimeFormComponent,
  type RuntimeFormComponent,
} from './form-component-registry'
import { type RuntimeFormComponentProps } from './types'

export type NodeFormRendererProps = {
  nodeFormKey: string
  nodeFormVersion: string
  value: Record<string, unknown>
  onChange: (value: Record<string, unknown>) => void
  fieldBindings?: RuntimeFormComponentProps['fieldBindings']
  taskFormData?: Record<string, unknown>
  disabled?: boolean
}

// 节点覆盖表单的运行态入口，优先使用节点自身值，回退到任务表单数据。
export function NodeFormRenderer({
  nodeFormKey,
  nodeFormVersion,
  value,
  onChange,
  fieldBindings,
  taskFormData,
  disabled,
}: NodeFormRendererProps) {
  const registration = findRuntimeFormRegistration(
    nodeFormKey,
    nodeFormVersion,
    'NODE_FORM'
  )
  const Component = resolveRuntimeFormComponent(nodeFormKey) as
    | RuntimeFormComponent
    | undefined

  if (!registration || !Component) {
    return (
      <Alert variant='destructive'>
        <AlertTitle>节点表单组件未注册</AlertTitle>
        <AlertDescription>
          节点表单 {nodeFormKey} · {nodeFormVersion} 对应的代码组件还没有接入运行态注册中心。
        </AlertDescription>
      </Alert>
    )
  }

  const props: RuntimeFormComponentProps = {
    value: Object.keys(value).length ? value : taskFormData ?? {},
    onChange,
    disabled,
    nodeFormKey,
    nodeFormVersion,
    fieldBindings,
    taskFormData,
  }

  return <Component {...props} />
}
