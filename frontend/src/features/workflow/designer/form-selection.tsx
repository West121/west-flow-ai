import { useMemo } from 'react'
import {
  listRuntimeFormRegistrations,
  type RuntimeFormKind,
  type RuntimeFormRegistration,
} from '@/features/forms/runtime/form-component-registry'

type FormSelectionValue = {
  formKey: string
  formVersion: string
}

type RuntimeFormSelectorProps = {
  label: string
  scope: RuntimeFormKind
  emptyMessage: string
  value: FormSelectionValue | null
  onChange: (selection: FormSelectionValue | null) => void
}

// 先按当前值匹配注册项，避免下拉框状态漂移。
function findSelectedRegistration(
  registrations: RuntimeFormRegistration[],
  value: FormSelectionValue | null
) {
  if (!value) {
    return null
  }

  return (
    registrations.find(
      (registration) =>
        registration.formKey === value.formKey &&
        registration.formVersion === value.formVersion
    ) ?? null
  )
}

// 表单选择器把 key/version 的选择状态集中在一起。
function RuntimeFormSelector({
  label,
  scope,
  emptyMessage,
  value,
  onChange,
}: RuntimeFormSelectorProps) {
  const registrations = useMemo(
    () => listRuntimeFormRegistrations(scope),
    [scope]
  )
  const selectedRegistration = findSelectedRegistration(registrations, value)

  return (
    <div className='space-y-2'>
      <label
        htmlFor={`${scope}-form-registration`}
        className='text-sm font-medium'
      >
        {label}
      </label>
      <select
        id={`${scope}-form-registration`}
        aria-label={label}
        className='h-10 rounded-md border border-input bg-background px-3 py-2 text-sm'
        value={
          selectedRegistration
            ? `${selectedRegistration.formKey}@@${selectedRegistration.formVersion}`
            : ''
        }
        onChange={(event) => {
          const nextValue = event.target.value
          const nextRegistration = registrations.find(
            (registration) =>
              `${registration.formKey}@@${registration.formVersion}` === nextValue
          )

          onChange(
            nextRegistration
              ? {
                  formKey: nextRegistration.formKey,
                  formVersion: nextRegistration.formVersion,
                }
              : null
          )
        }}
      >
        <option value=''>
          {registrations.length > 0 ? '请选择表单' : emptyMessage}
        </option>
        {registrations.map((registration) => (
          <option
            key={`${registration.formKey}@@${registration.formVersion}`}
            value={`${registration.formKey}@@${registration.formVersion}`}
          >
            {registration.title} · {registration.formVersion}
          </option>
        ))}
      </select>
      {selectedRegistration ? (
        <div className='rounded-md bg-muted/35 px-3 py-2 text-xs text-muted-foreground'>
          编码：{selectedRegistration.formKey}
        </div>
      ) : null}
    </div>
  )
}

export type ProcessFormSelection = {
  processFormKey: string
  processFormVersion: string
}

export type NodeFormSelection = {
  nodeFormKey: string
  nodeFormVersion: string
}

// 流程默认表单选择器只处理流程级表单注册。
export function ProcessFormSelector({
  label,
  value,
  onChange,
}: {
  label: string
  value: ProcessFormSelection | null
  onChange: (selection: ProcessFormSelection | null) => void
}) {
  return (
    <RuntimeFormSelector
      label={label}
      scope='PROCESS_FORM'
      emptyMessage='暂无可选流程表单'
      value={
        value
          ? {
              formKey: value.processFormKey,
              formVersion: value.processFormVersion,
            }
          : null
      }
      onChange={(selection) =>
        onChange(
          selection
            ? {
                processFormKey: selection.formKey,
                processFormVersion: selection.formVersion,
              }
            : null
        )
      }
    />
  )
}

// 节点表单选择器只处理节点级表单注册。
export function NodeFormSelector({
  label,
  value,
  onChange,
}: {
  label: string
  value: NodeFormSelection | null
  onChange: (selection: NodeFormSelection | null) => void
}) {
  return (
    <RuntimeFormSelector
      label={label}
      scope='NODE_FORM'
      emptyMessage='暂无可选节点表单'
      value={
        value
          ? {
              formKey: value.nodeFormKey,
              formVersion: value.nodeFormVersion,
            }
          : null
      }
      onChange={(selection) =>
        onChange(
          selection
            ? {
                nodeFormKey: selection.formKey,
                nodeFormVersion: selection.formVersion,
              }
            : null
        )
      }
    />
  )
}
