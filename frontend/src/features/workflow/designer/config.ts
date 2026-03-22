import {
  type WorkflowApproverNodeConfig,
  type WorkflowCcNodeConfig,
  type WorkflowConditionNodeConfig,
  type WorkflowEdgeConditionType,
  type WorkflowNodeConfigMap,
  type WorkflowNodeKind,
  type WorkflowNodeTone,
  type WorkflowStartNodeConfig,
} from './types'

export type WorkflowEdgeCondition = {
  type: WorkflowEdgeConditionType
  expression: string
}

export type WorkflowNodeConfigRecord = {
  start: WorkflowStartNodeConfig
  approver: WorkflowApproverNodeConfig
  condition: WorkflowConditionNodeConfig
  cc: WorkflowCcNodeConfig
  parallel: Record<string, never>
  end: Record<string, never>
}

const defaultApproverConfig: WorkflowApproverNodeConfig = {
  assignment: {
    mode: 'USER',
    userIds: ['usr_002'],
    roleCodes: [],
    departmentRef: '',
    formFieldKey: '',
  },
  approvalPolicy: {
    type: 'SEQUENTIAL',
    voteThreshold: null,
  },
  operations: ['APPROVE', 'REJECT', 'RETURN'],
  commentRequired: false,
}

const defaultCcConfig: WorkflowCcNodeConfig = {
  targets: {
    mode: 'USER',
    userIds: ['usr_003'],
    roleCodes: [],
    departmentRef: '',
  },
  readRequired: false,
}

const defaultConditionConfig: WorkflowConditionNodeConfig = {
  defaultEdgeId: '',
}

const defaultStartConfig: WorkflowStartNodeConfig = {
  initiatorEditable: true,
}

const emptyConfig = {}

export function defaultNodeConfig<K extends WorkflowNodeKind>(
  kind: K
): WorkflowNodeConfigMap[K] {
  switch (kind) {
    case 'start':
      return defaultStartConfig as WorkflowNodeConfigMap[K]
    case 'approver':
      return defaultApproverConfig as WorkflowNodeConfigMap[K]
    case 'condition':
      return defaultConditionConfig as WorkflowNodeConfigMap[K]
    case 'cc':
      return defaultCcConfig as WorkflowNodeConfigMap[K]
    case 'parallel':
    case 'end':
      return emptyConfig as WorkflowNodeConfigMap[K]
  }
}

export function toneForKind(kind: WorkflowNodeKind): WorkflowNodeTone {
  switch (kind) {
    case 'start':
      return 'success'
    case 'approver':
      return 'brand'
    case 'condition':
      return 'warning'
    default:
      return 'neutral'
  }
}

export function descriptionForKind(kind: WorkflowNodeKind) {
  switch (kind) {
    case 'start':
      return '流程发起与表单提交入口'
    case 'approver':
      return '审批节点'
    case 'condition':
      return '条件分支节点'
    case 'cc':
      return '抄送节点'
    case 'parallel':
      return '并行编排节点'
    case 'end':
      return '流程结束节点'
  }
}

export function labelForKind(kind: WorkflowNodeKind, fallback: string) {
  switch (kind) {
    case 'start':
      return '开始'
    case 'approver':
      return '审批'
    case 'condition':
      return '条件'
    case 'cc':
      return '抄送'
    case 'parallel':
      return '并行'
    case 'end':
      return '结束'
    default:
      return fallback
  }
}

export function normalizeNodeConfig<K extends WorkflowNodeKind>(
  kind: K,
  config: unknown
): WorkflowNodeConfigMap[K] {
  if (kind === 'start') {
    const value = config as Partial<WorkflowStartNodeConfig> | null | undefined
    return {
      initiatorEditable:
        typeof value?.initiatorEditable === 'boolean'
          ? value.initiatorEditable
          : defaultStartConfig.initiatorEditable,
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'approver') {
    const value = config as Partial<WorkflowApproverNodeConfig> | null | undefined
    return {
      assignment: {
        mode: value?.assignment?.mode ?? defaultApproverConfig.assignment.mode,
        userIds: Array.isArray(value?.assignment?.userIds)
          ? value.assignment.userIds.map(String)
          : [...defaultApproverConfig.assignment.userIds],
        roleCodes: Array.isArray(value?.assignment?.roleCodes)
          ? value.assignment.roleCodes.map(String)
          : [...defaultApproverConfig.assignment.roleCodes],
        departmentRef: value?.assignment?.departmentRef
          ? String(value.assignment.departmentRef)
          : defaultApproverConfig.assignment.departmentRef,
        formFieldKey: value?.assignment?.formFieldKey
          ? String(value.assignment.formFieldKey)
          : defaultApproverConfig.assignment.formFieldKey,
      },
      approvalPolicy: {
        type: value?.approvalPolicy?.type ?? defaultApproverConfig.approvalPolicy.type,
        voteThreshold:
          typeof value?.approvalPolicy?.voteThreshold === 'number'
            ? value.approvalPolicy.voteThreshold
            : value?.approvalPolicy?.voteThreshold === null
              ? null
              : defaultApproverConfig.approvalPolicy.voteThreshold,
      },
      operations: Array.isArray(value?.operations) && value.operations.length > 0
        ? value.operations.map(String)
        : [...defaultApproverConfig.operations],
      commentRequired: Boolean(value?.commentRequired ?? defaultApproverConfig.commentRequired),
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'condition') {
    const value = config as Partial<WorkflowConditionNodeConfig> | null | undefined
    return {
      defaultEdgeId: value?.defaultEdgeId ? String(value.defaultEdgeId) : '',
    } as WorkflowNodeConfigMap[K]
  }

  if (kind === 'cc') {
    const value = config as Partial<WorkflowCcNodeConfig> | null | undefined
    return {
      targets: {
        mode: value?.targets?.mode ?? defaultCcConfig.targets.mode,
        userIds: Array.isArray(value?.targets?.userIds)
          ? value.targets.userIds.map(String)
          : [...defaultCcConfig.targets.userIds],
        roleCodes: Array.isArray(value?.targets?.roleCodes)
          ? value.targets.roleCodes.map(String)
          : [...defaultCcConfig.targets.roleCodes],
        departmentRef: value?.targets?.departmentRef
          ? String(value.targets.departmentRef)
          : defaultCcConfig.targets.departmentRef,
      },
      readRequired: Boolean(value?.readRequired ?? defaultCcConfig.readRequired),
    } as WorkflowNodeConfigMap[K]
  }

  return emptyConfig as WorkflowNodeConfigMap[K]
}

export function parseListValue(value: string) {
  return value
    .split(/[\n,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export function joinListValue(values: string[]) {
  return values.join(', ')
}

export function edgeConditionFromConfig(
  condition: unknown
): WorkflowEdgeCondition | undefined {
  const value = condition as Partial<WorkflowEdgeCondition> | null | undefined
  if (!value?.expression) {
    return undefined
  }
  return {
    type: value.type === 'EXPRESSION' ? value.type : 'EXPRESSION',
    expression: String(value.expression),
  }
}

export function normalizeEdgeCondition(condition: unknown) {
  return edgeConditionFromConfig(condition)
}

export function buildConditionFormDefaults(edgeId: string, expression = '', label = '') {
  return {
    edgeId,
    label,
    conditionExpression: expression,
  }
}
