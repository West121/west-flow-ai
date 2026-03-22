import { type ComponentType } from 'react'
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
  component: RuntimeFormComponent
}

export const runtimeFormRegistrations: RuntimeFormRegistration[] = [
  {
    kind: 'PROCESS_FORM',
    processKey: 'oa_leave',
    formKey: 'oa-leave-start-form',
    formVersion: '1.0.0',
    title: 'OA 请假发起表单',
    description: '流程发起默认表单',
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

function sameKind(
  registration: RuntimeFormRegistration,
  kind?: RuntimeFormKind
) {
  return kind ? registration.kind === kind : true
}

export function listRuntimeFormRegistrations(kind?: RuntimeFormKind) {
  return runtimeFormRegistrations.filter((registration) =>
    sameKind(registration, kind)
  )
}

export function listRuntimeFormKeys(kind?: RuntimeFormKind) {
  return Array.from(
    new Set(listRuntimeFormRegistrations(kind).map((registration) => registration.formKey))
  )
}

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

export function findProcessRuntimeFormByProcessKey(processKey: string) {
  return (
    runtimeFormRegistrations.find(
      (registration) =>
        registration.kind === 'PROCESS_FORM' &&
        registration.processKey === processKey
    ) ?? null
  )
}

export function resolveRuntimeFormComponent(formKey: string) {
  return runtimeFormRegistrations.find(
    (registration) => registration.formKey === formKey
  )?.component
}
