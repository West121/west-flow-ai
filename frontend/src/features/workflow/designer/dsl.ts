import {
  descriptionForKind,
  labelForKind,
  normalizeEdgeCondition,
  normalizeNodeConfig,
} from './config'
import { type WorkflowSnapshot } from './types'

export type ProcessDefinitionMeta = {
  processKey: string
  processName: string
  category: string
  formKey: string
  formVersion: string
}

export type ProcessDefinitionDslNodeType =
  | 'start'
  | 'approver'
  | 'cc'
  | 'condition'
  | 'parallel_split'
  | 'parallel_join'
  | 'end'

export type ProcessDefinitionDslPayload = {
  dslVersion: string
  processKey: string
  processName: string
  category: string
  formKey: string
  formVersion: string
  settings: {
    allowWithdraw: boolean
    allowUrge: boolean
    allowTransfer: boolean
  }
  nodes: Array<{
    id: string
    type: ProcessDefinitionDslNodeType
    name: string
    description: string
    position: {
      x: number
      y: number
    }
    config: Record<string, unknown>
    ui: {
      width: number
      height: number
    }
  }>
  edges: Array<{
    id: string
    source: string
    target: string
    priority: number
    label: string
    condition?: {
      type: string
      expression: string
    }
  }>
}

export type ProcessDefinitionDetailResponse = {
  processDefinitionId: string
  processKey: string
  processName: string
  category: string
  version: number
  status: 'DRAFT' | 'PUBLISHED'
  createdAt: string
  updatedAt: string
  dsl: ProcessDefinitionDslPayload
  bpmnXml: string
}

const DEFAULT_NODE_WIDTH = 220
const DEFAULT_NODE_HEIGHT = 96

function nodeTypeFor(kind: string): ProcessDefinitionDslNodeType {
  switch (kind) {
    case 'start':
    case 'approver':
    case 'cc':
    case 'condition':
    case 'end':
      return kind
    case 'parallel':
      return 'parallel_split'
    default:
      return 'approver'
  }
}

function nodeKindFor(type: ProcessDefinitionDslNodeType) {
  switch (type) {
    case 'parallel_split':
    case 'parallel_join':
      return 'parallel'
    default:
      return type
  }
}

function toneFor(type: ProcessDefinitionDslNodeType) {
  switch (type) {
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

export function workflowSnapshotToProcessDefinitionDsl(
  snapshot: WorkflowSnapshot,
  meta: ProcessDefinitionMeta
): ProcessDefinitionDslPayload {
  return {
    dslVersion: '1.0.0',
    processKey: meta.processKey,
    processName: meta.processName,
    category: meta.category,
    formKey: meta.formKey,
    formVersion: meta.formVersion,
    settings: {
      allowWithdraw: true,
      allowUrge: true,
      allowTransfer: true,
    },
    nodes: snapshot.nodes.map((node) => {
      const nodeType = nodeTypeFor(node.data.kind)
      return {
        id: node.id,
        type: nodeType,
        name: node.data.label.trim() || labelForKind(node.data.kind, node.data.label),
        description: node.data.description || descriptionForKind(node.data.kind),
        position: {
          x: node.position.x,
          y: node.position.y,
        },
        config: normalizeNodeConfig(node.data.kind, node.data.config),
        ui: {
          width: node.width ?? DEFAULT_NODE_WIDTH,
          height: node.height ?? DEFAULT_NODE_HEIGHT,
        },
      }
    }),
    edges: snapshot.edges.map((edge, index) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      priority: index + 1,
      label: typeof edge.label === 'string' ? edge.label : edge.id,
      condition: normalizeEdgeCondition(edge.data?.condition),
    })),
  }
}

export function processDefinitionDetailToWorkflowSnapshot(
  detail: ProcessDefinitionDetailResponse
): WorkflowSnapshot {
  return {
    nodes: detail.dsl.nodes.map((node) => {
      const kind = nodeKindFor(node.type)
      return {
        id: node.id,
        type: 'workflow',
        position: {
          x: node.position.x,
          y: node.position.y,
        },
        data: {
          kind,
          label: node.name || labelForKind(kind, node.name),
          description: node.description || descriptionForKind(kind),
          tone: toneFor(node.type),
          config: normalizeNodeConfig(kind, node.config),
        },
        width: node.ui?.width ?? DEFAULT_NODE_WIDTH,
        height: node.ui?.height ?? DEFAULT_NODE_HEIGHT,
      }
    }),
    edges: detail.dsl.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      type: 'smoothstep',
      label: edge.label,
      data: {
        condition: normalizeEdgeCondition(edge.condition),
      },
    })),
    selectedNodeId: null,
  }
}
