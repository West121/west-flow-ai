import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import {
  findRuntimeFormRegistration,
  resolveRuntimeFormComponent,
  type RuntimeFormComponent,
} from './form-component-registry'
import { type RuntimeFormComponentProps } from './types'

export type ProcessFormRendererProps = {
  processFormKey: string
  processFormVersion: string
  value: Record<string, unknown>
  onChange: (value: Record<string, unknown>) => void
  disabled?: boolean
  userDisplayNames?: Record<string, string> | null
}

// 流程默认表单的运行态入口，缺少注册时直接提示配置问题。
export function ProcessFormRenderer({
  processFormKey,
  processFormVersion,
  value,
  onChange,
  disabled,
  userDisplayNames,
}: ProcessFormRendererProps) {
  const registration = findRuntimeFormRegistration(
    processFormKey,
    processFormVersion,
    'PROCESS_FORM'
  )
  const Component = resolveRuntimeFormComponent(processFormKey) as
    | RuntimeFormComponent
    | undefined

  if (!registration || !Component) {
    return (
      <Alert variant='destructive'>
        <AlertTitle>表单组件未注册</AlertTitle>
        <AlertDescription>
          流程默认表单 {processFormKey} · {processFormVersion} 对应的代码组件还没有接入运行态注册中心。
        </AlertDescription>
      </Alert>
    )
  }

  const props: RuntimeFormComponentProps = {
    value,
    onChange,
    disabled,
    userDisplayNames,
    processFormKey,
    processFormVersion,
  }

  return <Component {...props} />
}
