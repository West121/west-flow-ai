import { type ComponentType } from 'react'
import { type WorkflowProcessFormField } from '@/features/workflow/designer/types'
import {
  OALeaveApproveForm,
} from '@/features/forms/components/node/oa-leave-approve-form'
import {
  OALeaveStartForm,
} from '@/features/forms/components/process/oa-leave-start-form'
import { type RuntimeFormComponentProps } from './types'

export type RuntimeFormComponent = ComponentType<RuntimeFormComponentProps>
export type RuntimeFormKind = 'PROCESS_FORM' | 'NODE_FORM'

export type RuntimeFormRegistration = {
  kind: RuntimeFormKind
  processKey: string
  formKey: string
  formVersion: string
  title: string
  description: string
  fields?: WorkflowProcessFormField[]
  component: RuntimeFormComponent
}

const leaveProcessFormFields: WorkflowProcessFormField[] = [
  { fieldKey: 'leaveType', label: '请假类型', valueType: 'string' },
  { fieldKey: 'days', label: '请假天数', valueType: 'number' },
  { fieldKey: 'reason', label: '请假原因', valueType: 'string' },
  { fieldKey: 'urgent', label: '是否紧急', valueType: 'boolean' },
  { fieldKey: 'managerUserId', label: '直属负责人', valueType: 'string' },
]

// 运行态表单注册表，页面根据表单 key 和版本找到对应实现。
export const runtimeFormRegistrations: RuntimeFormRegistration[] = [
  {
    kind: 'PROCESS_FORM',
    processKey: 'oa_leave',
    formKey: 'oa-leave-start-form',
    formVersion: '1.0.0',
    title: 'OA 请假发起表单',
    description: '流程发起默认表单',
    fields: leaveProcessFormFields,
    component: OALeaveStartForm,
  },
  {
    kind: 'PROCESS_FORM',
    processKey: 'oa_leave',
    formKey: 'oa-leave-start-form',
    formVersion: '1.1.0',
    title: 'OA 请假发起表单',
    description: '流程发起默认表单',
    fields: leaveProcessFormFields,
    component: OALeaveStartForm,
  },
  {
    kind: 'NODE_FORM',
    processKey: 'oa_leave',
    formKey: 'oa-leave-approve-form',
    formVersion: '1.0.0',
    title: 'OA 请假审批表单',
    description: '审批节点覆盖表单',
    component: OALeaveApproveForm,
  },
]

// 先按表单类型过滤，再做 key/version 查找，保持查询逻辑简单。
function sameKind(
  registration: RuntimeFormRegistration,
  kind?: RuntimeFormKind
) {
  return kind ? registration.kind === kind : true
}

// 列出指定类型的全部注册项，供下拉选择和版本列表复用。
export function listRuntimeFormRegistrations(kind?: RuntimeFormKind) {
  return runtimeFormRegistrations.filter((registration) =>
    sameKind(registration, kind)
  )
}

// 读取当前可用的表单 key 列表。
export function listRuntimeFormKeys(kind?: RuntimeFormKind) {
  return Array.from(
    new Set(listRuntimeFormRegistrations(kind).map((registration) => registration.formKey))
  )
}

// 按 key 列出可用版本，方便表单选择器展示。
export function listRuntimeFormVersions(
  formKey: string,
  kind?: RuntimeFormKind
) {
  return listRuntimeFormRegistrations(kind)
    .filter((registration) => registration.formKey === formKey)
    .slice()
    .sort((left, right) =>
      left.formVersion.localeCompare(right.formVersion, 'zh-Hans-CN', {
        numeric: true,
        sensitivity: 'base',
      })
    )
}

// 精确定位某个表单注册项。
export function findRuntimeFormRegistration(
  formKey: string,
  formVersion?: string,
  kind?: RuntimeFormKind
) {
  return (
    listRuntimeFormRegistrations(kind).find(
      (registration) =>
        registration.formKey === formKey &&
        (formVersion ? registration.formVersion === formVersion : true)
    ) ?? null
  )
}

// 根据流程编码找默认发起表单。
export function findProcessRuntimeFormByProcessKey(processKey: string) {
  return (
    runtimeFormRegistrations.find(
      (registration) =>
        registration.kind === 'PROCESS_FORM' &&
        registration.processKey === processKey
    ) ?? null
  )
}

export function resolveRuntimeProcessFormFields(processKey: string) {
  return findProcessRuntimeFormByProcessKey(processKey)?.fields ?? []
}

// 只按表单 key 解析组件实现，渲染器会在上层先校验注册项。
export function resolveRuntimeFormComponent(formKey: string) {
  return runtimeFormRegistrations.find(
    (registration) => registration.formKey === formKey
  )?.component
}
