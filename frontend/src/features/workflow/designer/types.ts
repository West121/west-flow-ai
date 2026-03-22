import { type Edge, type Node } from '@xyflow/react'

export type WorkflowNodeKind =
  | 'start'
  | 'approver'
  | 'condition'
  | 'cc'
  | 'timer'
  | 'trigger'
  | 'parallel'
  | 'end'

export type WorkflowNodeTone = 'brand' | 'success' | 'warning' | 'neutral'

export type WorkflowApproverAssignmentMode =
  | 'USER'
  | 'ROLE'
  | 'DEPARTMENT'
  | 'DEPARTMENT_AND_CHILDREN'
  | 'FORM_FIELD'

export type WorkflowApproverApprovalPolicyType =
  | 'SEQUENTIAL'
  | 'PARALLEL'
  | 'VOTE'

export type WorkflowTimeoutApprovalAction = 'APPROVE' | 'REJECT'
export type WorkflowReminderChannel =
  | 'IN_APP'
  | 'EMAIL'
  | 'WEBHOOK'
  | 'SMS'
  | 'WECHAT'
  | 'DINGTALK'
export type WorkflowTimerScheduleType = 'ABSOLUTE_TIME' | 'RELATIVE_TO_ARRIVAL'
export type WorkflowTriggerMode = 'IMMEDIATE' | 'SCHEDULED'

export type WorkflowCcTargetMode = 'USER' | 'ROLE' | 'DEPARTMENT'
export type WorkflowConditionExpressionMode = 'EXPRESSION' | 'FIELD_COMPARE'
export type WorkflowFormFieldValueType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'date'
  | 'datetime'

export type WorkflowFieldBindingSource = 'PROCESS_FORM' | 'NODE_FORM'

export type WorkflowFieldBinding = {
  source: WorkflowFieldBindingSource
  sourceFieldKey: string
  targetFieldKey: string
}

export type WorkflowProcessFormField = {
  fieldKey: string
  label: string
  valueType: WorkflowFormFieldValueType
}

export type WorkflowEdgeConditionType = 'EXPRESSION'

export type WorkflowStartNodeConfig = {
  initiatorEditable: boolean
}

export type WorkflowApproverNodeConfig = {
  assignment: {
    mode: WorkflowApproverAssignmentMode
    userIds: string[]
    roleCodes: string[]
    departmentRef: string
    formFieldKey: string
  }
  nodeFormKey?: string
  nodeFormVersion?: string
  fieldBindings?: WorkflowFieldBinding[]
  approvalPolicy: {
    type: WorkflowApproverApprovalPolicyType
    voteThreshold: number | null
  }
  timeoutPolicy: {
    enabled: boolean
    durationMinutes: number | null
    action: WorkflowTimeoutApprovalAction
  }
  reminderPolicy: {
    enabled: boolean
    firstReminderAfterMinutes: number | null
    repeatIntervalMinutes: number | null
    maxTimes: number | null
    channels: WorkflowReminderChannel[]
  }
  operations: string[]
  commentRequired: boolean
}

export type WorkflowTimerNodeConfig = {
  scheduleType: WorkflowTimerScheduleType
  runAt: string
  delayMinutes: number | null
  comment: string
}

export type WorkflowTriggerNodeConfig = {
  triggerMode: WorkflowTriggerMode
  scheduleType: WorkflowTimerScheduleType
  runAt: string
  delayMinutes: number | null
  triggerKey: string
  retryTimes: number | null
  retryIntervalMinutes: number | null
  payloadTemplate: string
}

export type WorkflowConditionNodeConfig = {
  defaultEdgeId: string
  expressionMode?: WorkflowConditionExpressionMode
  expressionFieldKey?: string
}

export type WorkflowCcNodeConfig = {
  targets: {
    mode: WorkflowCcTargetMode
    userIds: string[]
    roleCodes: string[]
    departmentRef: string
  }
  readRequired: boolean
}

export type WorkflowNodeConfigMap = {
  start: WorkflowStartNodeConfig
  approver: WorkflowApproverNodeConfig
  condition: WorkflowConditionNodeConfig
  cc: WorkflowCcNodeConfig
  timer: WorkflowTimerNodeConfig
  trigger: WorkflowTriggerNodeConfig
  parallel: Record<string, never>
  end: Record<string, never>
}

export type WorkflowNodeData<K extends WorkflowNodeKind = WorkflowNodeKind> = {
  kind: K
  label: string
  description: string
  tone: WorkflowNodeTone
  config: WorkflowNodeConfigMap[K]
}

export type WorkflowNode = Node<WorkflowNodeData, 'workflow'>
export type WorkflowEdgeData = {
  condition?: {
    type: WorkflowEdgeConditionType
    expression: string
  }
}

export type WorkflowEdge = Edge<WorkflowEdgeData>

export type WorkflowSnapshot = {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  selectedNodeId: string | null
}

export type WorkflowHelperLines = {
  vertical: number | null
  horizontal: number | null
}
