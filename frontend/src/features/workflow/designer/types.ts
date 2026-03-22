import { type Edge, type Node } from '@xyflow/react'

export type WorkflowNodeKind =
  | 'start'
  | 'approver'
  | 'condition'
  | 'cc'
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

export type WorkflowCcTargetMode = 'USER' | 'ROLE' | 'DEPARTMENT'

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
  approvalPolicy: {
    type: WorkflowApproverApprovalPolicyType
    voteThreshold: number | null
  }
  operations: string[]
  commentRequired: boolean
}

export type WorkflowConditionNodeConfig = {
  defaultEdgeId: string
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
