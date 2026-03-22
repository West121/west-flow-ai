import { useMemo } from 'react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  listRuntimeFormKeys,
  listRuntimeFormRegistrations,
  listRuntimeFormVersions,
  type RuntimeFormKind,
  type RuntimeFormRegistration,
} from '@/features/forms/runtime/form-component-registry'

type FormSelectionValue = {
  formKey: string
  formVersion: string
}

type RuntimeFormSelectorProps = {
  label: string
  description?: string
  scope: RuntimeFormKind
  emptyMessage: string
  keyLabel: string
  versionLabel: string
  statusLabel: string
  value: FormSelectionValue | null
  onChange: (selection: FormSelectionValue | null) => void
}

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

function RuntimeFormSelector({
  label,
  description,
  scope,
  emptyMessage,
  keyLabel,
  versionLabel,
  statusLabel,
  value,
  onChange,
}: RuntimeFormSelectorProps) {
  const registrations = useMemo(
    () => listRuntimeFormRegistrations(scope),
    [scope]
  )
  const formKeys = useMemo(() => listRuntimeFormKeys(scope), [scope])
  const selectedKey = value?.formKey ?? formKeys[0] ?? ''
  const selectedVersions = useMemo(
    () => listRuntimeFormVersions(selectedKey, scope),
    [scope, selectedKey]
  )
  const selectedVersion =
    value?.formVersion ?? selectedVersions[0]?.formVersion ?? ''
  const selectedRegistration =
    findSelectedRegistration(registrations, value) ??
    selectedVersions.find(
      (registration) => registration.formVersion === selectedVersion
    ) ??
    null

  return (
    <Card>
      <CardHeader>
        <CardTitle>{label}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent className='grid gap-4'>
        <div className='grid gap-4 md:grid-cols-2'>
          <div className='grid gap-2'>
            <Label htmlFor={`${scope}-form-key`}>{keyLabel}</Label>
            <select
              id={`${scope}-form-key`}
              aria-label={keyLabel}
              className='h-10 rounded-md border border-input bg-background px-3 py-2 text-sm'
              value={selectedKey}
              onChange={(event) => {
                const nextKey = event.target.value
                const nextVersions = listRuntimeFormVersions(nextKey, scope)
                onChange(
                  nextKey && nextVersions[0]
                    ? {
                        formKey: nextKey,
                        formVersion: nextVersions[0].formVersion,
                      }
                    : null
                )
              }}
            >
              {formKeys.length > 0 ? null : <option value=''>{emptyMessage}</option>}
              {formKeys.map((formKey) => {
                const record = registrations.find(
                  (registration) => registration.formKey === formKey
                )
                return (
                  <option key={formKey} value={formKey}>
                    {record?.title ?? formKey} ({formKey})
                  </option>
                )
              })}
            </select>
          </div>

          <div className='grid gap-2'>
            <Label htmlFor={`${scope}-form-version`}>{versionLabel}</Label>
            <select
              id={`${scope}-form-version`}
              aria-label={versionLabel}
              className='h-10 rounded-md border border-input bg-background px-3 py-2 text-sm'
              value={selectedVersion}
              onChange={(event) => {
                const nextVersion = event.target.value
                onChange(
                  selectedKey && nextVersion
                    ? {
                        formKey: selectedKey,
                        formVersion: nextVersion,
                      }
                    : null
                )
              }}
            >
              {selectedVersions.length > 0 ? null : (
                <option value=''>请先选择表单编码</option>
              )}
              {selectedVersions.map((registration) => (
                <option key={registration.formVersion} value={registration.formVersion}>
                  {registration.formVersion}
                </option>
              ))}
            </select>
          </div>
        </div>

        {selectedRegistration ? (
          <div className='grid gap-3 rounded-2xl border bg-muted/20 p-4 text-sm'>
            <div className='flex flex-wrap gap-2 text-xs text-muted-foreground'>
              <span>{statusLabel}</span>
              <span>流程：{selectedRegistration.processKey}</span>
              <span>表单：{selectedRegistration.formKey}</span>
              <span>版本：{selectedRegistration.formVersion}</span>
            </div>
            <p className='font-medium'>{selectedRegistration.title}</p>
            <p className='text-xs text-muted-foreground'>
              {selectedRegistration.description}
            </p>
          </div>
        ) : (
          <div className='rounded-2xl border border-dashed p-4 text-sm text-muted-foreground'>
            {emptyMessage}
          </div>
        )}
      </CardContent>
    </Card>
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

export function ProcessFormSelector({
  label,
  description,
  value,
  onChange,
}: {
  label: string
  description?: string
  value: ProcessFormSelection | null
  onChange: (selection: ProcessFormSelection | null) => void
}) {
  return (
    <RuntimeFormSelector
      label={label}
      description={description}
      scope='PROCESS_FORM'
      emptyMessage='暂无可选流程表单'
      keyLabel='流程默认表单编码'
      versionLabel='流程默认表单版本'
      statusLabel='流程表单已注册'
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

export function NodeFormSelector({
  label,
  description,
  value,
  onChange,
}: {
  label: string
  description?: string
  value: NodeFormSelection | null
  onChange: (selection: NodeFormSelection | null) => void
}) {
  return (
    <RuntimeFormSelector
      label={label}
      description={description}
      scope='NODE_FORM'
      emptyMessage='暂无可选节点表单'
      keyLabel='节点表单编码'
      versionLabel='节点表单版本'
      statusLabel='节点表单已注册'
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
