import { type ProcessDefinitionMeta } from '../designer/dsl'
import { type WorkflowEdge, type WorkflowNode } from '../designer/types'

export type WorkflowDesignerCollaborationStatus =
  | 'local'
  | 'connecting'
  | 'connected'
  | 'disconnected'

export type WorkflowDesignerCollaborationMode = 'broadcast' | 'websocket'

export type WorkflowDesignerCollaborationPeer = {
  clientId: number
  userId: string
  displayName: string
  color: string
  selectedNodeId: string | null
  editingNodeId: string | null
  cursor: {
    x: number
    y: number
  } | null
}

export type WorkflowDesignerCollaborationAwarenessState = {
  userId: string
  displayName: string
  color: string
  selectedNodeId: string | null
  editingNodeId: string | null
  cursor: {
    x: number
    y: number
  } | null
}

export type WorkflowDesignerSharedState = {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  definitionMeta: ProcessDefinitionMeta
}
