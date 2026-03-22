import { type Edge, type Node } from '@xyflow/react'

export type WorkflowNodeKind =
  | 'start'
  | 'approver'
  | 'condition'
  | 'cc'
  | 'parallel'
  | 'end'

export type WorkflowNodeTone = 'brand' | 'success' | 'warning' | 'neutral'

export type WorkflowNodeData = {
  kind: WorkflowNodeKind
  label: string
  description: string
  tone: WorkflowNodeTone
}

export type WorkflowNode = Node<WorkflowNodeData, 'workflow'>
export type WorkflowEdge = Edge

export type WorkflowSnapshot = {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  selectedNodeId: string | null
}

export type WorkflowHelperLines = {
  vertical: number | null
  horizontal: number | null
}
